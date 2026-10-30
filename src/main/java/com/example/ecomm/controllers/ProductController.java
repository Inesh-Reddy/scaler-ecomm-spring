package com.example.ecomm.controllers;


import com.example.ecomm.dtos.ProductDto;
import com.example.ecomm.dtos.ProductPatchDto;
import com.example.ecomm.dtos.ProductPitchDto;
import com.example.ecomm.mappers.ProductMapper;
import com.example.ecomm.models.Product;
import com.example.ecomm.services.IProductService;
import com.example.ecomm.services.PitchService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@RestController
public class ProductController {
    private final IProductService productService;
    private final IProductService supplierService;
    private final PitchService pitchService;
    private final ProductMapper productMapper;

    public ProductController(@Qualifier("storageProductService") IProductService productService,
                             @Qualifier("dummyJsonProductService") IProductService supplierService,
                             PitchService pitchService,
                             ProductMapper productMapper) {
        this.productService = productService;
        this.supplierService = supplierService;
        this.pitchService = pitchService;
        this.productMapper = productMapper;
    }

    @GetMapping("/products")
    public ResponseEntity<List<ProductDto>> getAllProducts(@RequestParam(required = false) BigDecimal minPrice,
                                                           @RequestParam(required = false) BigDecimal maxPrice,
                                                           Pageable pageable) {
        List<ProductDto> products = productService.getAllProducts(minPrice, maxPrice, pageable).stream()
                .map(productMapper::toDto)
                .toList();
        return new ResponseEntity<>(products, HttpStatus.OK);
    }

    @GetMapping("/products/{id}")
    public ResponseEntity<ProductDto> getProductById(@PathVariable("id") Long id) {
        Product product = productService.getProductById(id);
        if (product == null) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
        return new ResponseEntity<>(productMapper.toDto(product), HttpStatus.OK);
    }

    @PostMapping("/products")
    public ResponseEntity<ProductDto> createProduct(@RequestBody ProductDto productDto) {
        Product created = productService.createProduct(productMapper.toEntity(productDto));
        return new ResponseEntity<>(productMapper.toDto(created), HttpStatus.CREATED);
    }

    @PutMapping("/products/{id}")
    public ResponseEntity<ProductDto> replaceProduct(@PathVariable("id") Long id,
                                                     @RequestBody ProductDto productDto) {
        Product replaced = productService.replaceProduct(productMapper.toEntity(productDto), id);
        if (replaced == null) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
        return new ResponseEntity<>(productMapper.toDto(replaced), HttpStatus.OK);
    }

    @PatchMapping("/products/{id}")
    public ResponseEntity<ProductDto> patchProduct(@PathVariable("id") Long id,
                                                   @RequestBody ProductPatchDto productPatchDto) {
        if (productPatchDto == null) {
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }

        Product patched = productService.patchProduct(productPatchDto, id);
        if (patched == null) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
        return new ResponseEntity<>(productMapper.toDto(patched), HttpStatus.OK);
    }

    @DeleteMapping("/products/{id}")
    public ResponseEntity<Void> deleteProduct(@PathVariable("id") Long id) {
        if (!productService.deleteProduct(id)) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }

    @PostMapping("/products/import")
    public String importProducts(@RequestParam(required = false) BigDecimal minPrice,
                                 @RequestParam(required = false) BigDecimal maxPrice,Pageable pageable) {
        System.out.println("Importing from 3p");
        int count = 0;
        for (Product product : supplierService.getAllProducts(minPrice, maxPrice, pageable)) {
            productService.createProduct(product);
            count++;
        }
        return "Imported " + count + " products";
    }

    @GetMapping("/products/{id}/pitch")
    public ResponseEntity<ProductPitchDto> getProductPitch(@PathVariable("id") Long id) {
        if (id < 1) {
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }

        Product product = productService.getProductById(id);
        if (product == null) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }

        ProductPitchDto pitchDto = new ProductPitchDto();
        pitchDto.setProductId(product.getId());
        pitchDto.setNotification(pitchService.generatePitch(product));
        return ResponseEntity.ok(pitchDto);
    }
}
