package com.example.order.dto;

import jakarta.validation.constraints.Positive;

public record OrderItemRequestDto(

        @Positive(message = "Product ID must be greater than zero")
        long productId,

        @Positive(message = "Quantity must be greater than zero")
        int quantity

) {
}