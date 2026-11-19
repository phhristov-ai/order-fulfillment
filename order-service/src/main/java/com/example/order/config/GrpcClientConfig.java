package com.example.order.config;

import com.example.inventory.grpc.InventoryServiceGrpc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.grpc.client.GrpcChannelFactory;

@Configuration
public class GrpcClientConfig {

    @Bean
    public InventoryServiceGrpc.InventoryServiceBlockingStub inventoryBlockingStub(
            GrpcChannelFactory channels) {

        return InventoryServiceGrpc.newBlockingStub(
                channels.createChannel("inventory")
        );
    }

    @Bean
    public InventoryServiceGrpc.InventoryServiceStub inventoryAsyncStub(
            GrpcChannelFactory channels) {

        return InventoryServiceGrpc.newStub(
                channels.createChannel("inventory")
        );
    }
}