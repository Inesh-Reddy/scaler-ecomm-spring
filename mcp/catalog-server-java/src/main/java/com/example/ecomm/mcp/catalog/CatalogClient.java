package com.example.ecomm.mcp.catalog;

import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * HTTP access to the catalog service.
 * <p>
 * Reads go through the service's own REST API (never straight to MySQL) so soft-deleted rows stay
 * invisible and the service remains the single source of truth.
 */
public final class CatalogClient {

    /**
     * {@code getAllProducts} runs {@code price BETWEEN ? AND ?}, and a BETWEEN with a null bound matches
     * nothing — {@code GET /products} with no minPrice/maxPrice returns {@code []}. So every scan sends
     * explicit bounds; these are the widest ones the DECIMAL(10,2) column can hold.
     */
    public static final BigDecimal PRICE_FLOOR = BigDecimal.ZERO;
    public static final BigDecimal PRICE_CEILING = new BigDecimal("99999999.99");

    private static final int PAGE_SIZE = 100;

    private final HttpClient http;
    private final String baseUrl;
    private final JsonMapper mapper;
    private final Duration timeout;
    private final int maxScan;

    public CatalogClient(String baseUrl, JsonMapper mapper, Duration timeout, int maxScan) {
        this.http = HttpClient.newBuilder().connectTimeout(timeout).build();
        this.baseUrl = baseUrl.replaceAll("/+$", "");
        this.mapper = mapper;
        this.timeout = timeout;
        this.maxScan = maxScan;
    }

    public static CatalogClient fromEnvironment() {
        return new CatalogClient(
                env("CATALOG_BASE_URL", "http://localhost:8080"),
                Json.mapper(),
                Duration.ofMillis(Long.parseLong(env("CATALOG_TIMEOUT_MS", "5000"))),
                Integer.parseInt(env("CATALOG_MAX_SCAN", "1000")));
    }

    /**
     * Walks every page in the price window.
     * <p>
     * The list endpoint returns a plain array (no total), so the scan stops at the first short page or
     * at {@code maxScan}, whichever comes first.
     */
    public ScanResult scan(BigDecimal minPrice, BigDecimal maxPrice) {
        List<Product> products = new ArrayList<>();
        boolean truncated = false;

        for (int page = 0; ; page++) {
            List<Product> batch = page(minPrice, maxPrice, page);
            products.addAll(batch);

            if (batch.size() < PAGE_SIZE) {
                break;
            }
            if (products.size() >= maxScan) {
                truncated = true;
                break;
            }
        }

        if (products.size() > maxScan) {
            return new ScanResult(List.copyOf(products.subList(0, maxScan)), true);
        }
        return new ScanResult(List.copyOf(products), truncated);
    }

    public Product product(long id) {
        try {
            return mapper.readValue(get("/products/" + id), Product.class);
        } catch (ProductNotFoundException e) {
            throw new ProductNotFoundException("No active product with id " + id + ".");
        }
    }

    public Pitch pitch(long id) {
        try {
            return mapper.readValue(get("/products/" + id + "/pitch"), Pitch.class);
        } catch (ProductNotFoundException e) {
            throw new ProductNotFoundException("No active product with id " + id + ".");
        }
    }

    public String baseUrl() {
        return baseUrl;
    }

    private List<Product> page(BigDecimal minPrice, BigDecimal maxPrice, int page) {
        String query = "minPrice=" + encode(minPrice == null ? PRICE_FLOOR : minPrice)
                + "&maxPrice=" + encode(maxPrice == null ? PRICE_CEILING : maxPrice)
                + "&page=" + page
                + "&size=" + PAGE_SIZE
                // Stable paging, so a scan cannot see the same row twice.
                + "&sort=id,asc";

        Product[] batch = mapper.readValue(get("/products?" + query), Product[].class);
        return List.of(batch);
    }

    private String get(String path) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + path))
                .timeout(timeout)
                .header("Accept", "application/json")
                .GET()
                .build();

        HttpResponse<String> response;
        try {
            response = http.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new CatalogException("Cannot reach the catalog service at " + baseUrl
                    + " (" + e.getMessage() + "). Start it with ./mvnw spring-boot:run, or point "
                    + "CATALOG_BASE_URL at a running instance.", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new CatalogException("Interrupted while calling the catalog service", e);
        }

        if (response.statusCode() == 404) {
            throw new ProductNotFoundException("GET " + path + " returned 404.");
        }
        if (response.statusCode() / 100 != 2) {
            throw new CatalogException("GET " + path + " returned HTTP " + response.statusCode() + ".");
        }
        return response.body();
    }

    private static String encode(BigDecimal value) {
        return URLEncoder.encode(value.toPlainString(), StandardCharsets.UTF_8);
    }

    private static String env(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }

    /** The catalog service could not be reached, or answered with a non-2xx status. */
    public static class CatalogException extends RuntimeException {
        public CatalogException(String message) {
            super(message);
        }

        public CatalogException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /** The catalog service answered 404: no such active product. */
    public static final class ProductNotFoundException extends CatalogException {
        public ProductNotFoundException(String message) {
            super(message);
        }
    }
}
