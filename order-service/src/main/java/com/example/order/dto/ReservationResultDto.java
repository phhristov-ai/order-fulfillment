package com.example.order.dto;

public record ReservationResultDto(
        long productId,
        int requestedQuantity,
        int remainingQuantity,
        boolean successful,
        String errorMessage
) {
}