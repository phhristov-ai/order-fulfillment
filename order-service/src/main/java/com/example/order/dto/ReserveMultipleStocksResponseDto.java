package com.example.order.dto;

import java.util.List;

public record ReserveMultipleStocksResponseDto(
        int successfulReservations,
        int failedReservations,
        List<ReservationResultDto> results
) {
}