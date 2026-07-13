package dev.opencollab.magicmetrics;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.opencollab.magicmetrics.config.ConfigLoader;
import dev.opencollab.magicmetrics.health.HealthResult;
import io.netty.bootstrap.ServerBootstrap;
import org.geysermc.event.subscribe.Subscribe;
import org.geysermc.geyser.GeyserImpl;
import org.geysermc.geyser.api.GeyserApi;
import org.geysermc.geyser.api.event.bedrock.SessionDisconnectEvent;
import org.geysermc.geyser.api.event.bedrock.SessionInitializeEvent;
import org.geysermc.geyser.api.event.lifecycle.*;
import org.geysermc.geyser.api.extension.Extension;
import org.geysermc.geyser.command.defaults.ConnectionTestCommand;
import org.geysermc.geyser.network.netty.GeyserServer;
import org.geysermc.geyser.util.WebUtils;

import java.io.IOException;
import java.lang.reflect.Field;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class GeyserMagicMetrics extends MagicMetrics implements Extension {
    private static final HealthResult HEALTHY_RESULT = new HealthResult(200, "HEALTHY", "Geyser is currently accepting players.");
    private final Random random = new Random();

    private ScheduledExecutorService scheduler;
    private boolean geyserReachable = false;
    private HealthResult currentHealthResult;

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

        currentHealthResult = new HealthResult(200, "PRE-INIT", "Geyser has started to initialize.");

        initHealth();

        scheduler = Executors.newScheduledThreadPool(1, r -> new Thread(r, "MagicMetrics Health Check Thread"));
        scheduler.scheduleAtFixedRate(() -> {
            if (!geyserReachable) return;

            byte[] randomBytes = new byte[2];
            this.random.nextBytes(randomBytes);
            StringBuilder randomStr = new StringBuilder();
            for (byte b : randomBytes) {
                randomStr.append(Integer.toHexString(b));
            }
            String connectionTestMotd = "MagicMetrics Health Check " + randomStr;
            ConnectionTestCommand.CONNECTION_TEST_MOTD = connectionTestMotd;
            JsonObject output = null;
            try {
                String hostname = URLEncoder.encode(this.config.healthCheck().address(), StandardCharsets.UTF_8);
                output = WebUtils.getJson("https://checker.geysermc.org/ping?hostname=" + hostname + "&port=" + this.config.healthCheck().port());
            } catch (IOException e) {
                currentHealthResult = new HealthResult(200, "PING_FAILED_EXTERNAL", "Unable to ping Geyser due to an exception while pinging https://checker.geysermc.org.");
                this.logger().error("Unable to check server", e);
            } finally {
                ConnectionTestCommand.CONNECTION_TEST_MOTD = null;
            }

            if (output == null) return;

            if (output.get("success").getAsBoolean()) {
                JsonObject ping = output.getAsJsonObject("ping");
                JsonObject pong = ping.getAsJsonObject("pong");
                String remoteMotd = pong.get("motd").getAsString();
                if (!connectionTestMotd.equals(remoteMotd)) {
                    currentHealthResult = new HealthResult(200, "PING_WARN_WRONG", "MOTD mismatch while pinging server.");
                    return;
                }

                if (ping.get("tcpFirst").getAsBoolean()) {
                    currentHealthResult = new HealthResult(200, "PING_WARN_UNREACHABLE", "Ping required TCP ping first, this is likely to cause connection issues.");
                    return;
                }

                currentHealthResult = HEALTHY_RESULT;
                return;
            }

            JsonElement message = output.get("message");
            if (message != null && !message.getAsString().isEmpty()) {
                currentHealthResult = new HealthResult(200, "PING_FAILED_NO_RESPONSE", "Unable to ping Geyser server, no response was given. Message: " + message.getAsString());
            } else {
                currentHealthResult = new HealthResult(200, "PING_FAILED_NO_RESPONSE", "Unable to ping Geyser server, no response was given.");
            }
        }, 1, 1, TimeUnit.MINUTES);
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

        geyserReachable = true;
        currentHealthResult = HEALTHY_RESULT;
    }

    @Subscribe
    public void onGeyserReloadBegin(GeyserPreReloadEvent ignored) {
        geyserReachable = false;
        currentHealthResult = new HealthResult(200, "RELOADING", "Geyser is currently reloading.");
    }

    @Subscribe
    public void onGeyserReloadFinish(GeyserPostReloadEvent ignored) {
        geyserReachable = true;
        currentHealthResult = HEALTHY_RESULT;
    }

    @Subscribe
    public void onGeyserShutdown(GeyserShutdownEvent ignored) {
        geyserReachable = false;
        currentHealthResult = new HealthResult(200, "SHUTTING_DOWN", "Geyser is shutting down.");

        if (scheduler != null) scheduler.shutdownNow();
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
    public HealthResult getCurrentHealthResult() {
        return currentHealthResult;
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
