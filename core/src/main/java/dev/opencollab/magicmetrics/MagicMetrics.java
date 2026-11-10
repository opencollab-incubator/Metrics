package dev.opencollab.magicmetrics;

import dev.opencollab.magicmetrics.config.Config;
import dev.opencollab.magicmetrics.health.HealthMetrics;
import dev.opencollab.magicmetrics.health.checks.DeadlockHealthCheck;
import dev.opencollab.magicmetrics.health.checks.NettyHealthCheck;
import dev.opencollab.magicmetrics.https.MetricServer;
import dev.opencollab.magicmetrics.nethernet.LayeredNetherServerMetrics;
import dev.opencollab.magicmetrics.server.NetherChildMetricsHandler;
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
import org.cloudburstmc.netty.channel.nethernet.config.NetherChannelOption;
import org.cloudburstmc.netty.channel.nethernet.config.NetherServerMetrics;

import java.util.List;

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
     * Wires the NetherNet metrics into an already-bound NetherNet server channel.
     * <p>
     * {@code NETHER_SERVER_METRICS} may be changed on a live server channel: the config hands
     * the new value straight to the signaling, so listener wide events are reported from here
     * on. Per connection metrics ({@code NETHER_METRICS}) are a child option, which the bound
     * server can no longer take through its bootstrap, so a handler at the front of the server
     * pipeline sets it on each connection as it is accepted.
     */
    public void wireServerMetrics(Channel channel) {
        if (serverMetrics == null || channel == null || !channel.isOpen()) return;
        NetherServerMetrics metrics = channel.config().getOption(NetherChannelOption.NETHER_SERVER_METRICS);
        if (metrics != null) {
            metrics = new LayeredNetherServerMetrics(List.of(metrics, serverMetrics));
        } else {
            metrics = serverMetrics;
        }
        channel.config().setOption(NetherChannelOption.NETHER_SERVER_METRICS, metrics);
        if (channel.pipeline().get(NetherChildMetricsHandler.NAME) == null) {
            channel.pipeline().addFirst(NetherChildMetricsHandler.NAME, new NetherChildMetricsHandler(serverMetrics));
        }
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
