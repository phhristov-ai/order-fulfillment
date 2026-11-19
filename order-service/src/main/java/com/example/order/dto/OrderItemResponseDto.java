package com.example.order.dto;

public record OrderItemResponseDto(
        long orderId,
        long productId,
        boolean successful,
        int remainingQuantity,
        String message
) {
}