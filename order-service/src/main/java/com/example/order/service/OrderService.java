package com.example.order.service;

import com.example.inventory.grpc.OrderItemRequest;
import com.example.inventory.grpc.OrderItemResponse;
import com.example.inventory.grpc.ReserveStockRequest;
import com.example.inventory.grpc.ReserveStockResponse;
import com.example.order.client.InventoryGrpcClient;
import com.example.order.dto.*;
import com.example.order.model.Order;
import com.example.order.repository.OrderRepository;
import io.grpc.stub.StreamObserver;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
public class OrderService {

    private final InventoryGrpcClient inventoryGrpcClient;
    private final OrderRepository orderRepository;
    private final SimpMessagingTemplate messagingTemplate;

    public OrderService(
            InventoryGrpcClient inventoryGrpcClient,
            OrderRepository orderRepository, SimpMessagingTemplate messagingTemplate) {

        this.inventoryGrpcClient = inventoryGrpcClient;
        this.orderRepository = orderRepository;
        this.messagingTemplate = messagingTemplate;
    }

    public Order createOrder(long productId, int quantity) {

        // Reserve stock through gRPC
        ReserveStockResponse response =
                inventoryGrpcClient.reserveStock(productId, quantity);

        // Create the order only if reservation succeeds
        Order order = new Order(
                0,
                response.getProductId(),
                response.getName(),
                quantity,
                response.getPrice(),
                response.getPrice() * quantity
        );

        return orderRepository.save(order);
    }

    public CompletableFuture<ReserveMultipleStocksResponseDto>
    reserveMultipleProducts(List<ReservationItemRequest> items) {

        List<ReserveStockRequest> requests = items.stream()
                .map(item -> ReserveStockRequest.newBuilder()
                        .setProductId(item.productId())
                        .setQuantity(item.quantity())
                        .build())
                .toList();

        return inventoryGrpcClient.reserveMultipleStocks(requests)
                .thenApply(response ->
                        new ReserveMultipleStocksResponseDto(
                                response.getSuccessfulReservations(),
                                response.getFailedReservations(),
                                response.getResultsList().stream()
                                        .map(result ->
                                                new ReservationResultDto(
                                                        result.getProductId(),
                                                        result.getRequestedQuantity(),
                                                        result.getRemainingQuantity(),
                                                        result.getSuccessful(),
                                                        result.getErrorMessage()
                                                )
                                        )
                                        .toList()
                        )
                );
    }

    public CompletableFuture<List<OrderItemResponseDto>>
    processOrderItems(
            long orderId,
            List<OrderItemRequestDto> items) {

        CompletableFuture<List<OrderItemResponseDto>> future =
                new CompletableFuture<>();

        StreamObserver<OrderItemResponse> responseObserver =
                getResponseObserver(orderId, future);

        try {
            StreamObserver<OrderItemRequest> requestObserver =
                    inventoryGrpcClient.processOrderStream(responseObserver);

            for (OrderItemRequestDto item : items) {

                requestObserver.onNext(
                        OrderItemRequest.newBuilder()
                                .setOrderId(orderId)
                                .setProductId(item.productId())
                                .setQuantity(item.quantity())
                                .build()
                );
            }

            requestObserver.onCompleted();

        } catch (RuntimeException exception) {
            future.completeExceptionally(exception);
        }

        return future;
    }

    private StreamObserver<OrderItemResponse> getResponseObserver(
            long orderId,
            CompletableFuture<List<OrderItemResponseDto>> future) {

        List<OrderItemResponseDto> responses =
                new CopyOnWriteArrayList<>();

        return new StreamObserver<>() {

            @Override
            public void onNext(OrderItemResponse response) {

                OrderItemResponseDto dto = new OrderItemResponseDto(
                        response.getOrderId(),
                        response.getProductId(),
                        response.getSuccessful(),
                        response.getRemainingQuantity(),
                        response.getMessage()
                );

                responses.add(dto);

                // Send each result immediately to React.
                messagingTemplate.convertAndSend(
                        "/topic/orders/" + orderId + "/items",
                        dto
                );
            }

            @Override
            public void onError(Throwable throwable) {
                future.completeExceptionally(throwable);
            }

            @Override
            public void onCompleted() {
                future.complete(List.copyOf(responses));
            }
        };
    }
}