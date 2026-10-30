package com.example.ecomm.mcp.catalog;

/** The category a product belongs to, as embedded in {@code GET /products} responses. */
public record Category(Long id, String name, String description) {
}
