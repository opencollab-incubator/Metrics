package dev.opencollab.magicmetrics;

import dev.opencollab.magicmetrics.config.ConfigLoader;
import dev.opencollab.magicmetrics.health.checks.NettyHealthCheck;
import dev.waterdog.waterdogpe.ProxyServer;
import dev.waterdog.waterdogpe.event.defaults.ProxyStartEvent;
import dev.waterdog.waterdogpe.network.NetworkInterface;
import dev.waterdog.waterdogpe.network.nethernet.NetherNetInterface;
import dev.waterdog.waterdogpe.network.nethernet.NetherNetProvider;
import dev.waterdog.waterdogpe.plugin.Plugin;
import io.netty.channel.Channel;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.List;

public class WDPEPlugin extends Plugin {
    private final WDPEMagicMetrics metrics;

    public WDPEPlugin() {
        this.metrics = new WDPEMagicMetrics(this);
    }

    @Override
    public void onEnable() {
        this.getProxy().getEventManager().subscribe(ProxyStartEvent.class, this::onStart);

        try {
            metrics.config = ConfigLoader.loadConfig(new File(this.getDataFolder(), "config.yml"));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        try {
            metrics.buildHttpsServer();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        if (metrics.config.healthCheck().enabled()) {
            metrics.initHealth();
        }
    }

    private void onStart(ProxyStartEvent event) {
        // Let's get hacky!
        ProxyServer server = event.getProxy();
        List<NetworkInterface> interfaces = server.getInterfaces();
        interfaces.forEach(networkInterface -> {
            if (networkInterface instanceof NetherNetInterface nethernet) {
                try {
                    Field bindingsField = NetherNetInterface.class.getDeclaredField("bindings");
                    bindingsField.setAccessible(true);
                    List<Object> bindings = (List<Object>) bindingsField.get(nethernet);

                    for (Object binding : bindings) {
                        Field channelField = binding.getClass().getDeclaredField("channel");
                        channelField.setAccessible(true);
                        Channel channel = (Channel) channelField.get(server);
                        if (channel == null) return;
                        this.metrics.wireServerMetrics(channel);
                        NettyHealthCheck.addGroup(channel.eventLoop().parent(), "wdpe-nethernet-builtin");
                    }
                } catch (Throwable e) {
                    this.metrics.logError("Failed to wire NetherNet metrics into WDPE's built-in signaling", e);
                }

                try {
                    Field providerField = NetherNetInterface.class.getDeclaredField("provider");
                    providerField.setAccessible(true);
                    NetherNetProvider provider = (NetherNetProvider) providerField.get(nethernet);
                    if (provider != null) {
                        Field channelField = NetherNetProvider.class.getDeclaredField("channel");
                        channelField.setAccessible(true);
                        Channel channel = (Channel) channelField.get(provider);
                        if (channel == null) return;
                        this.metrics.wireServerMetrics(channel);
                        NettyHealthCheck.addGroup(channel.eventLoop().parent(), "wdpe-nethernet-provider");
                    }
                } catch (Throwable e) {
                    this.metrics.logError("Failed to wire NetherNet metrics into WDPE's signaling provider", e);
                }
            }
        });
    }
}
