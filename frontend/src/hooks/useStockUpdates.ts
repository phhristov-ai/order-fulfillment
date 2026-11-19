
import { useEffect, useState } from "react";
import { Client, type IMessage, type StompSubscription } from "@stomp/stompjs";
import SockJS from "sockjs-client";

interface StockUpdate {
    productId: number;
    availableQuantity: number;
}

export function useStockUpdates(productId: number) {
    const [stock, setStock] = useState<StockUpdate | null>(null);
    const [connected, setConnected] = useState(false);

    useEffect(() => {
        if (!productId) return;

        let subscription: StompSubscription | undefined;

        const client = new Client({
            webSocketFactory: () =>
                new SockJS("http://localhost:8080/ws"),

            reconnectDelay: 5000,

            onConnect: () => {
                setConnected(true);

                subscription = client.subscribe(
                    `/topic/stock/${productId}`,
                    (message: IMessage) => {
                        try {
                            const update: StockUpdate =
                                JSON.parse(message.body);

                            setStock(update);
                        } catch (error) {
                            console.error(
                                "Invalid stock update:",
                                error
                            );
                        }
                    }
                );
            },

            onDisconnect: () => {
                setConnected(false);
            },

            onStompError: (frame) => {
                console.error(
                    "STOMP error:",
                    frame.headers["message"]
                );
            },

            onWebSocketClose: () => {
                setConnected(false);
            },

            onWebSocketError: (event) => {
                console.error("WebSocket error:", event);
            }
        });

        client.activate();

        return () => {
            subscription?.unsubscribe();
            setConnected(false);
            void client.deactivate();
        };

    }, [productId]);

    return {
        stock,
        connected
    };
}
