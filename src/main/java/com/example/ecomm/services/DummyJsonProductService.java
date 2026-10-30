package com.example.ecomm.services;

import com.example.ecomm.dtos.DummyJsonProductDto;
import com.example.ecomm.dtos.DummyJsonProductListDto;
import com.example.ecomm.dtos.ProductPatchDto;
import com.example.ecomm.models.Category;
import com.example.ecomm.models.Product;
import org.springframework.boot.restclient.RestTemplateBuilder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Service
public class DummyJsonProductService implements IProductService{

    private final RestTemplate restTemplate;
    // Could not autowire. No beans of 'RestTemplate' type found.
    DummyJsonProductService(RestTemplateBuilder restTemplate) {
        this.restTemplate = restTemplate.build();
    }


    @Override
    public Product getProductById(Long id) {
        /// https://dummyjson.com/products/1
        try {
            ResponseEntity<DummyJsonProductDto> response =
                    restTemplate.getForEntity("https://dummyjson.com/products/{id}",
                            DummyJsonProductDto.class, id);

            System.out.println("Service called:" + response.getStatusCode());

            return from(response.getBody());
        } catch (HttpClientErrorException.NotFound e) {
            return null;
        }

    }

    @Override
    public Page<Product> getAllProducts(@RequestParam(required = false) BigDecimal minPrice,
                                        @RequestParam(required = false) BigDecimal maxPrice, Pageable pageable) {
        /// https://dummyjson.com/products?limit=0  ->  { "products": [ ... ] }
        System.out.println("Getting all products");
        ResponseEntity<DummyJsonProductListDto> response = restTemplate.getForEntity(
                "https://dummyjson.com/products?limit=0", DummyJsonProductListDto.class);

        List<Product> products = new ArrayList<>();
        for (DummyJsonProductDto dto : response.getBody().getProducts()) {
            products.add(from(dto));
        }
//        System.out.println("Getting all products");
        return (Page<Product>) products;
    }

    @Override
    public Product createProduct(Product product) {
        throw new IllegalStateException("Creation denied");
    }

    @Override
    public Product replaceProduct(Product product, Long id) {
        throw new IllegalStateException("Replacement denied");
    }

    @Override
    public Product patchProduct(ProductPatchDto patch, Long id) {
        throw new IllegalStateException("Patching denied");
    }

    @Override
    public boolean deleteProduct(Long id) {
        throw new IllegalStateException("Deletion denied");
    }


    private Product from(DummyJsonProductDto dummyJsonProductDto) {
        Product.ProductBuilder product = Product.builder()
                .description(dummyJsonProductDto.getDescription())
                .imageUrl(dummyJsonProductDto.getThumbnail())
                .price(dummyJsonProductDto.getPrice())
                .name(dummyJsonProductDto.getTitle());

        Category category = new Category();
        category.setName(dummyJsonProductDto.getCategory());

        product.category(category);
        return product.build();

    }
}
