package com.example.ecomm.mcp.catalog;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SearchTest {

    private static final Product MOUSE = product(
            2L, "Wireless Mouse", "2.4GHz wireless mouse", "25.00", "electronics");
    private static final Product KEYBOARD = product(
            3L, "Mechanical Keyboard", "Hot-swappable mechanical keyboard", "89.99", "electronics");
    private static final Product YOGA_MAT = product(
            4L, "Yoga Mat", "Non-slip yoga mat", "19.99", "fitness");
    private static final Product ORPHAN = new Product(
            5L, "Mystery Box", null, new BigDecimal("5.00"), null, null);

    private static final List<Product> CATALOG = List.of(MOUSE, KEYBOARD, YOGA_MAT, ORPHAN);

    @Test
    @DisplayName("tokenize lowercases and splits on whitespace")
    void tokenize() {
        assertEquals(List.of("wireless", "mouse"), Search.tokenize("  Wireless   MOUSE "));
        assertEquals(List.of(), Search.tokenize(null));
    }

    @Test
    @DisplayName("terms match name, description and category, case-insensitively")
    void matchesAcrossFields() {
        assertTrue(Search.matches(MOUSE, List.of("wireless"), null));
        assertTrue(Search.matches(MOUSE, List.of("2.4ghz"), null), "description is searched");
        assertTrue(Search.matches(MOUSE, List.of("electronics"), null), "category name is searched");
        assertFalse(Search.matches(MOUSE, List.of("keyboard"), null));
    }

    @Test
    @DisplayName("every term must match somewhere")
    void allTermsRequired() {
        assertTrue(Search.matches(MOUSE, List.of("wireless", "mouse"), null));
        assertFalse(Search.matches(MOUSE, List.of("wireless", "yoga"), null));
    }

    @Test
    @DisplayName("partial words match")
    void partialWords() {
        assertTrue(Search.matches(KEYBOARD, List.of("keyb"), null));
        assertTrue(Search.matches(MOUSE, List.of("wire"), null));
    }

    @Test
    @DisplayName("category filter is a case-insensitive substring on the category name")
    void categoryFilter() {
        assertTrue(Search.matches(MOUSE, List.of(), "electro"));
        assertTrue(Search.matches(MOUSE, List.of(), "ELECTRONICS"));
        assertFalse(Search.matches(YOGA_MAT, List.of(), "electro"));
        assertFalse(Search.matches(ORPHAN, List.of(), "anything"), "a null category never matches a filter");
    }

    @Test
    @DisplayName("sort price_asc, price_desc and name")
    void sorting() {
        assertEquals(List.of(5L, 4L, 2L, 3L), ids(Search.search(CATALOG, null, null, Search.Sort.PRICE_ASC, 10)));
        assertEquals(List.of(3L, 2L, 4L, 5L), ids(Search.search(CATALOG, null, null, Search.Sort.PRICE_DESC, 10)));
        assertEquals(
                List.of("Mechanical Keyboard", "Mystery Box", "Wireless Mouse", "Yoga Mat"),
                Search.search(CATALOG, null, null, Search.Sort.NAME, 10).products().stream()
                        .map(Search.Summary::name)
                        .toList());
    }

    @Test
    @DisplayName("relevance ranks name hits above description-only hits")
    void relevanceRanking() {
        Product descriptionOnly = product(
                6L, "Desk Pad", "Pairs with your mouse", "30.00", "electronics");
        Product phone = product(
                7L, "Mouse Pad", "Pairs with your mouse", "8.00", "electronics");

        // Both match the description and name terms; the one with the term in its name comes first.
        List<Long> ids = ids(Search.search(List.of(descriptionOnly, phone), "mouse", null, Search.Sort.RELEVANCE, 10));
        assertEquals(List.of(7L, 6L), ids);
    }

    @Test
    @DisplayName("limit caps the returned rows but not the reported total")
    void limitKeepsTotal() {
        Search.Result result = Search.search(CATALOG, null, "electronics", Search.Sort.RELEVANCE, 1);

        assertEquals(2, result.total());
        assertEquals(1, result.products().size());
        assertEquals(2L, result.products().get(0).id(), "cheapest electronics product first under relevance");
    }

    @Test
    @DisplayName("summaries carry the fields a caller needs and flatten the category")
    void summaryShape() {
        Search.Summary summary = Search.search(CATALOG, "wireless", null, Search.Sort.RELEVANCE, 10)
                .products()
                .get(0);

        assertEquals(2L, summary.id());
        assertEquals("Wireless Mouse", summary.name());
        assertEquals(new BigDecimal("25.00"), summary.price());
        assertEquals("electronics", summary.category());
        assertEquals("2.4GHz wireless mouse", summary.description());
        assertNull(Search.summarize(ORPHAN).category());
    }

    @Test
    @DisplayName("categories count products and span prices, including the uncategorised bucket")
    void categoryRollup() {
        assertEquals(
                List.of(
                        new Search.CategorySummary("(uncategorised)", 1, new BigDecimal("5.00"), new BigDecimal("5.00")),
                        new Search.CategorySummary("electronics", 2, new BigDecimal("25.00"), new BigDecimal("89.99")),
                        new Search.CategorySummary("fitness", 1, new BigDecimal("19.99"), new BigDecimal("19.99"))),
                Search.categories(CATALOG));
    }

    @Test
    @DisplayName("an unsupported sort fails loudly")
    void unsupportedSort() {
        assertThrows(IllegalArgumentException.class, () -> Search.Sort.from("cheapest"));
        assertEquals(Search.Sort.RELEVANCE, Search.Sort.from(null));
    }

    private static List<Long> ids(Search.Result result) {
        return result.products().stream().map(Search.Summary::id).toList();
    }

    private static Product product(Long id, String name, String description, String price, String category) {
        return new Product(id, name, description, new BigDecimal(price), name + ".png",
                new Category(null, category, category + " description"));
    }
}
