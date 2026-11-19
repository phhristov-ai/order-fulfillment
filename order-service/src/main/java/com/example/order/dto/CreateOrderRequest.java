package com.example.order.dto;

import jakarta.validation.constraints.Positive;

public record CreateOrderRequest(

        @Positive
        long productId,

        @Positive
        int quantity

) {
}