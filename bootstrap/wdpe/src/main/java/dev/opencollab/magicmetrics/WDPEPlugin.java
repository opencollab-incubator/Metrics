package dev.opencollab.magicmetrics;

import dev.waterdog.waterdogpe.ProxyServer;
import dev.waterdog.waterdogpe.event.defaults.ProxyStartEvent;
import dev.waterdog.waterdogpe.network.NetworkInterface;
import dev.waterdog.waterdogpe.network.RakNetInterface;
import dev.waterdog.waterdogpe.plugin.Plugin;
import io.netty.channel.Channel;

import java.lang.reflect.Field;
import java.util.List;

public class WDPEPlugin extends Plugin {
    private final WDPEMagicMetrics metrics;

    public WDPEPlugin() {
        this.metrics = new WDPEMagicMetrics(this);
        this.getProxy().getEventManager().subscribe(ProxyStartEvent.class, this::onStart);
    }

    @Override
    public void onEnable() {

    }

    @Override
    public void onDisable() {

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
                } catch (NoSuchFieldException | IllegalAccessException e) {
                    throw new RuntimeException(e);
                }
            }
        });
    }
}
