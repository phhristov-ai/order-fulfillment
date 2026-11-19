package com.example.inventory.repository;

import com.example.inventory.exception.InsufficientStockException;
import com.example.inventory.model.Product;
import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class ProductRepository {

    private final Map<Long, Product> products = new ConcurrentHashMap<>();

    public ProductRepository() {
        products.put(1L, new Product(1L, "Laptop", 1200.00, 10));
        products.put(2L, new Product(2L, "Mouse", 25.00, 25));
        products.put(3L, new Product(3L, "Keyboard", 75.00, 5));
    }

    public Optional<Product> findById(long id) {
        return Optional.ofNullable(products.get(id));
    }

    public synchronized Optional<Product> reserve(
            long productId,
            int quantity) {

        Product product = products.get(productId);

        if (product == null) {
            return Optional.empty();
        }

        if (quantity <= 0) {
            throw new IllegalArgumentException(
                    "Quantity must be greater than zero"
            );
        }

        if (product.availableQuantity() < quantity) {
            throw new InsufficientStockException();
        }

        Product updatedProduct = new Product(
                product.id(),
                product.name(),
                product.price(),
                product.availableQuantity() - quantity
        );

        products.put(productId, updatedProduct);

        return Optional.of(updatedProduct);
    }
}