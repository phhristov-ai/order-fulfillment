package com.example.inventory.grpc;

import com.example.inventory.exception.InsufficientStockException;
import com.example.inventory.model.Product;
import com.example.inventory.repository.ProductRepository;
import com.example.inventory.service.StockSubscriptionManager;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.ServerCallStreamObserver;
import io.grpc.stub.StreamObserver;

import org.springframework.grpc.server.service.GrpcService;

import java.util.ArrayList;
import java.util.List;

@GrpcService
public class InventoryGrpcService
        extends InventoryServiceGrpc.InventoryServiceImplBase {

    private final ProductRepository productRepository;

    private final StockSubscriptionManager subscriptionManager;

    public InventoryGrpcService(ProductRepository productRepository, StockSubscriptionManager subscriptionManager) {
        this.productRepository = productRepository;
        this.subscriptionManager = subscriptionManager;
    }

    @Override
    public void getProduct(
            ProductRequest request,
            StreamObserver<ProductResponse> responseObserver) {

        Product product = productRepository.findById(request.getProductId())
                .orElseThrow(() -> Status.NOT_FOUND
                        .withDescription("Product not found")
                        .asRuntimeException());

        ProductResponse response = ProductResponse.newBuilder()
                .setProductId(product.id())
                .setName(product.name())
                .setPrice(product.price())
                .setAvailableQuantity(product.availableQuantity())
                .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    @Override
    public void checkStock(
            StockCheckRequest request,
            StreamObserver<StockCheckResponse> responseObserver) {

        Product product = productRepository.findById(request.getProductId())
                .orElseThrow(() -> Status.NOT_FOUND
                        .withDescription("Product not found")
                        .asRuntimeException());

        boolean available =
                product.availableQuantity() >= request.getRequestedQuantity();

        StockCheckResponse response = StockCheckResponse.newBuilder()
                .setAvailable(available)
                .setAvailableQuantity(product.availableQuantity())
                .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    @Override
    public void reserveStock(
            ReserveStockRequest request,
            StreamObserver<ReserveStockResponse> responseObserver) {

        if (request.getQuantity() <= 0) {
            responseObserver.onError(
                    Status.INVALID_ARGUMENT
                            .withDescription("Quantity must be greater than zero")
                            .asRuntimeException()
            );
            return;
        }

        try {
            Product product = productRepository
                    .reserve(request.getProductId(), request.getQuantity())
                    .orElseThrow(() -> Status.NOT_FOUND
                            .withDescription("Product not found")
                            .asRuntimeException());

            subscriptionManager.publish(
                    product.id(),
                    product.availableQuantity()
            );

            ReserveStockResponse response = ReserveStockResponse.newBuilder()
                    .setProductId(product.id())
                    .setName(product.name())
                    .setPrice(product.price())
                    .setRemainingQuantity(product.availableQuantity())
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();

        } catch (InsufficientStockException exception) {
            responseObserver.onError(
                    Status.FAILED_PRECONDITION
                            .withDescription("Insufficient stock")
                            .asRuntimeException()
            );
        }
    }

    @Override
    public void watchStock(
            ProductRequest request,
            StreamObserver<StockUpdate> responseObserver) {

        Product product = productRepository.findById(request.getProductId())
                .orElse(null);

        if (product == null) {
            responseObserver.onError(
                    Status.NOT_FOUND
                            .withDescription("Product not found")
                            .asRuntimeException()
            );
            return;
        }

        long productId = product.id();

        ServerCallStreamObserver<StockUpdate> serverObserver =
                (ServerCallStreamObserver<StockUpdate>) responseObserver;

        StockSubscriptionManager.Subscription subscription =
                subscriptionManager.subscribe(productId, responseObserver);

        // Remove subscription when the client disconnects.
        serverObserver.setOnCancelHandler(() ->
                subscriptionManager.unsubscribe(productId, subscription)
        );

        if (serverObserver.isCancelled()) {
            subscriptionManager.unsubscribe(productId, subscription);
            return;
        }

        StockUpdate initialUpdate = StockUpdate.newBuilder()
                .setProductId(productId)
                .setAvailableQuantity(product.availableQuantity())
                .build();

        try {
            subscription.send(initialUpdate);
        } catch (RuntimeException exception) {
            subscriptionManager.unsubscribe(productId, subscription);
        }
    }

    @Override
    public StreamObserver<ReserveStockRequest> reserveMultipleStocks(
            StreamObserver<ReserveMultipleStocksResponse> responseObserver) {

        List<ReservationResult> results = new ArrayList<>();

        return new StreamObserver<>() {

            private int successful = 0;
            private int failed = 0;

            @Override
            public void onNext(ReserveStockRequest request) {

                ReservationResult.Builder result =
                        ReservationResult.newBuilder()
                                .setProductId(request.getProductId())
                                .setRequestedQuantity(request.getQuantity());

                try {
                    Product product = productRepository
                            .reserve(request.getProductId(), request.getQuantity())
                            .orElseThrow(() -> new RuntimeException("Product not found"));

                    result.setSuccessful(true)
                            .setRemainingQuantity(product.availableQuantity());

                    successful++;

                    subscriptionManager.publish(
                            product.id(),
                            product.availableQuantity()
                    );

                } catch (InsufficientStockException exception) {

                    result.setSuccessful(false)
                            .setErrorMessage("Insufficient stock");

                    failed++;

                } catch (RuntimeException exception) {

                    result.setSuccessful(false)
                            .setErrorMessage(exception.getMessage());

                    failed++;
                }

                results.add(result.build());
            }

            @Override
            public void onError(Throwable throwable) {
                // Client terminated the request stream with an error.
                // Do not send a normal response.
            }

            @Override
            public void onCompleted() {

                ReserveMultipleStocksResponse response =
                        ReserveMultipleStocksResponse.newBuilder()
                                .setSuccessfulReservations(successful)
                                .setFailedReservations(failed)
                                .addAllResults(results)
                                .build();

                responseObserver.onNext(response);
                responseObserver.onCompleted();
            }
        };
    }

    @Override
    public StreamObserver<OrderItemRequest> processOrderStream(
            StreamObserver<OrderItemResponse> responseObserver) {

        return new StreamObserver<>() {

            @Override
            public void onNext(OrderItemRequest request) {

                OrderItemResponse.Builder result =
                        OrderItemResponse.newBuilder()
                                .setOrderId(request.getOrderId())
                                .setProductId(request.getProductId());

                if (request.getQuantity() <= 0) {
                    result.setSuccessful(false)
                            .setMessage("Quantity must be greater than zero");

                    responseObserver.onNext(result.build());
                    return;
                }

                try {
                    Product product = productRepository
                            .reserve(request.getProductId(), request.getQuantity())
                            .orElseThrow(() -> Status.NOT_FOUND
                                    .withDescription("Product not found")
                                    .asRuntimeException());

                    subscriptionManager.publish(
                            product.id(),
                            product.availableQuantity()
                    );

                    result.setSuccessful(true)
                            .setRemainingQuantity(product.availableQuantity())
                            .setMessage("Reservation successful");

                } catch (InsufficientStockException exception) {

                    result.setSuccessful(false)
                            .setMessage("Insufficient stock");

                } catch (StatusRuntimeException exception) {

                    if (exception.getStatus().getCode() == Status.Code.NOT_FOUND) {
                        result.setSuccessful(false)
                                .setMessage("Product not found");
                    } else {
                        responseObserver.onError(exception);
                        return;
                    }
                }

                responseObserver.onNext(result.build());
            }

            @Override
            public void onError(Throwable throwable) {
                // Client terminated the stream.
            }

            @Override
            public void onCompleted() {
                responseObserver.onCompleted();
            }
        };
    }
}