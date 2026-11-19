package com.example.order.client;

import com.example.inventory.grpc.*;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

@Component
public class InventoryGrpcClient {

    private static final long RPC_DEADLINE_SECONDS = 2;
    private static final long RESERVATION_DEADLINE_SECONDS = 5;
    private static final long ORDER_STREAM_DEADLINE_SECONDS = 30;

    private static final int MAX_RETRY_ATTEMPTS = 10;
    private static final long RETRY_ATTEMPT_SECONDS = 1;
    private static final long OVERALL_RETRY_SECONDS = 10;
    private static final long RETRY_DELAY_MILLIS = 500;

    private final InventoryServiceGrpc.InventoryServiceBlockingStub stub;
    private final InventoryServiceGrpc.InventoryServiceStub asyncStub;

    public InventoryGrpcClient(
            InventoryServiceGrpc.InventoryServiceBlockingStub stub,
            InventoryServiceGrpc.InventoryServiceStub asyncStub) {

        this.stub = stub;
        this.asyncStub = asyncStub;
    }

    public ProductResponse getProduct(long productId) {

        ProductRequest request = ProductRequest.newBuilder()
                .setProductId(productId)
                .build();

        return getProductWithRetry(request);
    }

    public StockCheckResponse checkStock(long productId, int requestedQuantity) {
        StockCheckRequest request = StockCheckRequest.newBuilder()
                .setProductId(productId)
                .setRequestedQuantity(requestedQuantity)
                .build();

        return withDeadline().checkStock(request);
    }

    public ReserveStockResponse reserveStock(long productId, int quantity) {
        ReserveStockRequest request = ReserveStockRequest.newBuilder()
                .setProductId(productId)
                .setQuantity(quantity)
                .build();

        return withDeadline().reserveStock(request);
    }

    private InventoryServiceGrpc.InventoryServiceBlockingStub withDeadline() {
        return stub.withDeadlineAfter(RPC_DEADLINE_SECONDS, TimeUnit.SECONDS);
    }

    private ProductResponse getProductWithRetry(ProductRequest request) {

        long overallTimeout = System.nanoTime()
                + TimeUnit.SECONDS.toNanos(OVERALL_RETRY_SECONDS);

        for (int attempt = 1; attempt <= MAX_RETRY_ATTEMPTS; attempt++) {

            long remaining = overallTimeout - System.nanoTime();

            if (remaining <= 0) {
                throw Status.DEADLINE_EXCEEDED
                        .withDescription("Overall retry deadline exceeded")
                        .asRuntimeException();
            }

            long attemptTimeout = Math.min(
                    remaining,
                    TimeUnit.SECONDS.toNanos(RETRY_ATTEMPT_SECONDS)
            );

            try {
                return stub
                        .withDeadlineAfter(attemptTimeout, TimeUnit.NANOSECONDS)
                        .getProduct(request);

            } catch (StatusRuntimeException e) {

                Status.Code code = e.getStatus().getCode();

                System.out.println(
                        "Attempt " + attempt + " failed: " + code
                );

                if (code != Status.Code.UNAVAILABLE
                        || attempt == MAX_RETRY_ATTEMPTS) {
                    throw e;
                }

                try {
                    Thread.sleep(RETRY_DELAY_MILLIS);

                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();

                    throw Status.CANCELLED
                            .withDescription("Retry interrupted")
                            .withCause(interrupted)
                            .asRuntimeException();
                }
            }
        }

        throw new IllegalStateException("Unexpected retry state");
    }

    public void watchStock(
            long productId,
            StreamObserver<StockUpdate> responseObserver) {

        ProductRequest request = ProductRequest.newBuilder()
                .setProductId(productId)
                .build();

        asyncStub
                .withDeadlineAfter(RESERVATION_DEADLINE_SECONDS, TimeUnit.MINUTES)
                .watchStock(request, responseObserver);
    }


    public CompletableFuture<ReserveMultipleStocksResponse>
    reserveMultipleStocks(List<ReserveStockRequest> requests) {

        if (requests == null || requests.isEmpty()) {
            return CompletableFuture.failedFuture(
                    new IllegalArgumentException(
                            "At least one reservation request is required"
                    )
            );
        }

        CompletableFuture<ReserveMultipleStocksResponse> future =
                new CompletableFuture<>();

        StreamObserver<ReserveMultipleStocksResponse> responseObserver =
                createReservationResponseObserver(future);

        StreamObserver<ReserveStockRequest> requestObserver =
                asyncStub.withDeadlineAfter(RESERVATION_DEADLINE_SECONDS, TimeUnit.SECONDS)
                        .reserveMultipleStocks(responseObserver);

        sendReservationRequests(requestObserver, requests, future);

        return future;
    }

    private StreamObserver<ReserveMultipleStocksResponse>
    createReservationResponseObserver(
            CompletableFuture<ReserveMultipleStocksResponse> future) {

        AtomicReference<ReserveMultipleStocksResponse> responseRef =
                new AtomicReference<>();

        return new StreamObserver<>() {

            @Override
            public void onNext(ReserveMultipleStocksResponse response) {

                if (!responseRef.compareAndSet(null, response)) {
                    future.completeExceptionally(
                            new IllegalStateException(
                                    "Received multiple responses"
                            )
                    );
                }
            }

            @Override
            public void onError(Throwable throwable) {
                future.completeExceptionally(throwable);
            }

            @Override
            public void onCompleted() {

                ReserveMultipleStocksResponse response = responseRef.get();

                if (response == null) {
                    future.completeExceptionally(
                            new IllegalStateException(
                                    "No reservation response received"
                            )
                    );
                } else {
                    future.complete(response);
                }
            }
        };
    }

    private void sendReservationRequests(
            StreamObserver<ReserveStockRequest> requestObserver,
            List<ReserveStockRequest> requests,
            CompletableFuture<ReserveMultipleStocksResponse> future) {

        try {
            for (ReserveStockRequest request : requests) {
                requestObserver.onNext(request);
            }

            requestObserver.onCompleted();

        } catch (RuntimeException exception) {

            future.completeExceptionally(exception);

            try {
                requestObserver.onError(exception);
            } catch (RuntimeException ignored) {
                // Stream may already have terminated.
            }
        }
    }


    public StreamObserver<OrderItemRequest> processOrderStream(
            StreamObserver<OrderItemResponse> responseObserver) {

        return asyncStub
                .withDeadlineAfter(ORDER_STREAM_DEADLINE_SECONDS, TimeUnit.SECONDS)
                .processOrderStream(responseObserver);
    }

}