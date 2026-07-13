package dev.opencollab.magicmetrics.server;

import com.sun.net.httpserver.HttpExchange;
import dev.opencollab.magicmetrics.MagicMetrics;
import dev.opencollab.magicmetrics.config.Config;
import dev.opencollab.magicmetrics.https.SimpleHttpResponder;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import io.micrometer.prometheusmetrics.PrometheusRenameFilter;
import org.cloudburstmc.netty.channel.raknet.RakConstants;
import org.cloudburstmc.netty.channel.raknet.config.RakServerMetrics;

import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

public class ServerMetrics implements RakServerMetrics, SimpleHttpResponder {
    private final MagicMetrics metrics;
    private final PrometheusMeterRegistry registry;
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

    public ServerMetrics(MagicMetrics metrics, PrometheusMeterRegistry registry) {
        this.metrics = metrics;
        this.registry = registry;
        this.registry.config().meterFilter(new PrometheusRenameFilter());
        this.activeChannels = buildGauge(metrics.config().networkMetrics().activeChannels(), registry, ACTIVE_CHANNELS::get);
        this.blockedAddresses = buildGauge(metrics.config().networkMetrics().blockedAddresses(), registry, BLOCKED_ADDRESSES::get);
        this.connectedPlayers = buildGauge(metrics.config().networkMetrics().connectedPlayers(), registry, CONNECTED_PLAYERS::get);

        this.pings = buildCounter(metrics.config().networkMetrics().pings(), registry);
        this.conReqs = buildCounter(metrics.config().networkMetrics().connectionRequests(), registry);
        this.conReq1 = buildCounter(metrics.config().networkMetrics().connectionRequests1(), registry);
        this.conReq2 = buildCounter(metrics.config().networkMetrics().connectionRequests2(), registry);
        this.newIncoming = buildCounter(metrics.config().networkMetrics().newIncomingConnections(), registry);
    }

    public Gauge buildGauge(Config.MetricConfig config, MeterRegistry registry, Supplier<Number> supplier) {
        if (config.enabled()) {
            return Gauge.builder(config.name(), supplier)
                    .description(config.description())
                    .tags(config.tags().entrySet().stream()
                            .map(entry -> Tag.of(entry.getKey(), entry.getValue()))
                            .toList()
                    )
                    .register(registry);
        }
        return null;
    }

    public Counter buildCounter(Config.MetricConfig config, MeterRegistry registry) {
        if (config.enabled()) {
            return Counter.builder(config.name())
                    .description(config.description())
                    .tags(config.tags().entrySet().stream()
                            .map(entry -> Tag.of(entry.getKey(), entry.getValue()))
                            .toList()
                    )
                    .register(registry);
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
        return new HttpResponse(200, registry.scrape(contentType));
    }
}
