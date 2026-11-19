package com.example.inventory.model;

public record Product(
        long id,
        String name,
        double price,
        int availableQuantity
) {}