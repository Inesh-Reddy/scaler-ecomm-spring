package com.example.ecomm.mcp.catalog;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Pure search/ranking over already-fetched catalog rows, free of I/O so it is easy to test. */
public final class Search {

    private static final String UNCATEGORISED = "(uncategorised)";

    private Search() {
    }

    public enum Sort {
        /** Name matches rank first, then cheapest. */
        RELEVANCE,
        PRICE_ASC,
        PRICE_DESC,
        NAME;

        public static Sort from(String raw) {
            if (raw == null || raw.isBlank()) {
                return RELEVANCE;
            }
            return switch (raw.trim().toLowerCase(Locale.ROOT)) {
                case "relevance" -> RELEVANCE;
                case "price_asc" -> PRICE_ASC;
                case "price_desc" -> PRICE_DESC;
                case "name" -> NAME;
                default -> throw new IllegalArgumentException("Unsupported sort \"" + raw + "\"");
            };
        }
    }

    /** The fields a caller needs, with the category flattened to its name. */
    public record Summary(Long id, String name, BigDecimal price, String category, String description) {
    }

    /** @param total every match, before the limit is applied */
    public record Result(int total, List<Summary> products) {
    }

    public record CategorySummary(String name, int productCount, BigDecimal minPrice, BigDecimal maxPrice) {
    }

    public static List<String> tokenize(String query) {
        if (query == null) {
            return List.of();
        }
        return Arrays.stream(query.toLowerCase(Locale.ROOT).trim().split("\\s+"))
                .filter(token -> !token.isEmpty())
                .toList();
    }

    /** Every token must appear somewhere in name/description/category, and the category must match. */
    public static boolean matches(Product product, List<String> tokens, String category) {
        if (category != null && !category.isBlank()
                && !categoryName(product).contains(category.trim().toLowerCase(Locale.ROOT))) {
            return false;
        }
        String text = haystack(product);
        return tokens.stream().allMatch(text::contains);
    }

    public static Result search(List<Product> products, String query, String category, Sort sort, int limit) {
        List<String> tokens = tokenize(query);
        List<Product> matched = products.stream()
                .filter(product -> matches(product, tokens, category))
                .toList();

        List<Product> sorted = new ArrayList<>(matched);
        sorted.sort(comparator(sort, tokens));

        List<Summary> summaries = sorted.stream().limit(limit).map(Search::summarize).toList();
        return new Result(matched.size(), summaries);
    }

    /** Category rollup: product count and price span per category name. */
    public static List<CategorySummary> categories(List<Product> products) {
        Map<String, List<Product>> byCategory = new LinkedHashMap<>();
        for (Product product : products) {
            byCategory.computeIfAbsent(categoryDisplayName(product), name -> new ArrayList<>()).add(product);
        }

        return byCategory.entrySet().stream()
                .map(entry -> {
                    List<BigDecimal> prices = entry.getValue().stream().map(Search::price).toList();
                    return new CategorySummary(
                            entry.getKey(),
                            entry.getValue().size(),
                            prices.stream().min(BigDecimal::compareTo).orElse(BigDecimal.ZERO),
                            prices.stream().max(BigDecimal::compareTo).orElse(BigDecimal.ZERO));
                })
                .sorted(Comparator.comparing(CategorySummary::name))
                .toList();
    }

    public static Summary summarize(Product product) {
        return new Summary(
                product.id(),
                product.name(),
                product.price(),
                product.category() == null ? null : product.category().name(),
                product.description());
    }

    private static Comparator<Product> comparator(Sort sort, List<String> tokens) {
        return switch (sort) {
            case PRICE_ASC -> Comparator.comparing(Search::price);
            case PRICE_DESC -> Comparator.comparing(Search::price).reversed();
            case NAME -> Comparator.comparing(product -> String.valueOf(product.name()));
            case RELEVANCE -> Comparator
                    .comparingInt((Product product) -> -nameHits(product, tokens))
                    .thenComparing(Search::price)
                    .thenComparing(product -> String.valueOf(product.name()));
        };
    }

    private static int nameHits(Product product, List<String> tokens) {
        String name = String.valueOf(product.name()).toLowerCase(Locale.ROOT);
        return (int) tokens.stream().filter(name::contains).count();
    }

    private static BigDecimal price(Product product) {
        return product.price() == null ? BigDecimal.ZERO : product.price();
    }

    private static String categoryName(Product product) {
        return product.category() == null ? "" : String.valueOf(product.category().name()).toLowerCase(Locale.ROOT);
    }

    private static String categoryDisplayName(Product product) {
        return product.category() == null || product.category().name() == null ? UNCATEGORISED : product.category().name();
    }

    private static String haystack(Product product) {
        return String.join(" ",
                        String.valueOf(product.name()),
                        String.valueOf(product.description()),
                        product.category() == null ? "" : String.valueOf(product.category().name()))
                .toLowerCase(Locale.ROOT);
    }
}
