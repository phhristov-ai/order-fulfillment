
package com.example.order.client;

import com.example.inventory.grpc.*;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InventoryGrpcClientTest {

    @Mock
    private InventoryServiceGrpc.InventoryServiceBlockingStub stub;

    @Mock
    private InventoryServiceGrpc.InventoryServiceStub asyncStub;

    private InventoryGrpcClient client;

    @BeforeEach
    void setUp() {
        client = new InventoryGrpcClient(stub, asyncStub);
    }

    // --------------------------------------------------
    // GET PRODUCT
    // --------------------------------------------------

    @Test
    void getProductShouldReturnProduct() {

        ProductResponse expected = ProductResponse.newBuilder()
                .setProductId(1)
                .setName("Laptop")
                .setPrice(1200)
                .setAvailableQuantity(10)
                .build();

        when(stub.withDeadlineAfter(
                anyLong(), eq(TimeUnit.NANOSECONDS)
        )).thenReturn(stub);

        when(stub.getProduct(any(ProductRequest.class)))
                .thenReturn(expected);

        ProductResponse actual = client.getProduct(1);

        assertEquals(expected, actual);

        ArgumentCaptor<ProductRequest> captor =
                ArgumentCaptor.forClass(ProductRequest.class);

        verify(stub).getProduct(captor.capture());

        assertEquals(1, captor.getValue().getProductId());
    }

    @Test
    void getProductShouldRetryWhenServiceIsUnavailable() {

        ProductResponse expected = ProductResponse.newBuilder()
                .setProductId(1)
                .setName("Laptop")
                .build();

        when(stub.withDeadlineAfter(
                anyLong(), eq(TimeUnit.NANOSECONDS)
        )).thenReturn(stub);

        when(stub.getProduct(any(ProductRequest.class)))
                .thenThrow(Status.UNAVAILABLE.asRuntimeException())
                .thenReturn(expected);

        ProductResponse actual = client.getProduct(1);

        assertEquals(expected, actual);

        verify(stub, times(2)).getProduct(any(ProductRequest.class));
    }

    @Test
    void getProductShouldNotRetryWhenProductIsNotFound() {

        when(stub.withDeadlineAfter(
                anyLong(), eq(TimeUnit.NANOSECONDS)
        )).thenReturn(stub);

        when(stub.getProduct(any(ProductRequest.class)))
                .thenThrow(Status.NOT_FOUND.asRuntimeException());

        StatusRuntimeException exception = assertThrows(
                StatusRuntimeException.class,
                () -> client.getProduct(999)
        );

        assertEquals(
                Status.Code.NOT_FOUND,
                exception.getStatus().getCode()
        );

        verify(stub, times(1)).getProduct(any(ProductRequest.class));
    }

    // --------------------------------------------------
    // CHECK STOCK
    // --------------------------------------------------

    @Test
    void checkStockShouldReturnStockInformation() {

        StockCheckResponse expected = StockCheckResponse.newBuilder()
                .setAvailable(true)
                .setAvailableQuantity(10)
                .build();

        when(stub.withDeadlineAfter(
                anyLong(), eq(TimeUnit.SECONDS)
        )).thenReturn(stub);

        when(stub.checkStock(any(StockCheckRequest.class)))
                .thenReturn(expected);

        StockCheckResponse actual = client.checkStock(1, 5);

        assertEquals(expected, actual);

        ArgumentCaptor<StockCheckRequest> captor =
                ArgumentCaptor.forClass(StockCheckRequest.class);

        verify(stub).checkStock(captor.capture());

        assertEquals(1, captor.getValue().getProductId());
        assertEquals(5, captor.getValue().getRequestedQuantity());
    }

    // --------------------------------------------------
    // RESERVE STOCK
    // --------------------------------------------------

    @Test
    void reserveStockShouldReturnReservationInformation() {

        ReserveStockResponse expected = ReserveStockResponse.newBuilder()
                .setProductId(1)
                .setName("Laptop")
                .setPrice(1200)
                .setRemainingQuantity(8)
                .build();

        when(stub.withDeadlineAfter(
                anyLong(), eq(TimeUnit.SECONDS)
        )).thenReturn(stub);

        when(stub.reserveStock(any(ReserveStockRequest.class)))
                .thenReturn(expected);

        ReserveStockResponse actual = client.reserveStock(1, 2);

        assertEquals(expected, actual);

        ArgumentCaptor<ReserveStockRequest> captor =
                ArgumentCaptor.forClass(ReserveStockRequest.class);

        verify(stub).reserveStock(captor.capture());

        assertEquals(1, captor.getValue().getProductId());
        assertEquals(2, captor.getValue().getQuantity());
    }

    // --------------------------------------------------
    // WATCH STOCK
    // --------------------------------------------------

    @Test
    void watchStockShouldSubscribeToCorrectProduct() {

        @SuppressWarnings("unchecked")
        StreamObserver<StockUpdate> responseObserver =
                mock(StreamObserver.class);

        when(asyncStub.withDeadlineAfter(
                anyLong(), eq(TimeUnit.MINUTES)
        )).thenReturn(asyncStub);

        client.watchStock(1, responseObserver);

        verify(asyncStub).watchStock(
                argThat(request -> request.getProductId() == 1),
                same(responseObserver)
        );
    }

    // --------------------------------------------------
    // RESERVE MULTIPLE STOCKS
    // --------------------------------------------------

    @Test
    void reserveMultipleStocksShouldRejectEmptyRequests() {

        CompletableFuture<ReserveMultipleStocksResponse> future =
                client.reserveMultipleStocks(List.of());

        CompletionException exception = assertThrows(
                CompletionException.class,
                future::join
        );

        assertInstanceOf(
                IllegalArgumentException.class,
                exception.getCause()
        );

        verifyNoInteractions(asyncStub);
    }

    @Test
    void reserveMultipleStocksShouldSendRequestsAndReturnResponse()
            throws Exception {

        ReserveStockRequest request1 =
                ReserveStockRequest.newBuilder()
                        .setProductId(1)
                        .setQuantity(2)
                        .build();

        ReserveStockRequest request2 =
                ReserveStockRequest.newBuilder()
                        .setProductId(2)
                        .setQuantity(3)
                        .build();

        ReserveMultipleStocksResponse expected =
                ReserveMultipleStocksResponse.newBuilder()
                        .setSuccessfulReservations(2)
                        .setFailedReservations(0)
                        .build();

        @SuppressWarnings("unchecked")
        StreamObserver<ReserveStockRequest> requestObserver =
                mock(StreamObserver.class);

        AtomicReference<StreamObserver<ReserveMultipleStocksResponse>>
                responseObserverRef = new AtomicReference<>();

        when(asyncStub.withDeadlineAfter(
                anyLong(), eq(TimeUnit.SECONDS)
        )).thenReturn(asyncStub);

        when(asyncStub.reserveMultipleStocks(any()))
                .thenAnswer(invocation -> {
                    responseObserverRef.set(invocation.getArgument(0));
                    return requestObserver;
                });

        CompletableFuture<ReserveMultipleStocksResponse> future =
                client.reserveMultipleStocks(
                        List.of(request1, request2)
                );

        verify(requestObserver).onNext(request1);
        verify(requestObserver).onNext(request2);
        verify(requestObserver).onCompleted();

        // Simulate the server response.
        StreamObserver<ReserveMultipleStocksResponse> responseObserver =
                responseObserverRef.get();

        assertNotNull(responseObserver);

        responseObserver.onNext(expected);
        responseObserver.onCompleted();

        ReserveMultipleStocksResponse actual =
                future.get(1, TimeUnit.SECONDS);

        assertEquals(expected, actual);
    }

    @Test
    void reserveMultipleStocksShouldCompleteExceptionallyOnGrpcError() {

        @SuppressWarnings("unchecked")
        StreamObserver<ReserveStockRequest> requestObserver =
                mock(StreamObserver.class);

        AtomicReference<StreamObserver<ReserveMultipleStocksResponse>>
                responseObserverRef = new AtomicReference<>();

        when(asyncStub.withDeadlineAfter(
                anyLong(), eq(TimeUnit.SECONDS)
        )).thenReturn(asyncStub);

        when(asyncStub.reserveMultipleStocks(any()))
                .thenAnswer(invocation -> {
                    responseObserverRef.set(invocation.getArgument(0));
                    return requestObserver;
                });

        CompletableFuture<ReserveMultipleStocksResponse> future =
                client.reserveMultipleStocks(
                        List.of(
                                ReserveStockRequest.newBuilder()
                                        .setProductId(1)
                                        .setQuantity(2)
                                        .build()
                        )
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
    void reserveMultipleStocksShouldFailWhenServerReturnsNoResponse() {

        @SuppressWarnings("unchecked")
        StreamObserver<ReserveStockRequest> requestObserver =
                mock(StreamObserver.class);

        AtomicReference<StreamObserver<ReserveMultipleStocksResponse>>
                responseObserverRef = new AtomicReference<>();

        when(asyncStub.withDeadlineAfter(
                anyLong(), eq(TimeUnit.SECONDS)
        )).thenReturn(asyncStub);

        when(asyncStub.reserveMultipleStocks(any()))
                .thenAnswer(invocation -> {
                    responseObserverRef.set(invocation.getArgument(0));
                    return requestObserver;
                });

        CompletableFuture<ReserveMultipleStocksResponse> future =
                client.reserveMultipleStocks(
                        List.of(
                                ReserveStockRequest.newBuilder()
                                        .setProductId(1)
                                        .setQuantity(1)
                                        .build()
                        )
                );

        responseObserverRef.get().onCompleted();

        CompletionException exception = assertThrows(
                CompletionException.class,
                future::join
        );

        assertInstanceOf(
                IllegalStateException.class,
                exception.getCause()
        );
    }

    // --------------------------------------------------
    // PROCESS ORDER STREAM
    // --------------------------------------------------

    @Test
    void processOrderStreamShouldReturnRequestObserver() {

        @SuppressWarnings("unchecked")
        StreamObserver<OrderItemResponse> responseObserver =
                mock(StreamObserver.class);

        @SuppressWarnings("unchecked")
        StreamObserver<OrderItemRequest> requestObserver =
                mock(StreamObserver.class);

        when(asyncStub.withDeadlineAfter(
                anyLong(), eq(TimeUnit.SECONDS)
        )).thenReturn(asyncStub);

        when(asyncStub.processOrderStream(responseObserver))
                .thenReturn(requestObserver);

        StreamObserver<OrderItemRequest> actual =
                client.processOrderStream(responseObserver);

        assertSame(requestObserver, actual);

        verify(asyncStub).processOrderStream(responseObserver);
    }
}
