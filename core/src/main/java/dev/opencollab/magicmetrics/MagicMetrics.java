package dev.opencollab.magicmetrics;

import dev.opencollab.magicmetrics.config.Config;
import dev.opencollab.magicmetrics.health.HealthMetrics;
import dev.opencollab.magicmetrics.health.checks.DeadlockHealthCheck;
import dev.opencollab.magicmetrics.health.checks.NettyHealthCheck;
import dev.opencollab.magicmetrics.https.MetricServer;
import dev.opencollab.magicmetrics.server.ServerMetrics;
import io.micrometer.core.instrument.Metrics;
import io.micrometer.prometheusmetrics.PrometheusConfig;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import io.netty.channel.Channel;
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

    public MagicMetrics() {
        Metrics.globalRegistry.add(registry);
    }

    /**
     * Creates the metric meters (network counters/gauges and JVM binders) and
     * exposes the Prometheus scrape endpoint. This is independent of any platform
     * network server, so it can - and should - be set up regardless of whether the
     * platform-specific network wiring below succeeds.
     */
    public void configureMetrics() {
        serverMetrics = new ServerMetrics(this, registry);
        this.server.registerHandler(config.networkMetrics().endpoint(), serverMetrics);
    }

    /**
     * Wires the RakNet server metrics into an already-bound server channel.
     * <p>
     * RakNet reads {@code RAK_SERVER_METRICS} from the channel config (a volatile
     * field) on every incoming packet, so setting it here takes effect even though
     * the channel is already bound. Setting the option on the
     * {@link io.netty.bootstrap.ServerBootstrap} instead only ever applies at bind
     * time, which is why doing so after the server has started had no effect.
     */
    public void wireServerMetrics(Channel channel) {
        if (serverMetrics == null) return;
        channel.config().setOption(RakChannelOption.RAK_SERVER_METRICS, serverMetrics);
    }

    public void initHealth() {
        HealthRegistries.getRegistry(HealthType.LIVENESS).register("netty-threads", NettyHealthCheck.INSTANCE);
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
