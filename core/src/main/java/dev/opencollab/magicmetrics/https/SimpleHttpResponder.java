package dev.opencollab.magicmetrics.https;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.OutputStream;

public interface SimpleHttpResponder extends HttpHandler {
    @Override
    default void handle(HttpExchange exchange) throws IOException {
        HttpResponse response = getResponse(exchange);
        exchange.sendResponseHeaders(response.statusCode(), response.message().length());
        OutputStream os = exchange.getResponseBody();
        os.write(response.message().getBytes());
        os.close();
    }

    HttpResponse getResponse(HttpExchange exchange);

    record HttpResponse(int statusCode, String message) {}
}
