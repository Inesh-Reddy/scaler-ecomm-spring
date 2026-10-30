package com.example.ecomm.services;

import com.example.ecomm.dtos.CategoryPatchDto;
import com.example.ecomm.dtos.ProductPatchDto;
import com.example.ecomm.models.Category;
import com.example.ecomm.models.Product;
import com.example.ecomm.models.State;
import com.example.ecomm.repository.CategoryRepository;
import com.example.ecomm.repository.ProductRepository;
import org.springframework.context.annotation.Primary;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.RequestParam;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

@Service
@Primary
public class StorageProductService implements IProductService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;

    public StorageProductService(ProductRepository productRepository, CategoryRepository categoryRepository) {
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
    }

    @Override
    public Product getProductById(Long id) {
        return productRepository.findByIdAndState(id, State.ACTIVE).orElse(null);
    }

    @Override
    public Page<Product> getAllProducts(@RequestParam(required = false) BigDecimal minPrice,
                                        @RequestParam(required = false) BigDecimal maxPrice, Pageable pageable) {
        return productRepository.findAllByPriceBetweenAndState(minPrice, maxPrice, State.ACTIVE, pageable);
    }

    @Override
    @Transactional
    public Product createProduct(Product input) {
        input.setId(null);
        input.setCategory(findOrCreateCategory(input.getCategory()));

        Date now = new Date();
        input.setCreatedAt(now);
        input.setLastUpdatedAt(now);
        return productRepository.save(input);
    }

    @Override
    @Transactional
    public Product replaceProduct(Product input, Long id) {
        Optional<Product> optionalProduct = productRepository.findByIdAndState(id, State.ACTIVE);
        if (optionalProduct.isEmpty()) {
            return null;
        }

        input.setId(id);
        input.setCategory(findOrCreateCategory(input.getCategory()));
        input.setCreatedAt(optionalProduct.get().getCreatedAt());
        input.setLastUpdatedAt(new Date());
        return productRepository.save(input);
    }

    @Override
    @Transactional
    public Product patchProduct(ProductPatchDto patch, Long id) {
        Optional<Product> optionalProduct = productRepository.findByIdAndState(id, State.ACTIVE);
        if (optionalProduct.isEmpty()) {
            return null;
        }

        Product product = optionalProduct.get();

        // Only fields that were actually sent are applied; a sent null clears the value.
        applyIfPresent(patch.getName(), product::setName);
        applyIfPresent(patch.getDescription(), product::setDescription);
        applyIfPresent(patch.getPrice(), product::setPrice);
        applyIfPresent(patch.getImageUrl(), product::setImageUrl);

        if (patch.getCategory() != null) {
            product.setCategory(patchCategory(patch.getCategory().orElse(null), product.getCategory()));
        }

        product.setLastUpdatedAt(new Date());
        return productRepository.save(product);
    }

    @Override
    @Transactional
    public boolean deleteProduct(Long id) {
        Optional<Product> optionalProduct = productRepository.findByIdAndState(id, State.ACTIVE);
        if (optionalProduct.isEmpty()) {
            return false;
        }

        Product product = optionalProduct.get();
        product.setState(State.INACTIVE);
        product.setLastUpdatedAt(new Date());
        productRepository.save(product);
        return true;
    }

    private <T> void applyIfPresent(Optional<T> value, Consumer<T> setter) {
        if (value != null) {
            setter.accept(value.orElse(null));
        }
    }

    /**
     * Resolves the category a patch points at.
     * <p>
     * {@code null} means the client sent {@code "category": null} -> detach the category.
     * Otherwise the category is identified by name (the sent name, or the one already on the product),
     * found-or-created the same way a full replace does, and its description is only touched when sent.
     */
    private Category patchCategory(CategoryPatchDto patch, Category current) {
        if (patch == null) {
            return null;
        }

        String name = patch.getName() != null
                ? patch.getName().orElse(null)
                : (current == null ? null : current.getName());

        // A category has to be identified by name; without one there is nothing to update.
        if (name == null || name.isBlank()) {
            return current;
        }

        Optional<Category> existing = categoryRepository.findByName(name);
        Category category = existing.orElseGet(() -> {
            Date now = new Date();
            Category created = new Category();
            created.setName(name);
            created.setCreatedAt(now);
            created.setLastUpdatedAt(now);
            return categoryRepository.save(created);
        });

        if (patch.getDescription() != null) {
            category.setDescription(patch.getDescription().orElse(null));
            category.setLastUpdatedAt(new Date());
            category = categoryRepository.save(category);
        }

        return category;
    }

    private Category findOrCreateCategory(Category category) {
        if (category == null) {
            return null;
        }

        Optional<Category> optionalCategory = categoryRepository.findByName(category.getName());
        if (optionalCategory.isPresent()) {
            return optionalCategory.get();
        }

        Date now = new Date();
        category.setId(null);
        category.setCreatedAt(now);
        category.setLastUpdatedAt(now);
        return categoryRepository.save(category);
    }
}
