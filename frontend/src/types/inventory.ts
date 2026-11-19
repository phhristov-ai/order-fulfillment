export interface Product {
    productId: number;
    name: string;
    price: number;
    availableQuantity: number;
}

export interface StockCheckResponse {
    available: boolean;
    availableQuantity: number;
}

export interface ReservationRequest {
    productId: number;
    quantity: number;
}

export interface ReservationResult {
    productId: number;
    requestedQuantity: number;
    remainingQuantity: number;
    successful: boolean;
    errorMessage: string;
}

export interface ReserveMultipleStocksResponse {
    successfulReservations: number;
    failedReservations: number;
    results: ReservationResult[];
}

export interface CreateOrderResponse {
    id: number;
    productId: number;
    productName: string;
    quantity: number;
    price: number;
    totalPrice: number;
}

export interface Product {
    productId: number;
    name: string;
    price: number;
    availableQuantity: number;
}
