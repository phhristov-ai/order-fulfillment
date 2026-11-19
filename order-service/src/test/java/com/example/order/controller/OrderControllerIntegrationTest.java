package com.example.order.controller;

import com.example.order.client.InventoryGrpcClient;
import com.example.order.service.OrderService;

import com.example.inventory.grpc.ProductResponse;
import com.example.inventory.grpc.StockCheckResponse;

import io.grpc.Status;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class OrderControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private InventoryGrpcClient inventoryGrpcClient;

    @MockitoBean
    private OrderService orderService;

    // ==========================================
    // GET PRODUCT
    // ==========================================

    @Test
    void getProduct_shouldReturnProduct() throws Exception {

        ProductResponse response = ProductResponse.newBuilder()
                .setProductId(1L)
                .setName("Laptop")
                .setPrice(1200.0)
                .setAvailableQuantity(10)
                .build();

        when(inventoryGrpcClient.getProduct(1L))
                .thenReturn(response);

        mockMvc.perform(get("/orders/product/1"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(
                        org.springframework.http.MediaType.APPLICATION_JSON
                ))
                .andExpect(jsonPath("$.productId").value(1))
                .andExpect(jsonPath("$.name").value("Laptop"))
                .andExpect(jsonPath("$.price").value(1200.0))
                .andExpect(jsonPath("$.availableQuantity").value(10));

        verify(inventoryGrpcClient).getProduct(1L);
    }

    @Test
    void getProduct_shouldPropagateNotFoundFromInventory() throws Exception {

        when(inventoryGrpcClient.getProduct(99L))
                .thenThrow(
                        Status.NOT_FOUND
                                .withDescription("Product not found")
                                .asRuntimeException()
                );

        mockMvc.perform(get("/orders/product/99"))
                .andExpect(status().isNotFound());

        verify(inventoryGrpcClient).getProduct(99L);
    }

    // ==========================================
    // CHECK STOCK
    // ==========================================

    @Test
    void checkStock_shouldReturnAvailability() throws Exception {

        StockCheckResponse response = StockCheckResponse.newBuilder()
                .setAvailable(true)
                .setAvailableQuantity(10)
                .build();

        when(inventoryGrpcClient.checkStock(1L, 5))
                .thenReturn(response);

        mockMvc.perform(get("/orders/stock/1")
                        .param("quantity", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(true))
                .andExpect(jsonPath("$.availableQuantity").value(10));

        verify(inventoryGrpcClient).checkStock(1L, 5);
    }

    @Test
    void checkStock_shouldReturnUnavailableWhenStockIsInsufficient()
            throws Exception {

        StockCheckResponse response = StockCheckResponse.newBuilder()
                .setAvailable(false)
                .setAvailableQuantity(2)
                .build();

        when(inventoryGrpcClient.checkStock(1L, 5))
                .thenReturn(response);

        mockMvc.perform(get("/orders/stock/1")
                        .param("quantity", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(false))
                .andExpect(jsonPath("$.availableQuantity").value(2));
    }

    // ==========================================
    // CREATE ORDER - VALIDATION
    // ==========================================

    @Test
    void createOrder_shouldRejectInvalidRequest() throws Exception {

        mockMvc.perform(post("/orders")
                        .contentType("application/json")
                        .content("""
                                {
                                    "productId": 1,
                                    "quantity": 0
                                }
                                """))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(orderService);
    }

    // ==========================================
    // RESERVE MULTIPLE - VALIDATION
    // ==========================================

    @Test
    void reserveMultipleProducts_shouldRejectInvalidRequest()
            throws Exception {

        mockMvc.perform(post("/orders/reserve-multiple")
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(orderService);
    }

    // ==========================================
    // PROCESS ORDER ITEMS - VALIDATION
    // ==========================================

    @Test
    void processOrderItems_shouldRejectInvalidOrderId()
            throws Exception {

        mockMvc.perform(post("/orders/0/process-items")
                        .contentType("application/json")
                        .content("[]"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(orderService);
    }

    @Test
    void processOrderItems_shouldRejectEmptyItems()
            throws Exception {

        mockMvc.perform(post("/orders/1/process-items")
                        .contentType("application/json")
                        .content("[]"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(orderService);
    }
}
