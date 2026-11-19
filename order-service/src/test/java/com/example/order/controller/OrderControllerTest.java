package com.example.order.controller;

import com.example.inventory.grpc.*;
import com.example.order.client.InventoryGrpcClient;
import com.example.order.dto.*;
import com.example.order.model.Order;
import com.example.order.service.OrderService;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(OrderController.class)
class OrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private InventoryGrpcClient inventoryGrpcClient;

    @MockitoBean
    private OrderService orderService;

    // --------------------------------------------------
    // GET PRODUCT
    // --------------------------------------------------

    @Test
    void getProductShouldReturnProductInformation() throws Exception {

        ProductResponse response = ProductResponse.newBuilder()
                .setProductId(1)
                .setName("Laptop")
                .setPrice(1200)
                .setAvailableQuantity(10)
                .build();

        when(inventoryGrpcClient.getProduct(1))
                .thenReturn(response);

        mockMvc.perform(get("/orders/product/1"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Laptop")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("1200")));

        verify(inventoryGrpcClient).getProduct(1);
    }

    // --------------------------------------------------
    // CHECK STOCK
    // --------------------------------------------------

    @Test
    void checkStockShouldReturnStockInformation() throws Exception {

        StockCheckResponse response = StockCheckResponse.newBuilder()
                .setAvailable(true)
                .setAvailableQuantity(10)
                .build();

        when(inventoryGrpcClient.checkStock(1, 5))
                .thenReturn(response);

        mockMvc.perform(
                        get("/orders/stock/1")
                                .param("quantity", "5")
                )
                .andExpect(status().isOk())
                .andExpect(content().string(
                        org.hamcrest.Matchers.containsString("true")
                ))
                .andExpect(content().string(
                        org.hamcrest.Matchers.containsString("10")
                ));

        verify(inventoryGrpcClient).checkStock(1, 5);
    }

    // --------------------------------------------------
    // CREATE ORDER
    // --------------------------------------------------

    @Test
    void createOrderShouldReturnCreatedOrder() throws Exception {

        Order order = new Order(
                1,
                1,
                "Laptop",
                2,
                1200,
                2400
        );

        when(orderService.createOrder(1, 2))
                .thenReturn(order);

        mockMvc.perform(
                        post("/orders")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                            "productId": 1,
                                            "quantity": 2
                                        }
                                        """)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productId").value(1))
                .andExpect(jsonPath("$.quantity").value(2));

        verify(orderService).createOrder(1, 2);
    }

    // --------------------------------------------------
    // RESERVE MULTIPLE PRODUCTS
    // --------------------------------------------------

    @Test
    void reserveMultipleProductsShouldReturnReservationSummary()
            throws Exception {

        ReserveMultipleStocksResponseDto response =
                new ReserveMultipleStocksResponseDto(
                        2,
                        0,
                        List.of(
                                new ReservationResultDto(
                                        1, 2, 8, true, ""
                                ),
                                new ReservationResultDto(
                                        2, 3, 17, true, ""
                                )
                        )
                );

        when(orderService.reserveMultipleProducts(anyList()))
                .thenReturn(CompletableFuture.completedFuture(response));

        mockMvc.perform(
                        post("/orders/reserve-multiple")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                            "items": [
                                                {
                                                    "productId": 1,
                                                    "quantity": 2
                                                },
                                                {
                                                    "productId": 2,
                                                    "quantity": 3
                                                }
                                            ]
                                        }
                                        """)
                )
                .andExpect(request().asyncStarted())
                .andDo(result -> mockMvc.perform(
                                        asyncDispatch(result)
                                )
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.successfulReservations").value(2))
                                .andExpect(jsonPath("$.failedReservations").value(0))
                                .andExpect(jsonPath("$.results.length()").value(2))
                                .andExpect(jsonPath("$.results[0].productId").value(1))
                                .andExpect(jsonPath("$.results[0].successful").value(true))
                                .andExpect(jsonPath("$.results[1].productId").value(2))
                );

        verify(orderService).reserveMultipleProducts(argThat(items ->
                items.size() == 2
                        && items.get(0).productId() == 1
                        && items.get(0).quantity() == 2
                        && items.get(1).productId() == 2
                        && items.get(1).quantity() == 3
        ));
    }

    // --------------------------------------------------
    // PROCESS ORDER ITEMS
    // --------------------------------------------------


    @Test
    void processOrderItemsShouldReturnIndividualResults()
            throws Exception {

        List<OrderItemResponseDto> responses = List.of(
                new OrderItemResponseDto(
                        1001, 1, true, 8, "Reservation successful"
                ),
                new OrderItemResponseDto(
                        1001, 2, false, 1, "Insufficient stock"
                )
        );

        when(orderService.processOrderItems(eq(1001L), anyList()))
                .thenReturn(CompletableFuture.completedFuture(responses));

        MvcResult mvcResult = mockMvc.perform(
                        post("/orders/1001/process-items")
                                .contentType(MediaType.APPLICATION_JSON)
                                .accept(MediaType.APPLICATION_JSON)
                                .content("""
                                    [
                                        {
                                            "productId": 1,
                                            "quantity": 2
                                        },
                                        {
                                            "productId": 2,
                                            "quantity": 3
                                        }
                                    ]
                                    """)
                )
                .andExpect(request().asyncStarted())
                .andReturn();

        // Wait for the CompletableFuture result.
        mvcResult.getAsyncResult(5000);

        // Dispatch the completed asynchronous request.
        mockMvc.perform(asyncDispatch(mvcResult))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_JSON
                ))
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].orderId").value(1001))
                .andExpect(jsonPath("$[0].productId").value(1))
                .andExpect(jsonPath("$[0].successful").value(true))
                .andExpect(jsonPath("$[1].productId").value(2))
                .andExpect(jsonPath("$[1].successful").value(false))
                .andExpect(jsonPath("$[1].message")
                        .value("Insufficient stock"));

        verify(orderService).processOrderItems(
                eq(1001L),
                argThat(items ->
                        items.size() == 2
                                && items.get(0).productId() == 1
                                && items.get(1).productId() == 2
                )
        );
    }


    @Test
    void createOrderShouldRejectZeroQuantity() throws Exception {

        mockMvc.perform(
                        post("/orders")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                    {
                                        "productId": 1,
                                        "quantity": 0
                                    }
                                    """)
                )
                .andExpect(status().isBadRequest());

        verifyNoInteractions(orderService);
    }

    @Test
    void createOrderShouldRejectNegativeProductId() throws Exception {

        mockMvc.perform(
                        post("/orders")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                    {
                                        "productId": -1,
                                        "quantity": 2
                                    }
                                    """)
                )
                .andExpect(status().isBadRequest());

        verifyNoInteractions(orderService);
    }


    @Test
    void reserveMultipleProductsShouldRejectEmptyItems() throws Exception {

        mockMvc.perform(
                        post("/orders/reserve-multiple")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                    {
                                        "items": []
                                    }
                                    """)
                )
                .andExpect(status().isBadRequest());

        verifyNoInteractions(orderService);
    }

    @Test
    void reserveMultipleProductsShouldRejectInvalidQuantity()
            throws Exception {

        mockMvc.perform(
                        post("/orders/reserve-multiple")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                    {
                                        "items": [
                                            {
                                                "productId": 1,
                                                "quantity": 0
                                            }
                                        ]
                                    }
                                    """)
                )
                .andExpect(status().isBadRequest());

        verifyNoInteractions(orderService);
    }


    @Test
    void processOrderItemsShouldRejectEmptyList() throws Exception {

        mockMvc.perform(
                        post("/orders/1001/process-items")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("[]")
                )
                .andExpect(status().isBadRequest());

        verifyNoInteractions(orderService);
    }

    @Test
    void processOrderItemsShouldRejectNegativeProductId()
            throws Exception {

        mockMvc.perform(
                        post("/orders/1001/process-items")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                    [
                                        {
                                            "productId": -1,
                                            "quantity": 2
                                        }
                                    ]
                                    """)
                )
                .andExpect(status().isBadRequest());

        verifyNoInteractions(orderService);
    }

    @Test
    void processOrderItemsShouldRejectZeroOrderId() throws Exception {

        mockMvc.perform(
                        post("/orders/0/process-items")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                    [
                                        {
                                            "productId": 1,
                                            "quantity": 2
                                        }
                                    ]
                                    """)
                )
                .andExpect(status().isBadRequest());

        verifyNoInteractions(orderService);
    }

}
