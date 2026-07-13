package dev.opencollab.magicmetrics;

import dev.opencollab.magicmetrics.config.ConfigLoader;
import dev.opencollab.magicmetrics.health.checks.NettyMeasure;
import io.netty.bootstrap.ServerBootstrap;
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

        if (!this.config.healthCheck().enabled()) return;

        initHealth();
    }

    @Subscribe
    public void onGeyserEnable(GeyserPostInitializeEvent ignored) {
        if (!config.networkMetrics().enabled()) {
            return;
        }

        // We just started - insert metrics!
        GeyserImpl impl = (GeyserImpl) GeyserApi.api();
        GeyserServer server = impl.getGeyserServer();

        try {
            Field bootstrap = server.getClass().getDeclaredField("bootstrap");
            bootstrap.setAccessible(true);
            ServerBootstrap serverBootstrap = (ServerBootstrap) bootstrap.get(server);
            configureMetrics(serverBootstrap);
            NettyMeasure.addGroup(serverBootstrap.config().group(), "geyser");
        } catch (Throwable e) {
            throw new RuntimeException(e);
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
