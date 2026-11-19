import { Client } from "@stomp/stompjs";

export interface StockUpdate {
    productId: number;
    availableQuantity: number;
}

export interface WatchStockRequest {
    productId: number;
}

const client = new Client({
    brokerURL: import.meta.env.VITE_WS_URL,

    reconnectDelay: 5000,

    debug: (message: string) => {
        console.log("[STOMP]", message);
    },

    onConnect: () => {
        console.log("Connected to Order Service WebSocket");
    },

    onDisconnect: () => {
        console.log("Disconnected from WebSocket");
    },

    onStompError: (frame) => {
        console.error("STOMP error:", frame.headers.message);
    }
});

export default client;