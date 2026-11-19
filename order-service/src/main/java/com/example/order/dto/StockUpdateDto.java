package com.example.order.dto;

public record StockUpdateDto(
        long productId,
        int availableQuantity
) {
}