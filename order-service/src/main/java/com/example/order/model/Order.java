package com.example.order.model;

public record Order(
        long id,
        long productId,
        String productName,
        int quantity,
        double unitPrice,
        double totalPrice
) {}