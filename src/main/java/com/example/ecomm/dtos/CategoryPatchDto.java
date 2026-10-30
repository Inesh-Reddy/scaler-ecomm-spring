package com.example.ecomm.dtos;

import lombok.Getter;
import lombok.Setter;

import java.util.Optional;

/**
 * Nested patch for a product's category, following the same three states as {@link ProductPatchDto}.
 *
 * A category is always identified by name, so an explicit null name is ignored rather than applied.
 */
@Getter
@Setter
public class CategoryPatchDto {
    private Optional<String> name;
    private Optional<String> description;
}
