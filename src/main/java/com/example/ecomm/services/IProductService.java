package com.example.ecomm.services;

import com.example.ecomm.dtos.ProductPatchDto;
import com.example.ecomm.models.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.List;

public interface IProductService {
    Product getProductById(Long id);
    Page<Product> getAllProducts(BigDecimal minPrice, BigDecimal maxPrice, Pageable pageable);
    Product createProduct(Product product);
    Product replaceProduct(Product product, Long id);
    Product patchProduct(ProductPatchDto patch, Long id);
    boolean deleteProduct(Long id);
}
