package com.example.ecomm.mcp.catalog;

import java.util.List;

/**
 * Every active product inside a price window.
 *
 * @param products  at most {@code maxScan} rows
 * @param truncated whether a cap stopped the scan, so the result may be incomplete
 */
public record ScanResult(List<Product> products, boolean truncated) {
}
