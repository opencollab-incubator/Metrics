package dev.opencollab.magicmetrics;

import dev.opencollab.magicmetrics.config.Config;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import org.cloudburstmc.netty.channel.raknet.RakConstants;
import org.cloudburstmc.netty.channel.raknet.config.RakServerMetrics;

import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

public class ServerMetrics implements RakServerMetrics {

    private final AtomicInteger ACTIVE_CHANNELS = new AtomicInteger();
    private final AtomicInteger BLOCKED_ADDRESSES = new AtomicInteger();

    private final Gauge activeChannels;
    private final Gauge blockedAddresses;
    private final Counter pings;
    private final Counter conReq1;
    private final Counter conReq2;
    private final Counter conReqs;
    private final Counter newIncoming;

    public ServerMetrics(Config config, MeterRegistry registry) {
        this.activeChannels = buildGauge(config.networkMetrics().activeChannels(), registry, ACTIVE_CHANNELS::get);
        this.blockedAddresses = buildGauge(config.networkMetrics().blockedAddresses(), registry, BLOCKED_ADDRESSES::get);

        this.pings = buildCounter(config.networkMetrics().pings(), registry);
        this.conReqs = buildCounter(config.networkMetrics().connectionRequests(), registry);
        this.conReq1 = buildCounter(config.networkMetrics().connectionRequests1(), registry);
        this.conReq2 = buildCounter(config.networkMetrics().connectionRequests2(), registry);
        this.newIncoming = buildCounter(config.networkMetrics().newIncomingConnections(), registry);
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
                System.out.println("Unknown packet id: " + packetId);
            }
        }
    }
}
