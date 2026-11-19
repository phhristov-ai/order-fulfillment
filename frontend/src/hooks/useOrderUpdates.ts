
import { useCallback, useEffect, useRef, useState } from "react";
import {
    Client,
    type IMessage,
    type StompSubscription
} from "@stomp/stompjs";
import SockJS from "sockjs-client";

import type { OrderItemResponse } from "../types/order";

export function useOrderUpdates() {
    const [connected, setConnected] = useState(false);

    const clientRef = useRef<Client | null>(null);

    useEffect(() => {
        const client = new Client({
            webSocketFactory: () =>
                new SockJS("http://localhost:8080/ws"),

            reconnectDelay: 5000,

            onConnect: () => {
                setConnected(true);
            },

            onDisconnect: () => {
                setConnected(false);
            },

            onWebSocketClose: () => {
                setConnected(false);
            },

            onStompError: (frame) => {
                console.error(
                    "Order WebSocket error:",
                    frame.headers["message"]
                );
            },

            onWebSocketError: (error) => {
                console.error("WebSocket error:", error);
            }
        });

        clientRef.current = client;
        client.activate();

        return () => {
            clientRef.current = null;
            setConnected(false);
            void client.deactivate();
        };
    }, []);

    const subscribeToOrder = useCallback(
        (
            orderId: number,
            onUpdate: (response: OrderItemResponse) => void
        ): StompSubscription | null => {

            const client = clientRef.current;

            if (!client || !client.connected) {
                console.error("Order WebSocket is not connected.");
                return null;
            }

            return client.subscribe(
                `/topic/orders/${orderId}/items`,
                (message: IMessage) => {
                    try {
                        const response: OrderItemResponse =
                            JSON.parse(message.body);

                        onUpdate(response);
                    } catch (error) {
                        console.error(
                            "Invalid order update:",
                            error
                        );
                    }
                }
            );
        },
        []
    );

    return {
        connected,
        subscribeToOrder
    };
}
