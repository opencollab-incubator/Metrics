package dev.opencollab.magicmetrics;

import dev.opencollab.magicmetrics.config.Config;
import io.netty.bootstrap.ServerBootstrap;
import org.cloudburstmc.netty.channel.raknet.config.RakChannelOption;

public class MagicMetrics {
    protected Config config = null;

    public MagicMetrics() {

    }

    public void configureMetrics(ServerBootstrap serverBootstrap) {
        serverBootstrap.option(RakChannelOption.RAK_SERVER_METRICS, new ServerMetrics(config, null));
    }
}
