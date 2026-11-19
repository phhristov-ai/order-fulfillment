
package com.example.inventory.grpc;

import com.example.inventory.exception.InsufficientStockException;
import com.example.inventory.model.Product;
import com.example.inventory.repository.ProductRepository;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.ClientCallStreamObserver;
import io.grpc.stub.ClientResponseObserver;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.grpc.test.autoconfigure.AutoConfigureTestGrpcTransport;
import org.springframework.grpc.client.ImportGrpcClients;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest
@AutoConfigureTestGrpcTransport
@ImportGrpcClients(types = {
        InventoryServiceGrpc.InventoryServiceBlockingStub.class,
        InventoryServiceGrpc.InventoryServiceStub.class
})
class InventoryGrpcIntegrationTest {

    @Autowired
    private InventoryServiceGrpc.InventoryServiceBlockingStub blockingStub;

    @Autowired
    private InventoryServiceGrpc.InventoryServiceStub asyncStub;

    @MockitoBean
    private ProductRepository productRepository;

    private Product createProduct() {
        return new Product(
                1L,
                "Laptop",
                1200.0,
                10
        );
    }


    @Test
    void getProduct_shouldReturnProduct() {

        when(productRepository.findById(1L))
                .thenReturn(Optional.of(createProduct()));

        ProductResponse response = blockingStub.getProduct(
                ProductRequest.newBuilder()
                        .setProductId(1L)
                        .build()
        );

        assertEquals(1L, response.getProductId());
        assertEquals("Laptop", response.getName());
        assertEquals(1200.0, response.getPrice());
        assertEquals(10, response.getAvailableQuantity());
    }

    @Test
    void getProduct_shouldReturnNotFound() {

        when(productRepository.findById(99L))
                .thenReturn(Optional.empty());

        StatusRuntimeException exception = assertThrows(
                StatusRuntimeException.class,
                () -> blockingStub.getProduct(
                        ProductRequest.newBuilder()
                                .setProductId(99L)
                                .build()
                )
        );

        assertEquals(
                Status.Code.NOT_FOUND,
                exception.getStatus().getCode()
        );
    }

    @Test
    void checkStock_shouldReturnAvailability() {

        when(productRepository.findById(1L))
                .thenReturn(Optional.of(createProduct()));

        StockCheckResponse response = blockingStub.checkStock(
                StockCheckRequest.newBuilder()
                        .setProductId(1L)
                        .setRequestedQuantity(5)
                        .build()
        );

        assertTrue(response.getAvailable());
        assertEquals(10, response.getAvailableQuantity());
    }

    @Test
    void reserveStock_shouldReserveAndReturnRemainingQuantity() {

        Product updatedProduct = new Product(
                1L,
                "Laptop",
                1200.0,
                7
        );

        when(productRepository.reserve(1L, 3))
                .thenReturn(Optional.of(updatedProduct));

        ReserveStockResponse response = blockingStub.reserveStock(
                ReserveStockRequest.newBuilder()
                        .setProductId(1L)
                        .setQuantity(3)
                        .build()
        );

        assertEquals(1L, response.getProductId());
        assertEquals("Laptop", response.getName());
        assertEquals(7, response.getRemainingQuantity());

        verify(productRepository).reserve(1L, 3);
    }


    @Test
    void watchStock_shouldReceiveInitialStockUpdate() throws Exception {

        when(productRepository.findById(1L))
                .thenReturn(Optional.of(createProduct()));

        CountDownLatch latch = new CountDownLatch(1);

        AtomicReference<StockUpdate> receivedUpdate =
                new AtomicReference<>();

        AtomicReference<ClientCallStreamObserver<ProductRequest>> call =
                new AtomicReference<>();

        ClientResponseObserver<ProductRequest, StockUpdate> observer =
                new ClientResponseObserver<>() {

                    @Override
                    public void beforeStart(
                            ClientCallStreamObserver<ProductRequest> requestStream) {
                        call.set(requestStream);
                    }

                    @Override
                    public void onNext(StockUpdate update) {
                        receivedUpdate.set(update);
                        latch.countDown();
                    }

                    @Override
                    public void onError(Throwable throwable) {
                        latch.countDown();
                    }

                    @Override
                    public void onCompleted() {
                        latch.countDown();
                    }
                };

        asyncStub.watchStock(
                ProductRequest.newBuilder()
                        .setProductId(1L)
                        .build(),
                observer
        );

        assertTrue(
                latch.await(5, TimeUnit.SECONDS),
                "Did not receive initial stock update"
        );

        StockUpdate update = receivedUpdate.get();

        assertNotNull(update);
        assertEquals(1L, update.getProductId());
        assertEquals(10, update.getAvailableQuantity());

        // Close the client stream.
        assertNotNull(call.get());
        call.get().cancel("Test completed", null);
    }


    @Test
    void watchStock_shouldReceiveUpdateAfterStockReservation() throws Exception {

        when(productRepository.findById(1L))
                .thenReturn(Optional.of(createProduct()));

        Product updatedProduct = new Product(
                1L,
                "Laptop",
                1200.0,
                7
        );

        when(productRepository.reserve(1L, 3))
                .thenReturn(Optional.of(updatedProduct));

        CountDownLatch initialUpdateLatch = new CountDownLatch(1);
        CountDownLatch reservationUpdateLatch = new CountDownLatch(1);

        java.util.List<StockUpdate> updates =
                new java.util.concurrent.CopyOnWriteArrayList<>();

        AtomicReference<ClientCallStreamObserver<ProductRequest>> call =
                new AtomicReference<>();
        AtomicReference<Throwable> receivedError = new AtomicReference<>();

        ClientResponseObserver<ProductRequest, StockUpdate> observer =
                new ClientResponseObserver<>() {

                    @Override
                    public void beforeStart(
                            ClientCallStreamObserver<ProductRequest> requestStream) {
                        call.set(requestStream);
                    }

                    @Override
                    public void onNext(StockUpdate update) {
                        updates.add(update);
                        if (updates.size() == 1) {
                            initialUpdateLatch.countDown();
                        } else if (updates.size() == 2) {
                            reservationUpdateLatch.countDown();
                        }
                    }

                    @Override
                    public void onError(Throwable throwable) {
                        receivedError.set(throwable);
                        initialUpdateLatch.countDown();
                        reservationUpdateLatch.countDown();
                    }

                    @Override
                    public void onCompleted() {
                        // watchStock is a long-lived stream.
                        // Completion is not expected during this test.
                    }
                };

        // Subscribe to stock updates.
        asyncStub.watchStock(
                ProductRequest.newBuilder()
                        .setProductId(1L)
                        .build(),
                observer
        );

        assertTrue(
                initialUpdateLatch.await(5, TimeUnit.SECONDS),
                "Initial stock update was not received"
        );

        blockingStub.reserveStock(
                ReserveStockRequest.newBuilder()
                        .setProductId(1L)
                        .setQuantity(3)
                        .build()
        );

        assertTrue(
                reservationUpdateLatch.await(5, TimeUnit.SECONDS),
                "Stock update after reservation was not received"
        );

        assertEquals(2, updates.size());

        assertEquals(10, updates.get(0).getAvailableQuantity());
        assertEquals(7, updates.get(1).getAvailableQuantity());

        assertEquals(1L, updates.get(0).getProductId());
        assertEquals(1L, updates.get(1).getProductId());
        assertNull(receivedError.get(), "Unexpected gRPC error");
        // Clean up subscription.
        assertNotNull(call.get());
        call.get().cancel("Test completed", null);
    }

    @Test
    void reserveStock_shouldReturnFailedPreconditionWhenStockIsInsufficient() {

        when(productRepository.reserve(1L, 20))
                .thenThrow(mock(InsufficientStockException.class));

        StatusRuntimeException exception = assertThrows(
                StatusRuntimeException.class,
                () -> blockingStub.reserveStock(
                        ReserveStockRequest.newBuilder()
                                .setProductId(1L)
                                .setQuantity(20)
                                .build()
                )
        );

        assertEquals(
                Status.Code.FAILED_PRECONDITION,
                exception.getStatus().getCode()
        );
    }

    @Test
    void reserveStock_shouldReturnInvalidArgumentForInvalidQuantity() {

        StatusRuntimeException exception = assertThrows(
                StatusRuntimeException.class,
                () -> blockingStub.reserveStock(
                        ReserveStockRequest.newBuilder()
                                .setProductId(1L)
                                .setQuantity(0)
                                .build()
                )
        );

        assertEquals(
                Status.Code.INVALID_ARGUMENT,
                exception.getStatus().getCode()
        );

        verifyNoInteractions(productRepository);
    }

    @Test
    void watchStock_shouldReturnNotFoundWhenProductDoesNotExist()
            throws Exception {

        when(productRepository.findById(99L))
                .thenReturn(Optional.empty());

        CountDownLatch errorLatch = new CountDownLatch(1);

        AtomicReference<Throwable> receivedError =
                new AtomicReference<>();

        ClientResponseObserver<ProductRequest, StockUpdate> observer =
                new ClientResponseObserver<>() {

                    @Override
                    public void beforeStart(
                            ClientCallStreamObserver<ProductRequest> requestStream) {
                    }

                    @Override
                    public void onNext(StockUpdate update) {
                    }

                    @Override
                    public void onError(Throwable throwable) {
                        receivedError.set(throwable);
                        errorLatch.countDown();
                    }

                    @Override
                    public void onCompleted() {
                    }
                };

        asyncStub.watchStock(
                ProductRequest.newBuilder()
                        .setProductId(99L)
                        .build(),
                observer
        );

        assertTrue(errorLatch.await(5, TimeUnit.SECONDS));

        assertInstanceOf(
                StatusRuntimeException.class,
                receivedError.get()
        );

        StatusRuntimeException exception =
                (StatusRuntimeException) receivedError.get();

        assertEquals(
                Status.Code.NOT_FOUND,
                exception.getStatus().getCode()
        );
    }

}
