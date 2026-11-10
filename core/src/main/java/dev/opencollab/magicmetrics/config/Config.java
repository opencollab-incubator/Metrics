package dev.opencollab.magicmetrics.config;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.experimental.Accessors;
import org.spongepowered.configurate.objectmapping.ConfigSerializable;
import org.spongepowered.configurate.objectmapping.meta.Comment;

import java.util.HashMap;
import java.util.Map;

@Getter
@Accessors(fluent = true)
@ConfigSerializable
public class Config {

    @Comment("The address to bind the HTTP server to")
    String address = "127.0.0.1";

    @Comment("The port to bind the HTTP server to")
    int port = 8080;

    @Comment("The path of the keystore for HTTPS")
    String keystorePath = "";

    @Comment("The password to the keystore for HTTPS")
    String keystorePassword = "";

    @Comment("Health check config")
    HealthCheck healthCheck;

    @Comment("Server network metrics")
    NetworkMetrics networkMetrics;

    @Comment("The version of the config. Do not change!")
    int configVersion;

    @Getter
    @Accessors(fluent = true)
    @ConfigSerializable
    public static class NetworkMetrics {

        @Comment("Whether to enable server metrics.")
        boolean enabled = true;

        @Comment("The endpoint of the server metrics endpoint")
        String endpoint = "/metrics";

        MetricConfig activeChannels = new MetricConfig(
                true, "active_channels", "The number of open NetherNet connections"
        );

        // Listener wide (signaling) events
        MetricConfig connectionsAccepted = new MetricConfig(
                true, "connections_accepted", "The number of joins accepted at signaling, tagged by whether the identity was verified", Map.of("id", "CONNECTION_ACCEPTED")
        );

        MetricConfig joinsRefused = new MetricConfig(
                true, "joins_refused", "The number of joins refused at signaling, tagged by the HTTP status the peer was refused with", Map.of("id", "JOIN_REFUSED")
        );

        MetricConfig joinsThrottled = new MetricConfig(
                true, "joins_throttled", "The number of joins turned away by the server throttle (a breakdown of joins_refused, not in addition to it)", Map.of("id", "JOIN_THROTTLED")
        );

        MetricConfig addressesRefused = new MetricConfig(
                true, "addresses_refused", "The number of signaling connections refused because the address already holds too many", Map.of("id", "ADDRESS_REFUSED")
        );

        MetricConfig plaintextRefused = new MetricConfig(
                true, "plaintext_refused", "The number of signaling requests refused for arriving without TLS where it is required", Map.of("id", "PLAINTEXT_REFUSED")
        );

        // Per connection events, summed over all connections
        MetricConfig connectionFailures = new MetricConfig(
                true, "connection_failures", "The number of connections that never carried traffic, tagged by reason", Map.of("id", "CONNECTION_FAILURE")
        );

        MetricConfig handshakeRetries = new MetricConfig(
                true, "handshake_retries", "The number of WebRTC handshake retries", Map.of("id", "HANDSHAKE_RETRY")
        );

        MetricConfig pathsSelected = new MetricConfig(
                true, "ice_paths_selected", "The number of ICE candidate pairs selected, tagged by local and remote candidate type (host, srflx, prflx, relay)", Map.of("id", "ICE_PATH_SELECTED")
        );

        MetricConfig bytesIn = new MetricConfig(
                true, "bytes_in", "The number of bytes received over NetherNet", Map.of("id", "BYTES_IN")
        );

        MetricConfig bytesOut = new MetricConfig(
                true, "bytes_out", "The number of bytes sent over NetherNet", Map.of("id", "BYTES_OUT")
        );

        MetricConfig messagesIn = new MetricConfig(
                true, "messages_in", "The number of whole messages received over NetherNet", Map.of("id", "MESSAGES_IN")
        );

        MetricConfig messagesOut = new MetricConfig(
                true, "messages_out", "The number of whole messages sent over NetherNet", Map.of("id", "MESSAGES_OUT")
        );

        MetricConfig fragmentsIn = new MetricConfig(
                true, "fragments_in", "The number of non-final fragments of split messages received", Map.of("id", "FRAGMENTS_IN")
        );

        MetricConfig fragmentsOut = new MetricConfig(
                true, "fragments_out", "The number of non-final fragments of split messages sent", Map.of("id", "FRAGMENTS_OUT")
        );

        MetricConfig decodeFailures = new MetricConfig(
                true, "decode_failures", "The number of inbound messages that could not be delivered (missing fragments or empty frames)", Map.of("id", "DECODE_FAILURE")
        );

        MetricConfig connectedPlayers = new MetricConfig(
                true, "connected_players", "The number of connected players", Map.of("id", "CONNECTED_PLAYERS")
        );

        JvmMetricConfig classLoaderMetrics = new JvmMetricConfig(
                true, Map.of("id", "CLASS_LOADER_METRICS")
        );
        JvmMetricConfig jvmMemoryMetrics = new JvmMetricConfig(
                true, Map.of("id", "JVM_MEMORY_METRICS")
        );
        JvmMetricConfig jvmGcMetrics = new JvmMetricConfig(
                true, Map.of("id", "JVM_GC_METRICS")
        );
        JvmMetricConfig processorMetrics = new JvmMetricConfig(
                true, Map.of("id", "PROCESSOR_METRICS")
        );
        JvmMetricConfig jvmThreadMetrics = new JvmMetricConfig(
                true, Map.of("id", "JVM_THREAD_METRICS")
        );
        JvmMetricConfig jvmThreadDeadlockMetrics = new JvmMetricConfig(
                true, Map.of("id", "JVM_THREAD_DEADLOCK_METRICS")
        );
    }

    @Getter
    @Accessors(fluent = true)
    @ConfigSerializable
    public static class HealthCheck {

        @Comment("Whether to enable the health check endpoint.")
        boolean enabled = true;

        @Comment("The endpoint of the health check")
        String endpoint = "/health";
    }

    @Getter
    @Accessors(fluent = true)
    @AllArgsConstructor
    @ConfigSerializable
    public static class MetricConfig {
        public MetricConfig() {} // Config access

        @Comment("Whether to enable this metric.")
        boolean enabled;

        @Comment("The name of this metric name.")
        String name;

        @Comment("The description")
        String description;

        @Comment("The tags, see the micrometer docs https://docs.micrometer.io/micrometer/reference/concepts/naming.html")
        Map<String, String> tags;

        public MetricConfig(boolean enabled, String name, String description) {
            this.enabled = enabled;
            this.name = name;
            this.description = description;
            this.tags = new HashMap<>();
        }
    }

    @Getter
    @Accessors(fluent = true)
    @AllArgsConstructor
    @ConfigSerializable
    public static class JvmMetricConfig {
        public JvmMetricConfig() {} // Config access

        @Comment("Whether to enable this metric.")
        boolean enabled;

        @Comment("The tags, see the micrometer docs https://docs.micrometer.io/micrometer/reference/concepts/naming.html")
        Map<String, String> tags;

        public JvmMetricConfig(boolean enabled) {
            this.enabled = enabled;
            this.tags = new HashMap<>();
        }
    }
}
