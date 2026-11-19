
package com.example.inventory.service;

import com.example.inventory.grpc.StockUpdate;
import io.grpc.stub.StreamObserver;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component
public class StockSubscriptionManager {

    private final ConcurrentMap<Long, Set<Subscription>> subscriptions =
            new ConcurrentHashMap<>();

    public Subscription subscribe(
            long productId,
            StreamObserver<StockUpdate> observer) {

        Subscription subscription = new Subscription(observer);

        subscriptions.compute(productId, (id, subscribers) -> {
            if (subscribers == null) {
                subscribers = ConcurrentHashMap.newKeySet();
            }

            subscribers.add(subscription);
            return subscribers;
        });

        return subscription;
    }

    public void unsubscribe(
            long productId,
            Subscription subscription) {

        subscription.deactivate();

        subscriptions.computeIfPresent(productId, (id, subscribers) -> {
            subscribers.remove(subscription);

            return subscribers.isEmpty() ? null : subscribers;
        });
    }

    public void publish(long productId, int availableQuantity) {

        Set<Subscription> subscribers = subscriptions.get(productId);

        if (subscribers == null) {
            return;
        }

        StockUpdate update = StockUpdate.newBuilder()
                .setProductId(productId)
                .setAvailableQuantity(availableQuantity)
                .build();

        for (Subscription subscription : subscribers) {
            try {
                subscription.send(update);
            } catch (RuntimeException exception) {
                unsubscribe(productId, subscription);
            }
        }
    }

    public static class Subscription {

        private final StreamObserver<StockUpdate> observer;
        private boolean active = true;

        public Subscription(StreamObserver<StockUpdate> observer) {
            this.observer = observer;
        }

        public synchronized void send(StockUpdate update) {
            if (active) {
                observer.onNext(update);
            }
        }

        public synchronized void deactivate() {
            active = false;
        }
    }
}