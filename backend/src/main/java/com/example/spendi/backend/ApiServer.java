package com.example.spendi.backend;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import static com.example.spendi.backend.Models.*;

/** Local HTTP API. Public deployment requires a TLS ingress with request timeouts. */
public final class ApiServer implements AutoCloseable {
    private static final int MAX_BODY = 16_384;
    private final HttpServer server;
    private final java.util.concurrent.ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final Semaphore capacity = new Semaphore(128);
    private final FinancialRepository repository;
    private final Authenticator authenticator;
    public ApiServer(String host, int port, FinancialRepository repository, Authenticator authenticator) throws IOException {
        this.repository = repository;
        this.authenticator = authenticator;
        server = HttpServer.create(new InetSocketAddress(host, port), 128);
        server.setExecutor(executor);
        server.createContext("/", this::handle);
    }
    public int port() { return server.getAddress().getPort(); }
    public void start() { server.start(); }
    @Override public void close() { server.stop(1); executor.close(); }

    private void handle(HttpExchange exchange) throws IOException {
        String requestId = UUID.randomUUID().toString();
        exchange.getResponseHeaders().set("X-Request-Id", requestId);
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
        boolean acquired = capacity.tryAcquire();
        try {
            if (!acquired) throw new ApiException(503, "busy", "Please retry later");
            route(exchange);
        } catch (ApiException exception) {
            if (exception.status == 401) exchange.getResponseHeaders().set("WWW-Authenticate", "Bearer");
            reply(exchange, exception.status, Map.of("code", exception.code, "message", exception.getMessage(), "requestId", requestId));
        } catch (IllegalArgumentException exception) {
            reply(exchange, 400, Map.of("code", "invalid_request", "message", "Invalid identifier or query parameter", "requestId", requestId));
        } catch (Exception exception) {
            // Do not log SQL parameters, request bodies, authentication tokens, or exception messages.
            System.getLogger(ApiServer.class.getName()).log(System.Logger.Level.ERROR,
                    "Request {0} failed ({1})", requestId, exception.getClass().getSimpleName());
            reply(exchange, 500, Map.of("code", "internal_error", "message", "The request could not be completed", "requestId", requestId));
        } finally {
            if (acquired) capacity.release();
            exchange.close();
        }
    }

    private void route(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        String path = exchange.getRequestURI().getPath();
        if (path.length() > 512) throw ApiException.missing();
        if (path.equals("/health/live")) {
            requireMethod(exchange, method, "GET");
            reply(exchange, 200, Map.of("status", "up"));
            return;
        }
        if (path.equals("/health/ready")) {
            requireMethod(exchange, method, "GET");
            boolean ready = repository.ready();
            reply(exchange, ready ? 200 : 503, Map.of("status", ready ? "ready" : "unavailable"));
            return;
        }
        String owner = owner(exchange);
        if (path.equals("/v1/transactions")) {
            requireMethod(exchange, method, "GET", "POST");
            if (method.equals("GET")) {
                var query = query(exchange, Set.of("month", "limit", "offset"));
                reply(exchange, 200, repository.list(owner, month(query.get("month")),
                        Integer.parseInt(query.getOrDefault("limit", "50")), Integer.parseInt(query.getOrDefault("offset", "0"))));
            } else {
                query(exchange, Set.of());
                var created = repository.create(owner, singleHeader(exchange, "Idempotency-Key"), body(exchange, TransactionWrite.class));
                exchange.getResponseHeaders().set("Location", "/v1/transactions/" + created.transaction().id());
                exchange.getResponseHeaders().set("Idempotency-Replayed", Boolean.toString(created.replayed()));
                reply(exchange, 201, created.transaction());
            }
            return;
        }
        String[] segments = path.split("/", -1);
        if (segments.length != 4 || !segments[1].equals("v1") || segments[3].isBlank()) throw ApiException.missing();
        switch (segments[2]) {
            case "transactions" -> {
                requireMethod(exchange, method, "GET", "PUT", "DELETE");
                UUID id = UUID.fromString(segments[3]);
                if (method.equals("GET")) {
                    query(exchange, Set.of());
                    reply(exchange, 200, repository.get(owner, id));
                } else if (method.equals("PUT")) {
                    query(exchange, Set.of());
                    reply(exchange, 200, repository.update(owner, id, body(exchange, TransactionWrite.class)));
                } else {
                    var query = query(exchange, Set.of("version"));
                    repository.delete(owner, id, Long.parseLong(query.getOrDefault("version", "0")));
                    exchange.sendResponseHeaders(204, -1);
                }
            }
            case "budgets" -> {
                requireMethod(exchange, method, "GET", "PUT");
                query(exchange, Set.of());
                var month = month(segments[3]);
                if (method.equals("GET")) reply(exchange, 200, repository.budget(owner, month));
                else {
                    var budget = repository.saveBudget(owner, month, body(exchange, BudgetWrite.class));
                    reply(exchange, budget.version() == 1 ? 201 : 200, budget);
                }
            }
            case "summaries" -> {
                requireMethod(exchange, method, "GET");
                query(exchange, Set.of());
                reply(exchange, 200, repository.summary(owner, month(segments[3])));
            }
            default -> throw ApiException.missing();
        }
    }

    private String owner(HttpExchange exchange) {
        String authorization = singleHeader(exchange, "Authorization");
        if (authorization == null || authorization.length() > 8200 || !authorization.regionMatches(true, 0, "Bearer ", 0, 7)) throw Authenticator.unauthorized();
        return authenticator.subject(authorization.substring(7));
    }
    private static String singleHeader(HttpExchange exchange, String name) {
        var values = exchange.getRequestHeaders().get(name);
        if (values == null || values.isEmpty()) return null;
        if (values.size() != 1) throw ApiException.invalid("Duplicate " + name + " header");
        return values.getFirst();
    }
    private static <T> T body(HttpExchange exchange, Class<T> type) throws IOException {
        String contentType = singleHeader(exchange, "Content-Type");
        if (contentType == null || !contentType.split(";", 2)[0].trim().equalsIgnoreCase("application/json")) {
            throw new ApiException(415, "unsupported_media_type", "Content-Type must be application/json");
        }
        byte[] bytes = exchange.getRequestBody().readNBytes(MAX_BODY + 1);
        if (bytes.length > MAX_BODY) throw new ApiException(413, "body_too_large", "Request body exceeds 16 KiB");
        return Json.read(new String(bytes, StandardCharsets.UTF_8), type);
    }
    private static Map<String, String> query(HttpExchange exchange, Set<String> allowed) {
        String raw = exchange.getRequestURI().getRawQuery();
        Map<String, String> values = new HashMap<>();
        if (raw == null || raw.isEmpty()) return values;
        if (raw.length() > 1024) throw ApiException.invalid("Query is too long");
        for (String pair : raw.split("&")) {
            String[] entry = pair.split("=", 2);
            String key = URLDecoder.decode(entry[0], StandardCharsets.UTF_8);
            String value = entry.length == 2 ? URLDecoder.decode(entry[1], StandardCharsets.UTF_8) : "";
            if (!allowed.contains(key) || values.putIfAbsent(key, value) != null) throw ApiException.invalid("Unsupported or duplicate query parameter");
        }
        return values;
    }
    private static void requireMethod(HttpExchange exchange, String actual, String... allowed) {
        if (!Set.of(allowed).contains(actual)) {
            exchange.getResponseHeaders().set("Allow", String.join(", ", allowed));
            throw new ApiException(405, "method_not_allowed", "Method not allowed");
        }
    }
    private static void reply(HttpExchange exchange, int status, Object value) throws IOException {
        byte[] bytes = Json.write(value).getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
    }
}
