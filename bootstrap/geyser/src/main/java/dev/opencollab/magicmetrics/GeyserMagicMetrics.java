package dev.opencollab.magicmetrics;

import dev.opencollab.magicmetrics.config.ConfigLoader;
import io.netty.bootstrap.ServerBootstrap;
import org.geysermc.event.subscribe.Subscribe;
import org.geysermc.geyser.GeyserImpl;
import org.geysermc.geyser.api.GeyserApi;
import org.geysermc.geyser.api.event.lifecycle.GeyserPostInitializeEvent;
import org.geysermc.geyser.api.event.lifecycle.GeyserPreInitializeEvent;
import org.geysermc.geyser.api.extension.Extension;
import org.geysermc.geyser.network.netty.GeyserServer;
import org.spongepowered.configurate.ConfigurateException;

import java.lang.reflect.Field;

public class GeyserMagicMetrics extends MagicMetrics implements Extension {

    @Subscribe
    public void onGeyserInit(GeyserPreInitializeEvent ignored) {
        try {
            this.config = ConfigLoader.loadConfig(this.dataFolder().resolve("config.yml").toFile());
        } catch (ConfigurateException e) {
            this.disable();
            throw new RuntimeException(e);
        }
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
            configureMetrics((ServerBootstrap) bootstrap.get(server));
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }
}
