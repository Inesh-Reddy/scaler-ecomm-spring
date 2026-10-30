package com.example.ecomm.mcp.catalog;

import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.server.transport.StdioServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.JsonSchema;
import io.modelcontextprotocol.spec.McpSchema.ServerCapabilities;
import io.modelcontextprotocol.spec.McpSchema.Tool;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

/**
 * Local (stdio) MCP server for the ShopNest catalog service.
 * <p>
 * stdout is the protocol channel — diagnostics go to stderr (SLF4J simple does this by default).
 */
public final class CatalogMcpServer {

    private static final String NAME = "catalog";
    private static final String VERSION = "1.0.0";

    private CatalogMcpServer() {
    }

    public static void main(String[] args) throws InterruptedException {
        CatalogClient client = CatalogClient.fromEnvironment();
        StdioServerTransportProvider transport = new StdioServerTransportProvider(McpJsonDefaults.getMapper());

        McpSyncServer server = McpServer.sync(transport)
                .serverInfo(NAME, VERSION)
                .instructions("Search the ShopNest product catalog (" + client.baseUrl() + "). "
                        + "Only ACTIVE products are returned; deleted ones are soft-deleted by the service and never "
                        + "appear. Use search_products for keyword/price/category lookups, get_product to fetch one id, "
                        + "list_categories to see the catalog shape, and get_product_pitch for marketing copy on one product.")
                .capabilities(ServerCapabilities.builder().tools(false).build())
                .validateToolInputs(true)
                .tools(
                        searchProductsTool(client),
                        getProductTool(client),
                        listCategoriesTool(client),
                        getProductPitchTool(client))
                .build();

        Runtime.getRuntime().addShutdownHook(new Thread(server::closeGracefully));

        System.err.println("[catalog-mcp] ready, catalog at " + client.baseUrl());
        // Deliberately no latch: the stdio read loop runs on its own (non-daemon) threads, so main may
        // return and the process stays up until the client closes the pipe or the JVM is stopped.
    }

    private static SyncToolSpecification searchProductsTool(CatalogClient client) {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("query", Map.of(
                "type", "string",
                "description", "Free-text terms, e.g. \"wireless mouse\"."));
        properties.put("category", Map.of(
                "type", "string",
                "description", "Category name filter; substring, case-insensitive."));
        properties.put("minPrice", Map.of(
                "type", "number",
                "minimum", 0,
                "description", "Inclusive lower price bound."));
        properties.put("maxPrice", Map.of(
                "type", "number",
                "minimum", 0,
                "description", "Inclusive upper price bound."));
        properties.put("limit", Map.of(
                "type", "integer",
                "minimum", 1,
                "maximum", 50,
                "default", 10,
                "description", "Maximum products to return."));
        properties.put("sort", Map.of(
                "type", "string",
                "enum", List.of("relevance", "price_asc", "price_desc", "name"),
                "default", "relevance",
                "description", "relevance ranks name matches first, then cheapest."));

        return tool("search_products",
                "Search products",
                "Keyword search across product name, description and category. All whitespace-separated terms must "
                        + "match; matching is case-insensitive substring, so partial words work. Optionally narrow by "
                        + "category and price range. Returns matching products plus how many rows were scanned.",
                objectSchema(properties),
                (exchange, request) -> {
                    Map<String, Object> args = arguments(request);
                    try {
                        String query = stringArg(args, "query");
                        String category = stringArg(args, "category");
                        BigDecimal minPrice = decimalArg(args, "minPrice");
                        BigDecimal maxPrice = decimalArg(args, "maxPrice");
                        int limit = intArg(args, "limit", 10);
                        Search.Sort sort = Search.Sort.from(stringArg(args, "sort"));

                        ScanResult scan = client.scan(minPrice, maxPrice);
                        Search.Result result = Search.search(scan.products(), query, category, sort, limit);

                        Map<String, Object> priceRange = new LinkedHashMap<>();
                        priceRange.put("min", minPrice);
                        priceRange.put("max", maxPrice);

                        Map<String, Object> payload = new LinkedHashMap<>();
                        payload.put("query", query);
                        payload.put("category", category);
                        payload.put("priceRange", priceRange);
                        payload.put("totalMatches", result.total());
                        payload.put("returned", result.products().size());
                        payload.put("scanned", scan.products().size());
                        // Only meaningful when the result may be incomplete: a scan cap was hit.
                        payload.put("truncated", scan.truncated());
                        payload.put("products", result.products());
                        return ok(payload);
                    } catch (RuntimeException e) {
                        return fail(e);
                    }
                });
    }

    private static SyncToolSpecification getProductTool(CatalogClient client) {
        Map<String, Object> properties = Map.of("id", Map.of(
                "type", "integer",
                "minimum", 1,
                "description", "Product id."));

        return tool("get_product",
                "Get product",
                "Fetch one active product by id, including its image URL and full description.",
                objectSchema(properties, List.of("id")),
                (exchange, request) -> {
                    try {
                        return ok(client.product(intArg(arguments(request), "id", 0)));
                    } catch (RuntimeException e) {
                        return fail(e);
                    }
                });
    }

    private static SyncToolSpecification listCategoriesTool(CatalogClient client) {
        return tool("list_categories",
                "List categories",
                "List every category that currently has active products, with product counts and price spans.",
                objectSchema(Map.of()),
                (exchange, request) -> {
                    try {
                        ScanResult scan = client.scan(null, null);
                        Map<String, Object> payload = new LinkedHashMap<>();
                        payload.put("categories", Search.categories(scan.products()));
                        payload.put("scanned", scan.products().size());
                        payload.put("truncated", scan.truncated());
                        return ok(payload);
                    } catch (RuntimeException e) {
                        return fail(e);
                    }
                });
    }

    private static SyncToolSpecification getProductPitchTool(CatalogClient client) {
        Map<String, Object> properties = Map.of("id", Map.of(
                "type", "integer",
                "minimum", 1,
                "description", "Product id."));

        return tool("get_product_pitch",
                "Get product pitch",
                "Generate marketing copy for one product. The catalog service calls Groq under the hood, so this is "
                        + "slower than a read and requires the service's GROQ_API_KEY to be configured.",
                objectSchema(properties, List.of("id")),
                (exchange, request) -> {
                    try {
                        return ok(client.pitch(intArg(arguments(request), "id", 0)));
                    } catch (RuntimeException e) {
                        return fail(e);
                    }
                });
    }

    private static SyncToolSpecification tool(String name,
                                              String title,
                                              String description,
                                              JsonSchema inputSchema,
                                              BiFunction<McpSyncServerExchange, CallToolRequest, CallToolResult> handler) {
        return SyncToolSpecification.builder()
                .tool(Tool.builder(name)
                        .title(title)
                        .description(description)
                        .inputSchema(inputSchema)
                        .build())
                .callHandler(handler)
                .build();
    }

    private static JsonSchema objectSchema(Map<String, Object> properties) {
        return objectSchema(properties, List.of());
    }

    private static JsonSchema objectSchema(Map<String, Object> properties, List<String> required) {
        return JsonSchema.builder()
                .type("object")
                .properties(properties)
                .required(required)
                .additionalProperties(false)
                .build();
    }

    private static CallToolResult ok(Object payload) {
        return CallToolResult.builder().addTextContent(Json.pretty(payload)).build();
    }

    private static CallToolResult fail(RuntimeException error) {
        String message = error instanceof CatalogClient.CatalogException || error instanceof IllegalArgumentException
                ? error.getMessage()
                : "Unexpected error: " + error.getMessage();
        return CallToolResult.builder().addTextContent(message).isError(true).build();
    }

    private static Map<String, Object> arguments(CallToolRequest request) {
        return request.arguments() == null ? Map.of() : request.arguments();
    }

    private static String stringArg(Map<String, Object> args, String key) {
        Object value = args.get(key);
        return value == null ? null : String.valueOf(value);
    }

    private static BigDecimal decimalArg(Map<String, Object> args, String key) {
        Object value = args.get(key);
        if (value == null) {
            return null;
        }
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        return value instanceof Number number ? new BigDecimal(number.toString()) : new BigDecimal(String.valueOf(value));
    }

    private static int intArg(Map<String, Object> args, String key, int fallback) {
        Object value = args.get(key);
        if (value == null) {
            return fallback;
        }
        return value instanceof Number number ? number.intValue() : Integer.parseInt(String.valueOf(value));
    }
}
