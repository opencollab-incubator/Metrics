package dev.opencollab.magicmetrics;

import dev.opencollab.magicmetrics.config.ConfigLoader;
import dev.opencollab.magicmetrics.health.checks.NettyHealthCheck;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import org.geysermc.event.subscribe.Subscribe;
import org.geysermc.geyser.GeyserImpl;
import org.geysermc.geyser.api.GeyserApi;
import org.geysermc.geyser.api.event.bedrock.SessionDisconnectEvent;
import org.geysermc.geyser.api.event.bedrock.SessionInitializeEvent;
import org.geysermc.geyser.api.event.lifecycle.GeyserPostInitializeEvent;
import org.geysermc.geyser.api.event.lifecycle.GeyserPreInitializeEvent;
import org.geysermc.geyser.api.extension.Extension;
import org.geysermc.geyser.network.netty.GeyserServer;

import java.io.IOException;
import java.lang.reflect.Field;

public class GeyserMagicMetrics extends MagicMetrics implements Extension {
    @Subscribe
    public void onGeyserInit(GeyserPreInitializeEvent ignored) {
        try {
            this.config = ConfigLoader.loadConfig(this.dataFolder().resolve("config.yml").toFile());
        } catch (IOException e) {
            this.disable();
            throw new RuntimeException(e);
        }

        try {
            this.buildHttpsServer();
        } catch (Exception e) {
            this.disable();
            throw new RuntimeException(e);
        }

        // Register the scrape endpoint and the network/JVM meters up-front. These do
        // not depend on the Geyser network server, so they must not be coupled to the
        // post-init server reflection - otherwise a reflection failure there would take
        // the JVM metrics and the whole /metrics endpoint down with it.
        if (this.config.networkMetrics().enabled()) {
            configureMetrics();
        }

        if (!this.config.healthCheck().enabled()) return;

        initHealth();
    }

    @Subscribe
    public void onGeyserEnable(GeyserPostInitializeEvent ignored) {
        if (!config.networkMetrics().enabled()) {
            return;
        }

        // The Bedrock server is now bound. RakNet reads RAK_SERVER_METRICS from the
        // live channel config on every packet, so wire our metrics into the already
        // bound channel(s) here. Setting it on the ServerBootstrap after bind (as this
        // did before) never reaches the running channel, which is why the network
        // stats stayed at zero.
        GeyserImpl impl = (GeyserImpl) GeyserApi.api();
        GeyserServer server = impl.getGeyserServer();

        try {
            Field bootstrapFutures = server.getClass().getDeclaredField("bootstrapFutures");
            bootstrapFutures.setAccessible(true);
            ChannelFuture[] futures = (ChannelFuture[]) bootstrapFutures.get(server);
            for (ChannelFuture future : futures) {
                Channel channel = future.channel();
                if (channel == null) continue;
                wireServerMetrics(channel);
                NettyHealthCheck.addGroup(channel.eventLoop().parent(), "geyser");
            }
        } catch (Throwable e) {
            // The metrics endpoint itself is already up (configured in pre-init), so a
            // failure to reflect into Geyser's server only costs the RakNet counters -
            // log it instead of tearing everything down.
            logError("Failed to wire RakNet network metrics into the Geyser server", e);
        }
    }

    @Subscribe
    public void onPlayerJoin(SessionInitializeEvent ignored) {
        this.playerJoined();
    }

    @Subscribe
    public void onPlayerQuit(SessionDisconnectEvent ignored) {
        this.playerLeft();
    }

    @Override
    public void logInfo(String message) {
        logger().info(message);
    }

    @Override
    public void logWarn(String message) {
        logger().warning(message);
    }

    @Override
    public void logError(String message) {
        logger().error(message);
    }

    @Override
    public void logError(String message, Throwable throwable) {
        logger().error(message, throwable);
    }
}
