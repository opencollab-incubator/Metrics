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
        String endpoint;

        MetricConfig activeChannels = new MetricConfig(
                true, "active_channels", "The number of active channels"
        );

        MetricConfig blockedAddresses = new MetricConfig(
                true, "blocked_addresses", "The address of the blocked channels due to exceeding packet limits"
        );

        MetricConfig pings = new MetricConfig(
                true, "total_pings", "Count of received unconnected pings"
        );

        MetricConfig connectionRequests1 = new MetricConfig(
                true, "connection_requests_one", "The number of unconnected packets", Map.of("id", "CONN_REQ_1")
        );

        MetricConfig connectionRequests2 = new MetricConfig(
                true, "connection_requests_two", "The number of unconnected packets", Map.of("id", "CONN_REQ_2")
        );

        MetricConfig connectionRequests = new MetricConfig(
                true, "connection_requests", "The number of unconnected packets", Map.of("id", "CONNECTION_REQUEST")
        );

        MetricConfig newIncomingConnections = new MetricConfig(
                true, "new_incoming_connections", "The number of unconnected packets", Map.of("id", "NEW_INCOMING_CONNECTION")
        );
    }

    @Getter
    @Accessors(fluent = true)
    @ConfigSerializable
    public static class HealthCheck {

        @Comment("Whether to enable the health check endpoint.")
        boolean enabled = true;

        @Comment("The endpoint of the health check")
        String endpoint = "/health/";
    }

    @Getter
    @Accessors(fluent = true)
    @AllArgsConstructor
    @ConfigSerializable
    public static class MetricConfig {

        @Comment("Whether to enable this metric.")
        boolean enabled;

        @Comment("The name of this metric name.")
        String name;

        @Comment("The description")
        String description;

        @Comment("The tags, TODO link docs")
        Map<String, String> tags;

        public MetricConfig(boolean enabled, String name, String description) {
            this.enabled = enabled;
            this.name = name;
            this.description = description;
            this.tags = new HashMap<>();
        }
    }

}
