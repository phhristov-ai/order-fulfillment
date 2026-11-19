
package com.example.inventory.service;

import com.example.inventory.grpc.StockUpdate;
import io.grpc.stub.StreamObserver;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class StockSubscriptionManagerTest {

    private StockSubscriptionManager subscriptionManager;

    @BeforeEach
    void setUp() {
        subscriptionManager = new StockSubscriptionManager();
    }

    // ==========================================
    // SUBSCRIBE
    // ==========================================

    @Test
    void subscribe_shouldRegisterSubscriber() {

        @SuppressWarnings("unchecked")
        StreamObserver<StockUpdate> observer = mock(StreamObserver.class);

        StockSubscriptionManager.Subscription subscription =
                subscriptionManager.subscribe(1L, observer);

        assertNotNull(subscription);

        subscriptionManager.publish(1L, 10);

        verify(observer).onNext(
                StockUpdate.newBuilder()
                        .setProductId(1L)
                        .setAvailableQuantity(10)
                        .build()
        );
    }

    @Test
    void subscribe_shouldSupportMultipleSubscribersForSameProduct() {

        @SuppressWarnings("unchecked")
        StreamObserver<StockUpdate> observer1 = mock(StreamObserver.class);

        @SuppressWarnings("unchecked")
        StreamObserver<StockUpdate> observer2 = mock(StreamObserver.class);

        subscriptionManager.subscribe(1L, observer1);
        subscriptionManager.subscribe(1L, observer2);

        subscriptionManager.publish(1L, 5);

        StockUpdate expectedUpdate = StockUpdate.newBuilder()
                .setProductId(1L)
                .setAvailableQuantity(5)
                .build();

        verify(observer1).onNext(expectedUpdate);
        verify(observer2).onNext(expectedUpdate);
    }

    @Test
    void subscribe_shouldKeepSubscribersForDifferentProductsSeparate() {

        @SuppressWarnings("unchecked")
        StreamObserver<StockUpdate> observer1 = mock(StreamObserver.class);

        @SuppressWarnings("unchecked")
        StreamObserver<StockUpdate> observer2 = mock(StreamObserver.class);

        subscriptionManager.subscribe(1L, observer1);
        subscriptionManager.subscribe(2L, observer2);

        subscriptionManager.publish(1L, 8);

        verify(observer1).onNext(
                StockUpdate.newBuilder()
                        .setProductId(1L)
                        .setAvailableQuantity(8)
                        .build()
        );

        verifyNoInteractions(observer2);
    }

    // ==========================================
    // UNSUBSCRIBE
    // ==========================================

    @Test
    void unsubscribe_shouldStopSendingUpdatesToSubscriber() {

        @SuppressWarnings("unchecked")
        StreamObserver<StockUpdate> observer = mock(StreamObserver.class);

        StockSubscriptionManager.Subscription subscription =
                subscriptionManager.subscribe(1L, observer);

        subscriptionManager.unsubscribe(1L, subscription);

        subscriptionManager.publish(1L, 5);

        verifyNoInteractions(observer);
    }

    @Test
    void unsubscribe_shouldKeepOtherSubscribersActive() {

        @SuppressWarnings("unchecked")
        StreamObserver<StockUpdate> observer1 = mock(StreamObserver.class);

        @SuppressWarnings("unchecked")
        StreamObserver<StockUpdate> observer2 = mock(StreamObserver.class);

        StockSubscriptionManager.Subscription subscription1 =
                subscriptionManager.subscribe(1L, observer1);

        subscriptionManager.subscribe(1L, observer2);

        subscriptionManager.unsubscribe(1L, subscription1);

        subscriptionManager.publish(1L, 7);

        verifyNoInteractions(observer1);

        verify(observer2).onNext(
                StockUpdate.newBuilder()
                        .setProductId(1L)
                        .setAvailableQuantity(7)
                        .build()
        );
    }

    @Test
    void unsubscribe_shouldRemoveProductWhenLastSubscriberIsRemoved() {

        @SuppressWarnings("unchecked")
        StreamObserver<StockUpdate> observer = mock(StreamObserver.class);

        StockSubscriptionManager.Subscription subscription =
                subscriptionManager.subscribe(1L, observer);

        subscriptionManager.unsubscribe(1L, subscription);

        subscriptionManager.publish(1L, 10);

        verifyNoInteractions(observer);
    }

    @Test
    void unsubscribe_shouldBeSafeWhenCalledMultipleTimes() {

        @SuppressWarnings("unchecked")
        StreamObserver<StockUpdate> observer = mock(StreamObserver.class);

        StockSubscriptionManager.Subscription subscription =
                subscriptionManager.subscribe(1L, observer);

        assertDoesNotThrow(() -> {
            subscriptionManager.unsubscribe(1L, subscription);
            subscriptionManager.unsubscribe(1L, subscription);
        });

        subscriptionManager.publish(1L, 10);

        verifyNoInteractions(observer);
    }

    // ==========================================
    // PUBLISH
    // ==========================================

    @Test
    void publish_shouldSendStockUpdateToSubscribers() {

        @SuppressWarnings("unchecked")
        StreamObserver<StockUpdate> observer = mock(StreamObserver.class);

        subscriptionManager.subscribe(1L, observer);

        subscriptionManager.publish(1L, 6);

        verify(observer).onNext(
                StockUpdate.newBuilder()
                        .setProductId(1L)
                        .setAvailableQuantity(6)
                        .build()
        );
    }

    @Test
    void publish_shouldNotFailWhenThereAreNoSubscribers() {

        assertDoesNotThrow(() ->
                subscriptionManager.publish(1L, 10)
        );
    }

    @Test
    void publish_shouldNotSendUpdatesToSubscribersOfAnotherProduct() {

        @SuppressWarnings("unchecked")
        StreamObserver<StockUpdate> observer = mock(StreamObserver.class);

        subscriptionManager.subscribe(2L, observer);

        subscriptionManager.publish(1L, 10);

        verifyNoInteractions(observer);
    }

    @Test
    void publish_shouldRemoveSubscriberWhenSendingUpdateFails() {

        @SuppressWarnings("unchecked")
        StreamObserver<StockUpdate> observer = mock(StreamObserver.class);

        StockSubscriptionManager.Subscription subscription =
                subscriptionManager.subscribe(1L, observer);

        doThrow(new RuntimeException("Client disconnected"))
                .when(observer)
                .onNext(any(StockUpdate.class));

        subscriptionManager.publish(1L, 5);

        verify(observer).onNext(any(StockUpdate.class));

        // The failing subscriber should have been removed.
        reset(observer);

        subscriptionManager.publish(1L, 3);

        verifyNoInteractions(observer);
    }

    @Test
    void publish_shouldContinueSendingWhenOneSubscriberFails() {

        @SuppressWarnings("unchecked")
        StreamObserver<StockUpdate> failingObserver = mock(StreamObserver.class);

        @SuppressWarnings("unchecked")
        StreamObserver<StockUpdate> workingObserver = mock(StreamObserver.class);

        subscriptionManager.subscribe(1L, failingObserver);
        subscriptionManager.subscribe(1L, workingObserver);

        doThrow(new RuntimeException("Client disconnected"))
                .when(failingObserver)
                .onNext(any(StockUpdate.class));

        subscriptionManager.publish(1L, 5);

        verify(failingObserver).onNext(any(StockUpdate.class));

        verify(workingObserver).onNext(
                StockUpdate.newBuilder()
                        .setProductId(1L)
                        .setAvailableQuantity(5)
                        .build()
        );
    }

    // ==========================================
    // SUBSCRIPTION
    // ==========================================

    @Test
    void subscription_shouldNotSendUpdatesAfterDeactivation() {

        @SuppressWarnings("unchecked")
        StreamObserver<StockUpdate> observer = mock(StreamObserver.class);

        StockSubscriptionManager.Subscription subscription =
                new StockSubscriptionManager.Subscription(observer);

        subscription.deactivate();

        StockUpdate update = StockUpdate.newBuilder()
                .setProductId(1L)
                .setAvailableQuantity(5)
                .build();

        subscription.send(update);

        verifyNoInteractions(observer);
    }

    @Test
    void subscription_shouldSendUpdatesWhileActive() {

        @SuppressWarnings("unchecked")
        StreamObserver<StockUpdate> observer = mock(StreamObserver.class);

        StockSubscriptionManager.Subscription subscription =
                new StockSubscriptionManager.Subscription(observer);

        StockUpdate update = StockUpdate.newBuilder()
                .setProductId(1L)
                .setAvailableQuantity(5)
                .build();

        subscription.send(update);

        verify(observer).onNext(update);
    }

    @Test
    void subscription_shouldNotSendUpdatesAfterMultipleDeactivations() {

        @SuppressWarnings("unchecked")
        StreamObserver<StockUpdate> observer = mock(StreamObserver.class);

        StockSubscriptionManager.Subscription subscription =
                new StockSubscriptionManager.Subscription(observer);

        subscription.deactivate();
        subscription.deactivate();

        subscription.send(
                StockUpdate.newBuilder()
                        .setProductId(1L)
                        .setAvailableQuantity(5)
                        .build()
        );

        verifyNoInteractions(observer);
    }
}
