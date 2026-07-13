package dev.opencollab.magicmetrics;

import dev.opencollab.magicmetrics.config.Config;
import dev.opencollab.magicmetrics.health.HealthMetrics;
import dev.opencollab.magicmetrics.health.checks.DeadlockHealthCheck;
import dev.opencollab.magicmetrics.health.checks.NettyMeasure;
import dev.opencollab.magicmetrics.https.MetricServer;
import dev.opencollab.magicmetrics.server.ServerMetrics;
import io.micrometer.prometheusmetrics.PrometheusConfig;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import io.netty.bootstrap.ServerBootstrap;
import io.smallrye.health.SmallRyeHealthReporter;
import io.smallrye.health.api.HealthType;
import io.smallrye.health.registry.HealthRegistries;
import lombok.Getter;
import lombok.experimental.Accessors;
import org.cloudburstmc.netty.channel.raknet.config.RakChannelOption;

public abstract class MagicMetrics {
    private final PrometheusMeterRegistry registry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
    @Getter
    private final SmallRyeHealthReporter reporter = new SmallRyeHealthReporter();

    @Getter
    @Accessors(fluent = true)
    protected Config config = null;

    private MetricServer server;
    private ServerMetrics serverMetrics;

    public void configureMetrics(ServerBootstrap... serverBootstraps) {
        serverMetrics = new ServerMetrics(this, registry);
        for (ServerBootstrap serverBootstrap : serverBootstraps) {
            serverBootstrap.option(RakChannelOption.RAK_SERVER_METRICS, serverMetrics);
        }
        this.server.registerHandler(config.networkMetrics().endpoint(), serverMetrics);
    }

    public void initHealth() {
        HealthRegistries.getRegistry(HealthType.LIVENESS).register("netty-threads", NettyMeasure.INSTANCE);
        HealthRegistries.getRegistry(HealthType.LIVENESS).register("deadlocked-threads", new DeadlockHealthCheck());
        this.server.registerHandler(config.healthCheck().endpoint(), new HealthMetrics(this));
    }

    public void buildHttpsServer() throws Exception {
        this.server = new MetricServer(this);
    }

    public void playerJoined() {
        if (this.serverMetrics == null) return;
        this.serverMetrics.playerJoined();
    }

    public void playerLeft() {
        if (this.serverMetrics == null) return;
        this.serverMetrics.playerLeft();
    }

    public abstract void logInfo(String message);

    public abstract void logWarn(String message);

    public abstract void logError(String message);

    public abstract void logError(String message, Throwable throwable);
}
