package com.example.order.repository;

import com.example.order.model.Order;
import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Repository
public class OrderRepository {

    private final Map<Long, Order> orders = new ConcurrentHashMap<>();
    private final AtomicLong idGenerator = new AtomicLong(1);

    public Order save(Order order) {
        long id = idGenerator.getAndIncrement();

        Order savedOrder = new Order(
                id,
                order.productId(),
                order.productName(),
                order.quantity(),
                order.unitPrice(),
                order.totalPrice()
        );

        orders.put(id, savedOrder);

        return savedOrder;
    }

    public List<Order> findAll() {
        return List.copyOf(orders.values());
    }
}