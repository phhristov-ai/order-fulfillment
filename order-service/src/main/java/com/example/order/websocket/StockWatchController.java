package com.example.order.websocket;

import com.example.inventory.grpc.StockUpdate;
import com.example.order.client.InventoryGrpcClient;
import com.example.order.dto.StockUpdateDto;
import com.example.order.dto.WatchStockRequest;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;

import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

@Controller
public class StockWatchController {

    private final InventoryGrpcClient inventoryGrpcClient;
    private final SimpMessagingTemplate messagingTemplate;

    public StockWatchController(
            InventoryGrpcClient inventoryGrpcClient,
            SimpMessagingTemplate messagingTemplate) {

        this.inventoryGrpcClient = inventoryGrpcClient;
        this.messagingTemplate = messagingTemplate;
    }

    @MessageMapping("/stock/watch")
    public void watchStock(WatchStockRequest request) {

        long productId = request.productId();

        inventoryGrpcClient.watchStock(
                productId,
                new StreamObserver<>() {

                    @Override
                    public void onNext(StockUpdate update) {

                        StockUpdateDto dto = new StockUpdateDto(
                                update.getProductId(),
                                update.getAvailableQuantity()
                        );

                        messagingTemplate.convertAndSend(
                                "/topic/stock/" + update.getProductId(),
                                dto
                        );
                    }

                    @Override
                    public void onError(Throwable throwable) {

                        Status status = Status.fromThrowable(throwable);

                        System.err.println(
                                "Stock monitoring failed: "
                                        + status.getCode()
                                        + " - "
                                        + status.getDescription()
                        );
                    }

                    @Override
                    public void onCompleted() {
                        System.out.println(
                                "Stock monitoring completed for product "
                                        + productId
                        );
                    }
                }
        );
    }
}