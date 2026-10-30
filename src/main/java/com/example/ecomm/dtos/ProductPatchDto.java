package com.example.ecomm.dtos;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * Payload for PATCH /products/{id}. Every field has three states:
 *
 * <pre>
 *   field not sent        -> null              -> leave the stored value untouched
 *   "field": value        -> Optional.of(value) -> apply the value
 *   "field": null         -> Optional.empty()   -> clear the stored value
 * </pre>
 *
 * That distinction only exists because the fields are Optional: a plain nullable field
 * cannot tell "absent" apart from "explicitly null".
 */
@Getter
@Setter
public class ProductPatchDto {
    private Optional<String> name;
    private Optional<String> description;
    private Optional<BigDecimal> price;
    private Optional<String> imageUrl;
    private Optional<CategoryPatchDto> category;
}
