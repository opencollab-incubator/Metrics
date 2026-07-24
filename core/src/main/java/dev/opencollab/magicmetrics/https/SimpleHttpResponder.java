package dev.opencollab.magicmetrics.https;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public interface SimpleHttpResponder extends HttpHandler {
    @Override
    default void handle(HttpExchange exchange) throws IOException {
        HttpResponse response = getResponse(exchange);
        byte[] bytes = response.message().getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(response.statusCode(), bytes.length);
        OutputStream os = exchange.getResponseBody();
        os.write(bytes);
        os.close();
    }

    HttpResponse getResponse(HttpExchange exchange);

    record HttpResponse(int statusCode, String message) {}
}
