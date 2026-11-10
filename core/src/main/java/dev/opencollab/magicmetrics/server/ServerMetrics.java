package dev.opencollab.magicmetrics.server;

import com.sun.net.httpserver.HttpExchange;
import dev.opencollab.magicmetrics.MagicMetrics;
import dev.opencollab.magicmetrics.config.Config;
import dev.opencollab.magicmetrics.https.SimpleHttpResponder;
import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.binder.MeterBinder;
import io.micrometer.core.instrument.binder.jvm.*;
import io.micrometer.core.instrument.binder.system.ProcessorMetrics;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import lombok.SneakyThrows;
import org.cloudburstmc.netty.channel.nethernet.config.NetherChannelMetrics;
import org.cloudburstmc.netty.channel.nethernet.config.NetherConnectionFailure;
import org.cloudburstmc.netty.channel.nethernet.config.NetherServerMetrics;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Collects NetherNet network metrics.
 * <p>
 * A single instance is used both as the listener wide {@link NetherServerMetrics} (set on the server
 * channel) and as the {@link NetherChannelMetrics} of every accepted connection, so the per connection
 * counters are totals over all connections. The transport calls these from its own threads (including
 * native callback threads), which is fine as every meter here is thread safe.
 */
public class ServerMetrics implements NetherServerMetrics, NetherChannelMetrics, SimpleHttpResponder {
    private final PrometheusMeterRegistry prometheusRegistry;
    private final AtomicInteger ACTIVE_CHANNELS = new AtomicInteger();
    private final AtomicInteger CONNECTED_PLAYERS = new AtomicInteger();

    private final Gauge activeChannels;
    private final Gauge connectedPlayers;

    // Listener wide
    private final TaggedCounter connectionsAccepted;
    private final TaggedCounter joinsRefused;
    private final Counter joinsThrottled;
    private final Counter addressesRefused;
    private final Counter plaintextRefused;

    // Per connection
    private final TaggedCounter connectionFailures;
    private final Counter handshakeRetries;
    private final TaggedCounter pathsSelected;
    private final Counter bytesIn;
    private final Counter bytesOut;
    private final Counter messagesIn;
    private final Counter messagesOut;
    private final Counter fragmentsIn;
    private final Counter fragmentsOut;
    private final Counter decodeFailures;

    public ServerMetrics(MagicMetrics metrics, PrometheusMeterRegistry prometheusRegistry) {
        this.prometheusRegistry = prometheusRegistry;
        Config.NetworkMetrics network = metrics.config().networkMetrics();

        this.activeChannels = buildGauge(network.activeChannels(), ACTIVE_CHANNELS::get);
        this.connectedPlayers = buildGauge(network.connectedPlayers(), CONNECTED_PLAYERS::get);

        this.connectionsAccepted = buildTaggedCounter(network.connectionsAccepted(), "verified");
        this.joinsRefused = buildTaggedCounter(network.joinsRefused(), "status");
        this.joinsThrottled = buildCounter(network.joinsThrottled());
        this.addressesRefused = buildCounter(network.addressesRefused());
        this.plaintextRefused = buildCounter(network.plaintextRefused());

        this.connectionFailures = buildTaggedCounter(network.connectionFailures(), "reason");
        this.handshakeRetries = buildCounter(network.handshakeRetries());
        this.pathsSelected = buildTaggedCounter(network.pathsSelected(), "local", "remote");
        this.bytesIn = buildCounter(network.bytesIn(), "bytes");
        this.bytesOut = buildCounter(network.bytesOut(), "bytes");
        this.messagesIn = buildCounter(network.messagesIn());
        this.messagesOut = buildCounter(network.messagesOut());
        this.fragmentsIn = buildCounter(network.fragmentsIn());
        this.fragmentsOut = buildCounter(network.fragmentsOut());
        this.decodeFailures = buildCounter(network.decodeFailures());

        buildJvmMetrics(network.classLoaderMetrics(), ClassLoaderMetrics::new);
        buildJvmMetrics(network.jvmMemoryMetrics(), JvmMemoryMetrics::new);
        buildJvmMetrics(network.jvmGcMetrics(), JvmGcMetrics::new);
        buildJvmMetrics(network.processorMetrics(), ProcessorMetrics::new);
        buildJvmMetrics(network.jvmThreadMetrics(), JvmThreadMetrics::new);
        buildJvmMetrics(network.jvmThreadDeadlockMetrics(), JvmThreadDeadlockMetrics::new);
    }

    private static List<Tag> tagsOf(Config.MetricConfig config) {
        if (config.tags() == null) return List.of();
        return config.tags().entrySet().stream()
                .map(entry -> Tag.of(entry.getKey(), entry.getValue()))
                .toList();
    }

    public Gauge buildGauge(Config.MetricConfig config, Supplier<Number> supplier) {
        if (config == null || !config.enabled()) return null;
        return Gauge.builder(config.name(), supplier)
                .description(config.description())
                .tags(tagsOf(config))
                .register(Metrics.globalRegistry);
    }

    public Counter buildCounter(Config.MetricConfig config) {
        return buildCounter(config, null);
    }

    public Counter buildCounter(Config.MetricConfig config, String baseUnit) {
        if (config == null || !config.enabled()) return null;
        return Counter.builder(config.name())
                .description(config.description())
                .baseUnit(baseUnit)
                .tags(tagsOf(config))
                .register(Metrics.globalRegistry);
    }

    /**
     * A counter with extra tags whose values are only known when the event fires (an HTTP status, a
     * failure reason...). Returns null when the metric is disabled.
     */
    public TaggedCounter buildTaggedCounter(Config.MetricConfig config, String... tagKeys) {
        if (config == null || !config.enabled()) return null;
        return new TaggedCounter(config, tagKeys);
    }

    @SneakyThrows
    public void buildJvmMetrics(Config.JvmMetricConfig config, Function<Tags, MeterBinder> binderFunction) {
        if (config == null || !config.enabled()) return;

        Tags tags = Tags.of(config.tags().entrySet().stream().map(
                entry -> Tag.of(entry.getKey(), entry.getValue())
        ).toArray(Tag[]::new));

        MeterBinder binder = binderFunction.apply(tags);
        binder.bindTo(Metrics.globalRegistry);

        if (binder instanceof AutoCloseable autoCloseable) {
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    autoCloseable.close();
                } catch (Exception ignored) {}
            }));
        }
    }

    private static void increment(Counter counter, int amount) {
        if (counter != null && amount > 0) {
            counter.increment(amount);
        }
    }

    private static void increment(Counter counter) {
        if (counter != null) {
            counter.increment();
        }
    }

    // Connection lifecycle, driven by the child channel handler (NetherNet has no listener callback for these)

    public void channelOpened() {
        ACTIVE_CHANNELS.incrementAndGet();
    }

    public void channelClosed() {
        ACTIVE_CHANNELS.decrementAndGet();
    }

    // NetherServerMetrics

    @Override
    public void connectionAccepted(String networkId, boolean verifiedIdentity) {
        if (connectionsAccepted != null) connectionsAccepted.increment(String.valueOf(verifiedIdentity));
    }

    @Override
    public void joinRefused(int httpStatus) {
        if (joinsRefused != null) joinsRefused.increment(String.valueOf(httpStatus));
    }

    @Override
    public void addressRefused(InetSocketAddress address) {
        increment(addressesRefused);
    }

    @Override
    public void plaintextRefused(InetSocketAddress address) {
        increment(plaintextRefused);
    }

    @Override
    public void joinThrottled(InetSocketAddress address) {
        increment(joinsThrottled);
    }

    // NetherChannelMetrics

    @Override
    public void bytesIn(int count) {
        increment(bytesIn, count);
    }

    @Override
    public void bytesOut(int count) {
        increment(bytesOut, count);
    }

    @Override
    public void messagesIn(int count) {
        increment(messagesIn, count);
    }

    @Override
    public void messagesOut(int count) {
        increment(messagesOut, count);
    }

    @Override
    public void fragmentsIn(int count) {
        increment(fragmentsIn, count);
    }

    @Override
    public void fragmentsOut(int count) {
        increment(fragmentsOut, count);
    }

    @Override
    public void decodeFail(int count) {
        increment(decodeFailures, count);
    }

    @Override
    public void pathSelected(String localType, String remoteType) {
        if (pathsSelected != null) {
            pathsSelected.increment(localType == null ? "unknown" : localType, remoteType == null ? "unknown" : remoteType);
        }
    }

    @Override
    public void connectionFailed(NetherConnectionFailure reason) {
        if (connectionFailures != null) connectionFailures.increment(reason.name());
    }

    @Override
    public void handshakeRetry() {
        increment(handshakeRetries);
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

    /**
     * One counter per combination of dynamic tag values, created on first use. The value sets are
     * small and bounded (HTTP statuses, failure reasons, ICE candidate types).
     */
    public static final class TaggedCounter {
        private final Config.MetricConfig config;
        private final List<Tag> baseTags;
        private final String[] tagKeys;
        private final Map<List<String>, Counter> counters = new ConcurrentHashMap<>();

        private TaggedCounter(Config.MetricConfig config, String[] tagKeys) {
            this.config = config;
            this.baseTags = tagsOf(config);
            this.tagKeys = tagKeys;
        }

        public void increment(String... values) {
            counters.computeIfAbsent(List.of(values), this::create).increment();
        }

        private Counter create(List<String> values) {
            Counter.Builder builder = Counter.builder(config.name())
                    .description(config.description())
                    .tags(baseTags);
            for (int i = 0; i < tagKeys.length; i++) {
                builder.tag(tagKeys[i], values.get(i));
            }
            return builder.register(Metrics.globalRegistry);
        }
    }
}
