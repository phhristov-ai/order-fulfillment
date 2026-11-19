package com.example.order.dto;

public record ProductResponseDto(
        long productId,
        String name,
        double price,
        int availableQuantity
) {
}