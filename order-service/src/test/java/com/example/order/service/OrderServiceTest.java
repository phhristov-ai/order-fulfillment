
package com.example.order.service;

import com.example.inventory.grpc.*;
import com.example.order.client.InventoryGrpcClient;
import com.example.order.dto.*;
import com.example.order.model.Order;
import com.example.order.repository.OrderRepository;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private InventoryGrpcClient inventoryGrpcClient;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    private OrderService orderService;

    @BeforeEach
    void setUp() {
        orderService = new OrderService(
                inventoryGrpcClient,
                orderRepository,
                messagingTemplate
        );
    }

    // --------------------------------------------------
    // CREATE ORDER
    // --------------------------------------------------

    @Test
    void createOrderShouldReserveStockAndSaveOrder() {

        ReserveStockResponse reservation =
                ReserveStockResponse.newBuilder()
                        .setProductId(1)
                        .setName("Laptop")
                        .setPrice(1200)
                        .setRemainingQuantity(8)
                        .build();

        when(inventoryGrpcClient.reserveStock(1, 2))
                .thenReturn(reservation);

        when(orderRepository.save(any(Order.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Order result = orderService.createOrder(1, 2);

        assertNotNull(result);

        verify(inventoryGrpcClient).reserveStock(1, 2);
        verify(orderRepository).save(any(Order.class));
    }

    @Test
    void createOrderShouldNotSaveOrderWhenReservationFails() {

        when(inventoryGrpcClient.reserveStock(1, 2))
                .thenThrow(Status.NOT_FOUND.asRuntimeException());

        assertThrows(
                StatusRuntimeException.class,
                () -> orderService.createOrder(1, 2)
        );

        verify(inventoryGrpcClient).reserveStock(1, 2);
        verifyNoInteractions(orderRepository);
    }

    // --------------------------------------------------
    // RESERVE MULTIPLE PRODUCTS
    // --------------------------------------------------

    @Test
    void reserveMultipleProductsShouldMapRequestsAndResponses() {

        List<ReservationItemRequest> items = List.of(
                new ReservationItemRequest(1, 2),
                new ReservationItemRequest(2, 3)
        );

        ReserveMultipleStocksResponse grpcResponse =
                ReserveMultipleStocksResponse.newBuilder()
                        .setSuccessfulReservations(2)
                        .setFailedReservations(0)
                        .addResults(
                                ReservationResult.newBuilder()
                                        .setProductId(1)
                                        .setRequestedQuantity(2)
                                        .setRemainingQuantity(8)
                                        .setSuccessful(true)
                        )
                        .addResults(
                                ReservationResult.newBuilder()
                                        .setProductId(2)
                                        .setRequestedQuantity(3)
                                        .setRemainingQuantity(17)
                                        .setSuccessful(true)
                        )
                        .build();

        when(inventoryGrpcClient.reserveMultipleStocks(anyList()))
                .thenReturn(CompletableFuture.completedFuture(grpcResponse));

        ReserveMultipleStocksResponseDto result =
                orderService.reserveMultipleProducts(items)
                        .join();

        assertEquals(2, result.successfulReservations());
        assertEquals(0, result.failedReservations());
        assertEquals(2, result.results().size());

        assertEquals(1, result.results().get(0).productId());
        assertEquals(2, result.results().get(0).requestedQuantity());
        assertEquals(8, result.results().get(0).remainingQuantity());
        assertTrue(result.results().get(0).successful());

        assertEquals(2, result.results().get(1).productId());
        assertEquals(17, result.results().get(1).remainingQuantity());

        ArgumentCaptor<List<ReserveStockRequest>> captor =
                ArgumentCaptor.forClass(List.class);

        verify(inventoryGrpcClient)
                .reserveMultipleStocks(captor.capture());

        List<ReserveStockRequest> requests = captor.getValue();

        assertEquals(2, requests.size());

        assertEquals(1, requests.get(0).getProductId());
        assertEquals(2, requests.get(0).getQuantity());

        assertEquals(2, requests.get(1).getProductId());
        assertEquals(3, requests.get(1).getQuantity());
    }

    @Test
    void reserveMultipleProductsShouldPropagateGrpcError() {

        List<ReservationItemRequest> items = List.of(
                new ReservationItemRequest(1, 2)
        );

        CompletableFuture<ReserveMultipleStocksResponse> failedFuture =
                CompletableFuture.failedFuture(
                        Status.UNAVAILABLE.asRuntimeException()
                );

        when(inventoryGrpcClient.reserveMultipleStocks(anyList()))
                .thenReturn(failedFuture);

        CompletionException exception = assertThrows(
                CompletionException.class,
                () -> orderService.reserveMultipleProducts(items).join()
        );

        assertEquals(
                Status.Code.UNAVAILABLE,
                ((StatusRuntimeException) exception.getCause())
                        .getStatus()
                        .getCode()
        );

        verifyNoInteractions(messagingTemplate);
    }

    // --------------------------------------------------
    // PROCESS ORDER ITEMS
    // --------------------------------------------------

    @Test
    void processOrderItemsShouldSendAllRequestsAndCollectResponses()
            throws Exception {

        long orderId = 1001;

        List<OrderItemRequestDto> items = List.of(
                new OrderItemRequestDto(1, 2),
                new OrderItemRequestDto(2, 3)
        );

        @SuppressWarnings("unchecked")
        StreamObserver<OrderItemRequest> requestObserver =
                mock(StreamObserver.class);

        AtomicReference<StreamObserver<OrderItemResponse>>
                responseObserverRef = new AtomicReference<>();

        when(inventoryGrpcClient.processOrderStream(any()))
                .thenAnswer(invocation -> {
                    responseObserverRef.set(invocation.getArgument(0));
                    return requestObserver;
                });

        CompletableFuture<List<OrderItemResponseDto>> future =
                orderService.processOrderItems(orderId, items);

        ArgumentCaptor<OrderItemRequest> requestCaptor =
                ArgumentCaptor.forClass(OrderItemRequest.class);

        verify(requestObserver, times(2))
                .onNext(requestCaptor.capture());

        verify(requestObserver).onCompleted();

        List<OrderItemRequest> sentRequests =
                requestCaptor.getAllValues();

        assertEquals(2, sentRequests.size());

        assertEquals(orderId, sentRequests.get(0).getOrderId());
        assertEquals(1, sentRequests.get(0).getProductId());
        assertEquals(2, sentRequests.get(0).getQuantity());

        assertEquals(orderId, sentRequests.get(1).getOrderId());
        assertEquals(2, sentRequests.get(1).getProductId());
        assertEquals(3, sentRequests.get(1).getQuantity());

        // Simulate Inventory Service responses.

        StreamObserver<OrderItemResponse> responseObserver =
                responseObserverRef.get();

        responseObserver.onNext(
                OrderItemResponse.newBuilder()
                        .setOrderId(orderId)
                        .setProductId(1)
                        .setSuccessful(true)
                        .setRemainingQuantity(8)
                        .setMessage("Reservation successful")
                        .build()
        );

        responseObserver.onNext(
                OrderItemResponse.newBuilder()
                        .setOrderId(orderId)
                        .setProductId(2)
                        .setSuccessful(false)
                        .setRemainingQuantity(1)
                        .setMessage("Insufficient stock")
                        .build()
        );

        responseObserver.onCompleted();

        List<OrderItemResponseDto> results =
                future.get(1, TimeUnit.SECONDS);

        assertEquals(2, results.size());

        assertTrue(results.get(0).successful());
        assertEquals(8, results.get(0).remainingQuantity());

        assertFalse(results.get(1).successful());
        assertEquals("Insufficient stock", results.get(1).message());

        verify(messagingTemplate, times(2))
                .convertAndSend(
                        eq("/topic/orders/1001/items"),
                        any(OrderItemResponseDto.class)
                );
    }

    @Test
    void processOrderItemsShouldPublishEachResponseThroughWebSocket() {

        long orderId = 1001;

        @SuppressWarnings("unchecked")
        StreamObserver<OrderItemRequest> requestObserver =
                mock(StreamObserver.class);

        AtomicReference<StreamObserver<OrderItemResponse>>
                responseObserverRef = new AtomicReference<>();

        when(inventoryGrpcClient.processOrderStream(any()))
                .thenAnswer(invocation -> {
                    responseObserverRef.set(invocation.getArgument(0));
                    return requestObserver;
                });

        CompletableFuture<List<OrderItemResponseDto>> future =
                orderService.processOrderItems(
                        orderId,
                        List.of(new OrderItemRequestDto(1, 2))
                );

        OrderItemResponse response =
                OrderItemResponse.newBuilder()
                        .setOrderId(orderId)
                        .setProductId(1)
                        .setSuccessful(true)
                        .setRemainingQuantity(8)
                        .setMessage("Reservation successful")
                        .build();

        responseObserverRef.get().onNext(response);

        ArgumentCaptor<OrderItemResponseDto> dtoCaptor =
                ArgumentCaptor.forClass(OrderItemResponseDto.class);

        verify(messagingTemplate).convertAndSend(
                eq("/topic/orders/1001/items"),
                dtoCaptor.capture()
        );

        assertEquals(1, dtoCaptor.getValue().productId());
        assertTrue(dtoCaptor.getValue().successful());

        responseObserverRef.get().onCompleted();

        assertEquals(1, future.join().size());
    }

    @Test
    void processOrderItemsShouldPropagateGrpcError() {

        @SuppressWarnings("unchecked")
        StreamObserver<OrderItemRequest> requestObserver =
                mock(StreamObserver.class);

        AtomicReference<StreamObserver<OrderItemResponse>>
                responseObserverRef = new AtomicReference<>();

        when(inventoryGrpcClient.processOrderStream(any()))
                .thenAnswer(invocation -> {
                    responseObserverRef.set(invocation.getArgument(0));
                    return requestObserver;
                });

        CompletableFuture<List<OrderItemResponseDto>> future =
                orderService.processOrderItems(
                        1001,
                        List.of(new OrderItemRequestDto(1, 2))
                );

        StatusRuntimeException grpcError =
                Status.UNAVAILABLE.asRuntimeException();

        responseObserverRef.get().onError(grpcError);

        CompletionException exception = assertThrows(
                CompletionException.class,
                future::join
        );

        assertEquals(grpcError, exception.getCause());
    }

    @Test
    void processOrderItemsShouldHandleSynchronousGrpcFailure() {

        when(inventoryGrpcClient.processOrderStream(any()))
                .thenThrow(Status.UNAVAILABLE.asRuntimeException());

        CompletableFuture<List<OrderItemResponseDto>> future =
                orderService.processOrderItems(
                        1001,
                        List.of(new OrderItemRequestDto(1, 2))
                );

        CompletionException exception = assertThrows(
                CompletionException.class,
                future::join
        );

        assertEquals(
                Status.Code.UNAVAILABLE,
                ((StatusRuntimeException) exception.getCause())
                        .getStatus()
                        .getCode()
        );
    }
}
