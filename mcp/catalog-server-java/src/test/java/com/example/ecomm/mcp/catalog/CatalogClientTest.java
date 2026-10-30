package com.example.ecomm.mcp.catalog;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises the HTTP contract against a stub catalog service, so no Spring context or MySQL is needed.
 */
class CatalogClientTest {

    private HttpServer server;
    private CatalogClient client;
    private final List<String> requestedPaths = new CopyOnWriteArrayList<>();
    private volatile Function<String, Reply> responder = path -> new Reply(200, "[]");

    private record Reply(int status, String body) {
    }

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            String pathAndQuery = exchange.getRequestURI().getPath()
                    + (exchange.getRequestURI().getQuery() == null ? "" : "?" + exchange.getRequestURI().getQuery());
            requestedPaths.add(pathAndQuery);

            Reply reply = responder.apply(pathAndQuery);
            byte[] body = reply.body().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(reply.status(), body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();

        client = new CatalogClient(
                "http://127.0.0.1:" + server.getAddress().getPort(),
                Json.mapper(),
                Duration.ofSeconds(5),
                1000);
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    @DisplayName("a scan with no price window still sends explicit bounds, plus stable id ordering")
    void scanSendsExplicitBounds() {
        client.scan(null, null);

        String requested = requestedPaths.get(0);
        assertTrue(requested.contains("minPrice=0"), requested);
        assertTrue(requested.contains("maxPrice=99999999.99"), requested);
        assertTrue(requested.contains("sort=id,asc"), requested);
        assertTrue(requested.contains("page=0"), requested);
    }

    @Test
    @DisplayName("a supplied price window is passed through unchanged")
    void scanPassesPriceWindow() {
        client.scan(new BigDecimal("10.50"), new BigDecimal("20.00"));

        String requested = requestedPaths.get(0);
        assertTrue(requested.contains("minPrice=10.50"), requested);
        assertTrue(requested.contains("maxPrice=20.00"), requested);
    }

    @Test
    @DisplayName("a full page is followed by the next one, and a short page ends the scan")
    void scanFollowsPages() {
        responder = path -> path.contains("page=0")
                ? new Reply(200, products(100))
                : new Reply(200, products(1));

        ScanResult result = client.scan(null, null);

        assertEquals(101, result.products().size());
        assertFalse(result.truncated());
        assertEquals(2, requestedPaths.size());
        assertTrue(requestedPaths.get(1).contains("page=1"), requestedPaths.get(1));
    }

    @Test
    @DisplayName("the scan cap stops paging and reports truncation")
    void scanHonoursMaxScan() {
        CatalogClient capped = new CatalogClient(
                "http://127.0.0.1:" + server.getAddress().getPort(), Json.mapper(), Duration.ofSeconds(5), 150);
        responder = path -> new Reply(200, products(100));

        ScanResult result = capped.scan(null, null);

        assertEquals(150, result.products().size());
        assertTrue(result.truncated());
    }

    @Test
    @DisplayName("a product id is fetched and decoded into the record")
    void fetchProduct() {
        responder = path -> new Reply(200, """
                {"id":2,"name":"Wireless Mouse","description":"2.4GHz wireless mouse","price":25.00,
                 "imageUrl":"mouse.png","category":{"id":null,"name":"electronics","description":"Gadgets"}}
                """);

        Product product = client.product(2);

        assertEquals(2L, product.id());
        assertEquals("Wireless Mouse", product.name());
        assertEquals(new BigDecimal("25.00"), product.price());
        assertEquals("electronics", product.category().name());
        assertTrue(requestedPaths.get(0).equals("/products/2"), requestedPaths.get(0));
    }

    @Test
    @DisplayName("unknown fields in a response are ignored, so the service can evolve")
    void unknownFieldsAreIgnored() {
        responder = path -> new Reply(200, """
                {"id":3,"name":"Yoga Mat","price":19.99,"category":null,"brandNewField":"ignored"}
                """);

        assertEquals("Yoga Mat", client.product(3).name());
    }

    @Test
    @DisplayName("a 404 becomes ProductNotFoundException naming the id")
    void missingProduct() {
        responder = path -> new Reply(404, "");

        CatalogClient.ProductNotFoundException thrown =
                assertThrows(CatalogClient.ProductNotFoundException.class, () -> client.product(999));

        assertEquals("No active product with id 999.", thrown.getMessage());
    }

    @Test
    @DisplayName("a non-2xx response is surfaced with the path and status")
    void serverError() {
        responder = path -> new Reply(500, "");

        CatalogClient.CatalogException thrown =
                assertThrows(CatalogClient.CatalogException.class, () -> client.product(2));

        assertTrue(thrown.getMessage().contains("HTTP 500"), thrown.getMessage());
    }

    @Test
    @DisplayName("an unreachable service explains how to start it")
    void unreachableService() {
        CatalogClient dead = new CatalogClient("http://127.0.0.1:1", Json.mapper(), Duration.ofMillis(500), 10);

        CatalogClient.CatalogException thrown = assertThrows(CatalogClient.CatalogException.class, () -> dead.scan(null, null));

        assertTrue(thrown.getMessage().contains("spring-boot:run"), thrown.getMessage());
    }

    private static String products(int count) {
        StringBuilder json = new StringBuilder("[");
        for (int i = 1; i <= count; i++) {
            if (i > 1) {
                json.append(',');
            }
            json.append("{\"id\":").append(i)
                    .append(",\"name\":\"Product ").append(i)
                    .append("\",\"price\":1.00,\"category\":{\"name\":\"misc\"}}");
        }
        return json.append(']').toString();
    }
}
