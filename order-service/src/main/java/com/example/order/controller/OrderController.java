package com.example.order.controller;

import com.example.inventory.grpc.StockCheckResponse;
import com.example.order.client.InventoryGrpcClient;
import com.example.inventory.grpc.ProductResponse;
import com.example.order.dto.*;
import com.example.order.model.Order;
import com.example.order.service.OrderService;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.util.JsonFormat;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/orders")
@Validated
public class OrderController {

    private final InventoryGrpcClient inventoryGrpcClient;
    private final OrderService orderService;

    public OrderController(InventoryGrpcClient inventoryGrpcClient, OrderService orderService) {
        this.inventoryGrpcClient = inventoryGrpcClient;
        this.orderService = orderService;
    }

    @GetMapping("/product/{productId}")
    public ProductResponseDto getProduct(
            @PathVariable long productId) {

        ProductResponse response =
                inventoryGrpcClient.getProduct(productId);

        return new ProductResponseDto(
                response.getProductId(),
                response.getName(),
                response.getPrice(),
                response.getAvailableQuantity()
        );
    }

    @GetMapping("/stock/{productId}")
    public StockCheckResponseDto checkStock(
            @PathVariable long productId,
            @RequestParam int quantity) {

        StockCheckResponse response =
                inventoryGrpcClient.checkStock(productId, quantity);

        return new StockCheckResponseDto(
                response.getAvailable(),
                response.getAvailableQuantity()
        );
    }

    @PostMapping
    public Order createOrder(
            @Valid @RequestBody CreateOrderRequest request) {
        return orderService.createOrder(
                request.productId(),
                request.quantity()
        );
    }

    @PostMapping("/reserve-multiple")
    public CompletableFuture<ReserveMultipleStocksResponseDto>
    reserveMultipleProducts(
            @Valid @RequestBody ReserveMultipleStocksRequest request) {

        return orderService.reserveMultipleProducts(request.items());
    }

    @PostMapping("/{orderId}/process-items")
    public CompletableFuture<List<OrderItemResponseDto>>
    processOrderItems(
            @PathVariable @Positive long orderId,
            @RequestBody @NotEmpty List<@NotNull @Valid OrderItemRequestDto> items) {

        return orderService.processOrderItems(orderId, items);
    }
}