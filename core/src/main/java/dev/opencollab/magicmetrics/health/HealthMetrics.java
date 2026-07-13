package dev.opencollab.magicmetrics.health;

import com.sun.net.httpserver.HttpExchange;
import dev.opencollab.magicmetrics.MagicMetrics;
import dev.opencollab.magicmetrics.https.SimpleHttpResponder;

public class HealthMetrics implements SimpleHttpResponder {
    private final MagicMetrics metrics;

    public HealthMetrics(MagicMetrics metrics) {
        this.metrics = metrics;
    }

    @Override
    public HttpResponse getResponse(HttpExchange exchange) {
        HealthResult result = metrics.getCurrentHealthResult();
        return new HttpResponse(result.statusCode(), "{\"short_code\": \"%s\",\"message\": \"%s\"}".formatted(result.shortCode(), result.message()));
    }
}
