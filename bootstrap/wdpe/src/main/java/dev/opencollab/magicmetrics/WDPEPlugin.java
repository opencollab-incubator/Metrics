package dev.opencollab.magicmetrics;

import dev.opencollab.magicmetrics.config.ConfigLoader;
import dev.opencollab.magicmetrics.health.checks.NettyHealthCheck;
import dev.waterdog.waterdogpe.ProxyServer;
import dev.waterdog.waterdogpe.event.defaults.ProxyStartEvent;
import dev.waterdog.waterdogpe.network.NetworkInterface;
import dev.waterdog.waterdogpe.network.RakNetInterface;
import dev.waterdog.waterdogpe.plugin.Plugin;
import io.netty.channel.Channel;
import io.netty.channel.EventLoopGroup;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
            if (networkInterface instanceof RakNetInterface rakNet) {
                try {
                    Field serverChannelsField = rakNet.getClass().getField("serverChannels");
                    serverChannelsField.setAccessible(true);
                    List<Channel> serverChannels = (List<Channel>) serverChannelsField.get(rakNet);
                    if (metrics.config.healthCheck().enabled()) {
                        Set<EventLoopGroup> groups = new HashSet<>();
                        for (Channel channel : serverChannels) {
                            groups.add(channel.eventLoop().parent());
                        }
                        int i = 0;
                        for (EventLoopGroup group : groups) {
                            NettyHealthCheck.addGroup(group, "wdpe-%d".formatted(i++));
                        }
                    }
                } catch (NoSuchFieldException | IllegalAccessException e) {
                    throw new RuntimeException(e);
                }
            }
        });
    }
}
