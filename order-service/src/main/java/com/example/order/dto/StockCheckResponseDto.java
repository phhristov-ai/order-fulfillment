package com.example.order.dto;

public record StockCheckResponseDto(
        boolean available,
        int availableQuantity
) {
}