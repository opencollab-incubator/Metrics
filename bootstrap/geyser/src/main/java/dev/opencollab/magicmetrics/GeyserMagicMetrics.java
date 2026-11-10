package dev.opencollab.magicmetrics;

import dev.opencollab.magicmetrics.config.ConfigLoader;
import dev.opencollab.magicmetrics.health.checks.NettyHealthCheck;
import io.netty.channel.Channel;
import org.geysermc.event.subscribe.Subscribe;
import org.geysermc.geyser.GeyserImpl;
import org.geysermc.geyser.api.GeyserApi;
import org.geysermc.geyser.api.event.bedrock.SessionDisconnectEvent;
import org.geysermc.geyser.api.event.bedrock.SessionInitializeEvent;
import org.geysermc.geyser.api.event.lifecycle.GeyserPostInitializeEvent;
import org.geysermc.geyser.api.event.lifecycle.GeyserPreInitializeEvent;
import org.geysermc.geyser.api.extension.Extension;
import org.geysermc.geyser.network.bedrock.nethernet.NetherNetServer;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.concurrent.CompletableFuture;

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

        GeyserImpl impl = (GeyserImpl) GeyserApi.api();
        NetherNetServer server = impl.getNetherNetServer();
        if (server == null) {
            logWarn("NetherNet is not enabled in Geyser's config (bedrock.transport), so no network metrics will be collected");
            return;
        }

        // Built-in signaling: bound synchronously during Geyser's start, so it is up by now
        // (or null if it is disabled or failed to start). Geyser has no getter for it.
        try {
            Field inbuiltChannel = NetherNetServer.class.getDeclaredField("inbuiltChannel");
            inbuiltChannel.setAccessible(true);
            wire((Channel) inbuiltChannel.get(server), "geyser-nethernet-builtin");
        } catch (Throwable e) {
            // The metrics endpoint itself is already up (configured in pre-init), so this
            // only costs the NetherNet counters - log it instead of tearing everything down.
            logError("Failed to wire NetherNet metrics into Geyser's built-in signaling", e);
        }

        // External signaling provider (NXS): opened asynchronously, so the channel may not exist
        // yet. Wait for Geyser's provider start future to finish, then wire whatever it opened.
        try {
            Field providerStart = NetherNetServer.class.getDeclaredField("providerStart");
            providerStart.setAccessible(true);
            CompletableFuture<?> start = (CompletableFuture<?>) providerStart.get(server);
            start.whenComplete((result, error) -> {
                try {
                    wire(server.providerChannel(), "geyser-nethernet-provider");
                } catch (Throwable e) {
                    logError("Failed to wire NetherNet metrics into Geyser's signaling provider", e);
                }
            });
        } catch (Throwable e) {
            logError("Failed to wire NetherNet metrics into Geyser's signaling provider", e);
        }
    }

    private void wire(Channel channel, String healthGroup) {
        if (channel == null) return;
        wireServerMetrics(channel);
        NettyHealthCheck.addGroup(channel.eventLoop().parent(), healthGroup);
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
