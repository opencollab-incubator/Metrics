package dev.opencollab.magicmetrics.server;

import com.sun.net.httpserver.HttpExchange;
import dev.opencollab.magicmetrics.MagicMetrics;
import dev.opencollab.magicmetrics.config.Config;
import dev.opencollab.magicmetrics.https.SimpleHttpResponder;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.binder.jvm.*;
import io.micrometer.core.instrument.binder.system.ProcessorMetrics;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import org.cloudburstmc.netty.channel.raknet.RakConstants;
import org.cloudburstmc.netty.channel.raknet.config.RakServerMetrics;

import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

public class ServerMetrics implements RakServerMetrics, SimpleHttpResponder {
    private final MagicMetrics metrics;
    private final PrometheusMeterRegistry prometheusRegistry;
    private final AtomicInteger ACTIVE_CHANNELS = new AtomicInteger();
    private final AtomicInteger BLOCKED_ADDRESSES = new AtomicInteger();
    private final AtomicInteger CONNECTED_PLAYERS = new AtomicInteger();

    private final Gauge activeChannels;
    private final Gauge blockedAddresses;
    private final Gauge connectedPlayers;
    private final Counter pings;
    private final Counter conReq1;
    private final Counter conReq2;
    private final Counter conReqs;
    private final Counter newIncoming;

    public ServerMetrics(MagicMetrics metrics, PrometheusMeterRegistry prometheusRegistry) {
        this.metrics = metrics;
        this.prometheusRegistry = prometheusRegistry;
        this.activeChannels = buildGauge(metrics.config().networkMetrics().activeChannels(), ACTIVE_CHANNELS::get);
        this.blockedAddresses = buildGauge(metrics.config().networkMetrics().blockedAddresses(), BLOCKED_ADDRESSES::get);
        this.connectedPlayers = buildGauge(metrics.config().networkMetrics().connectedPlayers(), CONNECTED_PLAYERS::get);

        this.pings = buildCounter(metrics.config().networkMetrics().pings());
        this.conReqs = buildCounter(metrics.config().networkMetrics().connectionRequests());
        this.conReq1 = buildCounter(metrics.config().networkMetrics().connectionRequests1());
        this.conReq2 = buildCounter(metrics.config().networkMetrics().connectionRequests2());
        this.newIncoming = buildCounter(metrics.config().networkMetrics().newIncomingConnections());

        if (metrics.config().networkMetrics().classLoaderMetrics()) new ClassLoaderMetrics().bindTo(Metrics.globalRegistry);
        if (metrics.config().networkMetrics().jvmMemoryMetrics()) new JvmMemoryMetrics().bindTo(Metrics.globalRegistry);
        if (metrics.config().networkMetrics().jvmGcMetrics()) {
            JvmGcMetrics jvmGcMetrics = new JvmGcMetrics(); // Why this one closable but not the others? hmmm
            jvmGcMetrics.bindTo(Metrics.globalRegistry);
            Runtime.getRuntime().addShutdownHook(new Thread(jvmGcMetrics::close));
        }
        if (metrics.config().networkMetrics().processorMetrics()) new ProcessorMetrics().bindTo(Metrics.globalRegistry);
        if (metrics.config().networkMetrics().jvmThreadMetrics()) new JvmThreadMetrics().bindTo(Metrics.globalRegistry);
        if (metrics.config().networkMetrics().jvmThreadDeadlockMetrics()) new JvmThreadDeadlockMetrics().bindTo(Metrics.globalRegistry);
    }

    public Gauge buildGauge(Config.MetricConfig config, Supplier<Number> supplier) {
        if (config.enabled()) {
            return Gauge.builder(config.name(), supplier)
                    .description(config.description())
                    .tags(config.tags().entrySet().stream()
                            .map(entry -> Tag.of(entry.getKey(), entry.getValue()))
                            .toList()
                    )
                    .register(Metrics.globalRegistry);
        }
        return null;
    }

    public Counter buildCounter(Config.MetricConfig config) {
        if (config.enabled()) {
            return Counter.builder(config.name())
                    .description(config.description())
                    .tags(config.tags().entrySet().stream()
                            .map(entry -> Tag.of(entry.getKey(), entry.getValue()))
                            .toList()
                    )
                    .register(Metrics.globalRegistry);
        }
        return null;
    }

    @Override
    public void channelOpen(InetSocketAddress address) {
        ACTIVE_CHANNELS.incrementAndGet();
    }

    @Override
    public void channelClose(InetSocketAddress address) {
        ACTIVE_CHANNELS.decrementAndGet();
    }

    @Override
    public void addressBlocked(InetSocketAddress address) {
        BLOCKED_ADDRESSES.incrementAndGet();
    }

    @Override
    public void addressUnblocked(InetSocketAddress address) {
        BLOCKED_ADDRESSES.decrementAndGet();
    }

    @Override
    public void unconnectedPing(InetSocketAddress address) {
        if (this.pings != null) {
            this.pings.increment();
        }
    }

    @Override
    public void connectionInitPacket(InetSocketAddress address, int packetId) {
        switch (packetId) {
            case RakConstants.ID_OPEN_CONNECTION_REQUEST_1 -> {
                if (conReq1 != null) {
                    this.conReq1.increment();
                }
            }
            case RakConstants.ID_OPEN_CONNECTION_REQUEST_2 -> {
                if (conReq2 != null) {
                    this.conReq2.increment();
                }
            }
            case RakConstants.ID_CONNECTION_REQUEST -> {
                if (conReqs != null) {
                    this.conReqs.increment();
                }
            }
            case RakConstants.ID_NEW_INCOMING_CONNECTION -> {
                if (newIncoming != null) {
                    this.newIncoming.increment();
                }
            }
            default -> {
                metrics.logWarn("Unknown packet id: " + packetId);
            }
        }
    }

    public void playerJoined() {
        CONNECTED_PLAYERS.incrementAndGet();
    }

    public void playerLeft() {
        CONNECTED_PLAYERS.decrementAndGet();
    }

    @Override
    public HttpResponse getResponse(HttpExchange exchange) {
        String contentType = "application/openmetrics-text";
        exchange.getResponseHeaders().add("Content-Type", contentType);
        return new HttpResponse(200, prometheusRegistry.scrape(contentType));
    }
}
