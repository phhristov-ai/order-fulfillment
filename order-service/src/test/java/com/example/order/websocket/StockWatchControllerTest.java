
package com.example.order.websocket;

import com.example.inventory.grpc.StockUpdate;
import com.example.order.client.InventoryGrpcClient;
import com.example.order.dto.StockUpdateDto;
import com.example.order.dto.WatchStockRequest;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StockWatchControllerTest {

    @Mock
    private InventoryGrpcClient inventoryGrpcClient;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    private StockWatchController controller;

    @BeforeEach
    void setUp() {
        controller = new StockWatchController(
                inventoryGrpcClient,
                messagingTemplate
        );
    }

    @Test
    void watchStockShouldSubscribeToInventoryUpdates() {

        controller.watchStock(new WatchStockRequest(1));

        verify(inventoryGrpcClient).watchStock(
                eq(1L),
                any(StreamObserver.class)
        );
    }

    @Test
    void watchStockShouldPublishUpdateToCorrectTopic() {

        ArgumentCaptor<StreamObserver<StockUpdate>> captor =
                ArgumentCaptor.forClass(StreamObserver.class);

        controller.watchStock(new WatchStockRequest(1));

        verify(inventoryGrpcClient).watchStock(
                eq(1L),
                captor.capture()
        );

        StreamObserver<StockUpdate> observer = captor.getValue();

        StockUpdate update = StockUpdate.newBuilder()
                .setProductId(1)
                .setAvailableQuantity(15)
                .build();

        observer.onNext(update);

        verify(messagingTemplate).convertAndSend(
                eq("/topic/stock/1"),
                Optional.ofNullable(argThat(message ->
                        message instanceof StockUpdateDto dto
                                && dto.productId() == 1
                                && dto.availableQuantity() == 15
                ))
        );
    }

    @Test
    void watchStockShouldPublishMultipleUpdates() {

        ArgumentCaptor<StreamObserver<StockUpdate>> captor =
                ArgumentCaptor.forClass(StreamObserver.class);

        controller.watchStock(new WatchStockRequest(1));

        verify(inventoryGrpcClient).watchStock(
                eq(1L),
                captor.capture()
        );

        StreamObserver<StockUpdate> observer = captor.getValue();

        observer.onNext(
                StockUpdate.newBuilder()
                        .setProductId(1)
                        .setAvailableQuantity(10)
                        .build()
        );

        observer.onNext(
                StockUpdate.newBuilder()
                        .setProductId(1)
                        .setAvailableQuantity(8)
                        .build()
        );

        verify(messagingTemplate, times(2)).convertAndSend(
                eq("/topic/stock/1"),
                any(StockUpdateDto.class)
        );
    }

    @Test
    void watchStockShouldHandleGrpcError() {

        ArgumentCaptor<StreamObserver<StockUpdate>> captor =
                ArgumentCaptor.forClass(StreamObserver.class);

        controller.watchStock(new WatchStockRequest(1));

        verify(inventoryGrpcClient).watchStock(
                eq(1L),
                captor.capture()
        );

        StreamObserver<StockUpdate> observer = captor.getValue();

        assertDoesNotThrow(() ->
                observer.onError(
                        Status.NOT_FOUND
                                .withDescription("Product not found")
                                .asRuntimeException()
                )
        );

        verifyNoInteractions(messagingTemplate);
    }

    @Test
    void watchStockShouldHandleStreamCompletion() {

        ArgumentCaptor<StreamObserver<StockUpdate>> captor =
                ArgumentCaptor.forClass(StreamObserver.class);

        controller.watchStock(new WatchStockRequest(1));

        verify(inventoryGrpcClient).watchStock(
                eq(1L),
                captor.capture()
        );

        StreamObserver<StockUpdate> observer = captor.getValue();

        assertDoesNotThrow(observer::onCompleted);

        verifyNoInteractions(messagingTemplate);
    }
}
