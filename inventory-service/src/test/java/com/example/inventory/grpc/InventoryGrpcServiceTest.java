
package com.example.inventory.grpc;

import com.example.inventory.exception.InsufficientStockException;
import com.example.inventory.model.Product;
import com.example.inventory.repository.ProductRepository;
import com.example.inventory.service.StockSubscriptionManager;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.ServerCallStreamObserver;
import io.grpc.stub.StreamObserver;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InventoryGrpcServiceTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private StockSubscriptionManager subscriptionManager;

    @Mock
    private StreamObserver<ProductResponse> productObserver;

    @Mock
    private StreamObserver<StockCheckResponse> stockObserver;

    @Mock
    private StreamObserver<ReserveStockResponse> reserveObserver;

    @Mock
    private ServerCallStreamObserver<StockUpdate> stockUpdateObserver;

    private InventoryGrpcService service;

    private Product product;

    @BeforeEach
    void setUp() {
        service = new InventoryGrpcService(
                productRepository,
                subscriptionManager
        );

        product = new Product(
                1L,
                "Laptop",
                1200.0,
                10
        );
    }

    // ==========================================
    // GET PRODUCT
    // ==========================================

    @Test
    void getProduct_shouldReturnProduct() {

        when(productRepository.findById(1L))
                .thenReturn(Optional.of(product));

        ProductRequest request = ProductRequest.newBuilder()
                .setProductId(1L)
                .build();

        service.getProduct(request, productObserver);

        verify(productObserver).onNext(
                ProductResponse.newBuilder()
                        .setProductId(1L)
                        .setName("Laptop")
                        .setPrice(1200.0)
                        .setAvailableQuantity(10)
                        .build()
        );

        verify(productObserver).onCompleted();
        verify(productObserver, never()).onError(any());
    }

    @Test
    void getProduct_shouldReturnNotFoundWhenProductDoesNotExist() {

        when(productRepository.findById(1L))
                .thenReturn(Optional.empty());

        ProductRequest request = ProductRequest.newBuilder()
                .setProductId(1L)
                .build();

        StatusRuntimeException exception = assertThrows(
                StatusRuntimeException.class,
                () -> service.getProduct(request, productObserver)
        );

        assertEquals(
                Status.Code.NOT_FOUND,
                exception.getStatus().getCode()
        );

        verifyNoInteractions(productObserver);
    }

    // ==========================================
    // CHECK STOCK
    // ==========================================

    @Test
    void checkStock_shouldReturnAvailableWhenEnoughStockExists() {

        when(productRepository.findById(1L))
                .thenReturn(Optional.of(product));

        StockCheckRequest request = StockCheckRequest.newBuilder()
                .setProductId(1L)
                .setRequestedQuantity(5)
                .build();

        service.checkStock(request, stockObserver);

        verify(stockObserver).onNext(
                StockCheckResponse.newBuilder()
                        .setAvailable(true)
                        .setAvailableQuantity(10)
                        .build()
        );

        verify(stockObserver).onCompleted();
    }

    @Test
    void checkStock_shouldReturnUnavailableWhenStockIsInsufficient() {

        when(productRepository.findById(1L))
                .thenReturn(Optional.of(product));

        StockCheckRequest request = StockCheckRequest.newBuilder()
                .setProductId(1L)
                .setRequestedQuantity(15)
                .build();

        service.checkStock(request, stockObserver);

        verify(stockObserver).onNext(
                StockCheckResponse.newBuilder()
                        .setAvailable(false)
                        .setAvailableQuantity(10)
                        .build()
        );

        verify(stockObserver).onCompleted();
    }

    @Test
    void checkStock_shouldReturnNotFoundWhenProductDoesNotExist() {

        when(productRepository.findById(1L))
                .thenReturn(Optional.empty());

        StockCheckRequest request = StockCheckRequest.newBuilder()
                .setProductId(1L)
                .setRequestedQuantity(5)
                .build();

        StatusRuntimeException exception = assertThrows(
                StatusRuntimeException.class,
                () -> service.checkStock(request, stockObserver)
        );

        assertEquals(
                Status.Code.NOT_FOUND,
                exception.getStatus().getCode()
        );

        verifyNoInteractions(stockObserver);
    }

    // ==========================================
    // RESERVE STOCK
    // ==========================================

    @Test
    void reserveStock_shouldReturnInvalidArgumentForZeroQuantity() {

        ReserveStockRequest request = ReserveStockRequest.newBuilder()
                .setProductId(1L)
                .setQuantity(0)
                .build();

        service.reserveStock(request, reserveObserver);

        verify(reserveObserver).onError(argThat(error ->
                ((StatusRuntimeException) error)
                        .getStatus().getCode()
                        == Status.Code.INVALID_ARGUMENT
        ));

        verifyNoInteractions(productRepository);
        verifyNoInteractions(subscriptionManager);
    }

    @Test
    void reserveStock_shouldReturnInvalidArgumentForNegativeQuantity() {

        ReserveStockRequest request = ReserveStockRequest.newBuilder()
                .setProductId(1L)
                .setQuantity(-5)
                .build();

        service.reserveStock(request, reserveObserver);

        verify(reserveObserver).onError(argThat(error ->
                ((StatusRuntimeException) error)
                        .getStatus().getCode()
                        == Status.Code.INVALID_ARGUMENT
        ));

        verifyNoInteractions(productRepository);
    }

    @Test
    void reserveStock_shouldReserveStockSuccessfully() {

        Product updatedProduct = new Product(
                1L,
                "Laptop",
                1200.0,
                7
        );

        when(productRepository.reserve(1L, 3))
                .thenReturn(Optional.of(updatedProduct));

        ReserveStockRequest request = ReserveStockRequest.newBuilder()
                .setProductId(1L)
                .setQuantity(3)
                .build();

        service.reserveStock(request, reserveObserver);

        verify(reserveObserver).onNext(
                ReserveStockResponse.newBuilder()
                        .setProductId(1L)
                        .setName("Laptop")
                        .setPrice(1200.0)
                        .setRemainingQuantity(7)
                        .build()
        );

        verify(reserveObserver).onCompleted();

        verify(subscriptionManager).publish(1L, 7);
    }

    @Test
    void reserveStock_shouldReturnNotFoundWhenProductDoesNotExist() {

        when(productRepository.reserve(1L, 3))
                .thenReturn(Optional.empty());

        ReserveStockRequest request = ReserveStockRequest.newBuilder()
                .setProductId(1L)
                .setQuantity(3)
                .build();

        assertThrows(
                StatusRuntimeException.class,
                () -> service.reserveStock(request, reserveObserver)
        );

        verifyNoInteractions(subscriptionManager);
    }

    @Test
    void reserveStock_shouldReturnFailedPreconditionWhenStockIsInsufficient() {

        when(productRepository.reserve(1L, 20))
                .thenThrow(mock(InsufficientStockException.class));

        ReserveStockRequest request = ReserveStockRequest.newBuilder()
                .setProductId(1L)
                .setQuantity(20)
                .build();

        service.reserveStock(request, reserveObserver);

        verify(reserveObserver).onError(argThat(error ->
                ((StatusRuntimeException) error)
                        .getStatus().getCode()
                        == Status.Code.FAILED_PRECONDITION
        ));

        verifyNoInteractions(subscriptionManager);
    }

    // ==========================================
    // RESERVE MULTIPLE STOCKS
    // ==========================================

    @Test
    void reserveMultipleStocks_shouldProcessMultipleReservations() {

        Product updatedProduct = new Product(
                1L,
                "Laptop",
                1200.0,
                7
        );

        when(productRepository.reserve(1L, 3))
                .thenReturn(Optional.of(updatedProduct));

        when(productRepository.reserve(1L, 20))
                .thenThrow(mock(InsufficientStockException.class));

        StreamObserver<ReserveMultipleStocksResponse> responseObserver =
                mock(StreamObserver.class);

        StreamObserver<ReserveStockRequest> requestObserver =
                service.reserveMultipleStocks(responseObserver);

        requestObserver.onNext(
                ReserveStockRequest.newBuilder()
                        .setProductId(1L)
                        .setQuantity(3)
                        .build()
        );

        requestObserver.onNext(
                ReserveStockRequest.newBuilder()
                        .setProductId(1L)
                        .setQuantity(20)
                        .build()
        );

        requestObserver.onCompleted();

        verify(responseObserver).onNext(argThat(response ->
                response.getSuccessfulReservations() == 1
                        && response.getFailedReservations() == 1
                        && response.getResultsCount() == 2
                        && response.getResults(0).getSuccessful()
                        && !response.getResults(1).getSuccessful()
        ));

        verify(responseObserver).onCompleted();

        verify(subscriptionManager).publish(1L, 7);
    }

    @Test
    void reserveMultipleStocks_shouldHandleProductNotFound() {

        when(productRepository.reserve(99L, 2))
                .thenReturn(Optional.empty());

        StreamObserver<ReserveMultipleStocksResponse> responseObserver =
                mock(StreamObserver.class);

        StreamObserver<ReserveStockRequest> requestObserver =
                service.reserveMultipleStocks(responseObserver);

        requestObserver.onNext(
                ReserveStockRequest.newBuilder()
                        .setProductId(99L)
                        .setQuantity(2)
                        .build()
        );

        requestObserver.onCompleted();

        verify(responseObserver).onNext(argThat(response ->
                response.getSuccessfulReservations() == 0
                        && response.getFailedReservations() == 1
                        && response.getResults(0).getErrorMessage()
                        .equals("Product not found")
        ));

        verify(responseObserver).onCompleted();
    }

    // ==========================================
    // PROCESS ORDER STREAM
    // ==========================================

    @Test
    void processOrderStream_shouldProcessOrderSuccessfully() {

        Product updatedProduct = new Product(
                1L,
                "Laptop",
                1200.0,
                7
        );

        when(productRepository.reserve(1L, 3))
                .thenReturn(Optional.of(updatedProduct));

        StreamObserver<OrderItemResponse> responseObserver =
                mock(StreamObserver.class);

        StreamObserver<OrderItemRequest> requestObserver =
                service.processOrderStream(responseObserver);

        requestObserver.onNext(
                OrderItemRequest.newBuilder()
                        .setOrderId(100L)
                        .setProductId(1L)
                        .setQuantity(3)
                        .build()
        );

        verify(responseObserver).onNext(argThat(response ->
                response.getOrderId() == 100L
                        && response.getProductId() == 1L
                        && response.getSuccessful()
                        && response.getRemainingQuantity() == 7
                        && response.getMessage()
                        .equals("Reservation successful")
        ));

        verify(subscriptionManager).publish(1L, 7);
    }

    @Test
    void processOrderStream_shouldRejectInvalidQuantity() {

        StreamObserver<OrderItemResponse> responseObserver =
                mock(StreamObserver.class);

        StreamObserver<OrderItemRequest> requestObserver =
                service.processOrderStream(responseObserver);

        requestObserver.onNext(
                OrderItemRequest.newBuilder()
                        .setOrderId(100L)
                        .setProductId(1L)
                        .setQuantity(0)
                        .build()
        );

        verify(responseObserver).onNext(argThat(response ->
                !response.getSuccessful()
                        && response.getMessage()
                        .equals("Quantity must be greater than zero")
        ));

        verifyNoInteractions(productRepository);
    }

    @Test
    void processOrderStream_shouldHandleInsufficientStock() {

        when(productRepository.reserve(1L, 20))
                .thenThrow(mock(InsufficientStockException.class));

        StreamObserver<OrderItemResponse> responseObserver =
                mock(StreamObserver.class);

        StreamObserver<OrderItemRequest> requestObserver =
                service.processOrderStream(responseObserver);

        requestObserver.onNext(
                OrderItemRequest.newBuilder()
                        .setOrderId(100L)
                        .setProductId(1L)
                        .setQuantity(20)
                        .build()
        );

        verify(responseObserver).onNext(argThat(response ->
                !response.getSuccessful()
                        && response.getMessage().equals("Insufficient stock")
        ));
    }

    @Test
    void processOrderStream_shouldHandleProductNotFound() {

        when(productRepository.reserve(99L, 2))
                .thenReturn(Optional.empty());

        StreamObserver<OrderItemResponse> responseObserver =
                mock(StreamObserver.class);

        StreamObserver<OrderItemRequest> requestObserver =
                service.processOrderStream(responseObserver);

        requestObserver.onNext(
                OrderItemRequest.newBuilder()
                        .setOrderId(100L)
                        .setProductId(99L)
                        .setQuantity(2)
                        .build()
        );

        verify(responseObserver).onNext(argThat(response ->
                !response.getSuccessful()
                        && response.getMessage().equals("Product not found")
        ));
    }

    @Test
    void processOrderStream_shouldCompleteResponseWhenRequestStreamCompletes() {

        StreamObserver<OrderItemResponse> responseObserver =
                mock(StreamObserver.class);

        StreamObserver<OrderItemRequest> requestObserver =
                service.processOrderStream(responseObserver);

        requestObserver.onCompleted();

        verify(responseObserver).onCompleted();
    }


// ==========================================
// WATCH STOCK
// ==========================================

    @Test
    void watchStock_shouldSubscribeAndSendInitialStockUpdate() {

        when(productRepository.findById(1L))
                .thenReturn(Optional.of(product));

        StockSubscriptionManager.Subscription subscription =
                mock(StockSubscriptionManager.Subscription.class);

        when(subscriptionManager.subscribe(1L, stockUpdateObserver))
                .thenReturn(subscription);

        when(stockUpdateObserver.isCancelled())
                .thenReturn(false);

        ProductRequest request = ProductRequest.newBuilder()
                .setProductId(1L)
                .build();

        service.watchStock(request, stockUpdateObserver);

        verify(subscriptionManager).subscribe(1L, stockUpdateObserver);

        verify(stockUpdateObserver).setOnCancelHandler(any(Runnable.class));

        verify(subscription).send(
                StockUpdate.newBuilder()
                        .setProductId(1L)
                        .setAvailableQuantity(10)
                        .build()
        );

        verify(subscriptionManager, never())
                .unsubscribe(anyLong(), any());
    }

    @Test
    void watchStock_shouldReturnNotFoundWhenProductDoesNotExist() {

        when(productRepository.findById(99L))
                .thenReturn(Optional.empty());

        ProductRequest request = ProductRequest.newBuilder()
                .setProductId(99L)
                .build();

        service.watchStock(request, stockUpdateObserver);

        verify(stockUpdateObserver).onError(argThat(error ->
                ((StatusRuntimeException) error)
                        .getStatus().getCode() == Status.Code.NOT_FOUND
        ));

        verifyNoInteractions(subscriptionManager);
    }

    @Test
    void watchStock_shouldUnsubscribeWhenClientDisconnects() {

        when(productRepository.findById(1L))
                .thenReturn(Optional.of(product));

        StockSubscriptionManager.Subscription subscription =
                mock(StockSubscriptionManager.Subscription.class);

        when(subscriptionManager.subscribe(1L, stockUpdateObserver))
                .thenReturn(subscription);

        when(stockUpdateObserver.isCancelled())
                .thenReturn(false);

        ProductRequest request = ProductRequest.newBuilder()
                .setProductId(1L)
                .build();

        service.watchStock(request, stockUpdateObserver);

        ArgumentCaptor<Runnable> cancelHandlerCaptor =
                ArgumentCaptor.forClass(Runnable.class);

        verify(stockUpdateObserver)
                .setOnCancelHandler(cancelHandlerCaptor.capture());

        // Simulate client disconnection.
        cancelHandlerCaptor.getValue().run();

        verify(subscriptionManager)
                .unsubscribe(1L, subscription);
    }

    @Test
    void watchStock_shouldUnsubscribeImmediatelyWhenObserverIsAlreadyCancelled() {

        when(productRepository.findById(1L))
                .thenReturn(Optional.of(product));

        StockSubscriptionManager.Subscription subscription =
                mock(StockSubscriptionManager.Subscription.class);

        when(subscriptionManager.subscribe(1L, stockUpdateObserver))
                .thenReturn(subscription);

        when(stockUpdateObserver.isCancelled())
                .thenReturn(true);

        ProductRequest request = ProductRequest.newBuilder()
                .setProductId(1L)
                .build();

        service.watchStock(request, stockUpdateObserver);

        verify(subscriptionManager)
                .unsubscribe(1L, subscription);

        verify(subscription, never()).send(any());

        verifyNoInteractions(subscription);
    }

    @Test
    void watchStock_shouldUnsubscribeWhenSendingInitialUpdateFails() {

        when(productRepository.findById(1L))
                .thenReturn(Optional.of(product));

        StockSubscriptionManager.Subscription subscription =
                mock(StockSubscriptionManager.Subscription.class);

        when(subscriptionManager.subscribe(1L, stockUpdateObserver))
                .thenReturn(subscription);

        when(stockUpdateObserver.isCancelled())
                .thenReturn(false);

        doThrow(new RuntimeException("Client disconnected"))
                .when(subscription)
                .send(any(StockUpdate.class));

        ProductRequest request = ProductRequest.newBuilder()
                .setProductId(1L)
                .build();

        service.watchStock(request, stockUpdateObserver);

        verify(subscriptionManager)
                .unsubscribe(1L, subscription);
    }

}
