package com.example.ecomm.mcp.catalog;

import java.math.BigDecimal;

/**
 * One catalog row as returned by {@code GET /products}.
 * <p>
 * {@code category.id} is always null in the service's responses — the mapper does not copy it —
 * so a category is identified by its name.
 */
public record Product(Long id, String name, String description, BigDecimal price, String imageUrl, Category category) {
}
