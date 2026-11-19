
export interface OrderItemRequest {
    productId: number;
    quantity: number;
}

export interface OrderItemResponse {
    orderId: number;
    productId: number;
    successful: boolean;
    remainingQuantity: number;
    message: string;
}

export interface OrderProduct {
    productId: number;
    name: string;
    price: number;
    availableQuantity: number;
}
