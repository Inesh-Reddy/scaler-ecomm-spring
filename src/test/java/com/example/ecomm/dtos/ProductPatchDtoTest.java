package com.example.ecomm.dtos;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the absent / present / explicit-null behaviour that PATCH /products/{id} relies on.
 */
class ProductPatchDtoTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("a field that is not sent stays null, so it is left untouched")
    void deserialize_absentFields_areNull() {
        ProductPatchDto dto = objectMapper.readValue("{\"price\": 12.50}", ProductPatchDto.class);

        assertThat(dto.getName()).isNull();
        assertThat(dto.getDescription()).isNull();
        assertThat(dto.getImageUrl()).isNull();
        assertThat(dto.getCategory()).isNull();
        assertThat(dto.getPrice()).contains(new BigDecimal("12.50"));
    }

    @Test
    @DisplayName("a field sent as null becomes Optional.empty, so it is cleared")
    void deserialize_explicitNull_isEmptyOptional() {
        ProductPatchDto dto = objectMapper.readValue(
                "{\"name\": null, \"description\": null, \"category\": null}", ProductPatchDto.class);

        assertThat(dto.getName()).isEmpty();
        assertThat(dto.getDescription()).isEmpty();
        assertThat(dto.getCategory()).isEmpty();
        assertThat(dto.getPrice()).isNull();
    }

    @Test
    @DisplayName("a nested category is deserialized with the same three-state rules")
    void deserialize_nestedCategory() {
        ProductPatchDto dto = objectMapper.readValue(
                "{\"category\": {\"name\": \"beauty\"}}", ProductPatchDto.class);

        CategoryPatchDto category = dto.getCategory().orElseThrow();
        assertThat(category.getName()).contains("beauty");
        assertThat(category.getDescription()).isNull();
    }
}
