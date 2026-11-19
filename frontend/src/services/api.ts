import axios from "axios";

import type {
    StockCheckResponse,
    CreateOrderResponse,
    ReserveMultipleStocksResponse
} from "../types/inventory";
import type { OrderItemRequest, OrderItemResponse } from "../types/order";

const api = axios.create({
    baseURL: import.meta.env.VITE_API_BASE_URL || "http://localhost:8080",
    headers: {
        "Content-Type": "application/json",
    },
});

export async function getProduct(productId: number) {
    const response = await api.get(`/orders/product/${productId}`);

    return typeof response.data === "string"
        ? JSON.parse(response.data)
        : response.data;
}

export async function checkStock(
    productId: number,
    quantity: number
): Promise<StockCheckResponse> {

    const response = await api.get(
        `/orders/stock/${productId}`,
        { params: { quantity } }
    );

    return typeof response.data === "string"
        ? JSON.parse(response.data)
        : response.data;
}

export async function createOrder(
    productId: number,
    quantity: number
): Promise<CreateOrderResponse> {

    const response = await api.post("/orders", {
        productId,
        quantity,
    });

    return response.data;
}

export async function reserveMultipleStocks(
    items: { productId: number; quantity: number }[]
): Promise<ReserveMultipleStocksResponse> {

    const response = await api.post<ReserveMultipleStocksResponse>(
        "/orders/reserve-multiple",
        { items }
    );

    return response.data;
}

export async function processOrderItems(
    orderId: number,
    items: OrderItemRequest[]
): Promise<OrderItemResponse[]> {

    const response = await api.post(
        `/orders/${orderId}/process-items`,
        items
    );

    return response.data;
}

export default api;

