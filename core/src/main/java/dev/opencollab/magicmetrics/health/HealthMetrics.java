package dev.opencollab.magicmetrics.health;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import dev.opencollab.magicmetrics.MagicMetrics;
import io.smallrye.health.SmallRyeHealth;
import jakarta.json.JsonWriter;
import jakarta.json.JsonWriterFactory;
import jakarta.json.spi.JsonProvider;
import jakarta.json.stream.JsonGenerator;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Map;

public class HealthMetrics implements HttpHandler {
    private final JsonProvider jsonProvider = JsonProvider.provider();
    private final MagicMetrics metrics;

    public HealthMetrics(MagicMetrics metrics) {
        this.metrics = metrics;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        SmallRyeHealth health = metrics.getReporter().getHealth();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        JsonWriterFactory factory = jsonProvider.createWriterFactory(Map.of(JsonGenerator.PRETTY_PRINTING, true));
        JsonWriter writer = factory.createWriter(out);

        writer.writeObject(health.getPayload());
        writer.close();

        exchange.sendResponseHeaders(200, out.size());
        OutputStream os = exchange.getResponseBody();
        os.write(out.toByteArray());
        os.close();
    }
}
