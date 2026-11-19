
import { useEffect, useMemo, useState } from "react";

import {
    getProduct,
    processOrderItems
} from "../services/api";

import { useOrderUpdates } from "../hooks/useOrderUpdates";

import type {
    OrderItemRequest,
    OrderItemResponse,
    OrderProduct
} from "../types/order";

export default function OrderProcessing() {
    const [products, setProducts] = useState<OrderProduct[]>([]);
    const [loading, setLoading] = useState(true);

    const [orderId, setOrderId] = useState(1002);
    const [selectedProduct, setSelectedProduct] = useState(1);
    const [quantity, setQuantity] = useState(1);

    const [items, setItems] = useState<OrderItemRequest[]>([]);
    const [results, setResults] = useState<OrderItemResponse[]>([]);

    const [processing, setProcessing] = useState(false);
    const [error, setError] = useState("");
    const [completed, setCompleted] = useState(false);

    const { connected, subscribeToOrder } = useOrderUpdates();

    useEffect(() => {
        async function loadProducts() {
            try {
                const loaded = await Promise.all(
                    [1, 2, 3].map(id => getProduct(id))
                );

                setProducts(loaded);
            } catch (err) {
                console.error(err);
                setError("Could not load products.");
            } finally {
                setLoading(false);
            }
        }

        void loadProducts();
    }, []);

    const productMap = useMemo(
        () => new Map(products.map(product => [
            product.productId,
            product
        ])),
        [products]
    );

    const successfulCount = results.filter(
        result => result.successful
    ).length;

    const failedCount = results.filter(
        result => !result.successful
    ).length;

    function addItem() {
        if (quantity < 1 || !Number.isInteger(quantity)) {
            setError("Quantity must be at least 1.");
            return;
        }

        setError("");

        setItems(previous => {
            const existing = previous.find(
                item => item.productId === selectedProduct
            );

            if (existing) {
                return previous.map(item =>
                    item.productId === selectedProduct
                        ? {
                            ...item,
                            quantity: item.quantity + quantity
                        }
                        : item
                );
            }

            return [
                ...previous,
                {
                    productId: selectedProduct,
                    quantity
                }
            ];
        });

        setQuantity(1);
    }

    function removeItem(productId: number) {
        setItems(previous =>
            previous.filter(item => item.productId !== productId)
        );
    }

    async function handleSubmit() {
        if (items.length === 0) {
            setError("Add at least one product.");
            return;
        }

        if (!Number.isInteger(orderId) || orderId <= 0) {
            setError("Enter a valid order ID.");
            return;
        }

        if (!connected) {
            setError("WebSocket is not connected yet. Please wait.");
            return;
        }

        setError("");
        setResults([]);
        setCompleted(false);
        setProcessing(true);

        // Subscribe before submitting the REST request.
        const subscription = subscribeToOrder(
            orderId,
            response => {
                setResults(previous => {
                    const existing = previous.findIndex(
                        item => item.productId === response.productId
                    );

                    if (existing === -1) {
                        return [...previous, response];
                    }

                    return previous.map((item, index) =>
                        index === existing ? response : item
                    );
                });
            }
        );

        if (!subscription) {
            setProcessing(false);
            setError("Could not subscribe to order updates.");
            return;
        }

        try {
            // REST starts the gRPC bidirectional stream.
            // WebSocket delivers individual results as they arrive.
            const finalResults = await processOrderItems(
                orderId,
                items
            );

            // Use the complete REST response as the final source of truth.
            setResults(finalResults);
            setCompleted(true);

            // Refresh product quantities.
            const updatedProducts = await Promise.all(
                products.map(product => getProduct(product.productId))
            );

            setProducts(updatedProducts);

        } catch (err) {
            console.error("Order processing failed:", err);
            setError("Order processing failed. Check the backend logs.");
        } finally {
            subscription.unsubscribe();
            setProcessing(false);
        }
    }

    return (
        <div className="order-processing-page">

            <div className="page-heading">
                <div>
                    <h1>Order Processing</h1>
                    <p>
                        Process multiple order items using bidirectional
                        gRPC streaming.
                    </p>
                </div>

                <span className={connected ? "badge success" : "badge warning"}>
                    {connected ? "WebSocket Connected" : "Connecting..."}
                </span>
            </div>

            {error && (
                <div className="error-message">
                    {error}
                </div>
            )}

            <section className="panel">
                <div className="panel-heading">
                    <h2>Order Information</h2>
                    <p>Configure your order before processing.</p>
                </div>

                <div className="form-content">
                    <label>Order ID</label>
                    <input
                        type="number"
                        min="1"
                        value={orderId}
                        disabled={processing}
                        onChange={e =>
                            setOrderId(Number(e.target.value))
                        }
                    />
                </div>
            </section>

            <section className="panel">
                <div className="panel-heading">
                    <h2>Add Order Items</h2>
                    <p>Select products and quantities.</p>
                </div>

                <div className="form-content">
                    {loading ? (
                        <p>Loading products...</p>
                    ) : (
                        <>
                            <label>Product</label>
                            <select
                                value={selectedProduct}
                                disabled={processing}
                                onChange={e =>
                                    setSelectedProduct(Number(e.target.value))
                                }
                            >
                                {products.map(product => (
                                    <option
                                        key={product.productId}
                                        value={product.productId}
                                    >
                                        {product.name} - Available:{" "}
                                        {product.availableQuantity}
                                    </option>
                                ))}
                            </select>

                            <label>Quantity</label>
                            <input
                                type="number"
                                min="1"
                                value={quantity}
                                disabled={processing}
                                onChange={e =>
                                    setQuantity(Number(e.target.value))
                                }
                            />

                            <button
                                className="secondary-button"
                                onClick={addItem}
                                disabled={processing || products.length === 0}
                            >
                                Add Product
                            </button>
                        </>
                    )}
                </div>
            </section>

            <section className="panel">
                <div className="panel-heading">
                    <h2>Order Items</h2>
                    <p>{items.length} different products added.</p>
                </div>

                {items.length === 0 ? (
                    <p className="panel-padding">
                        No products added yet.
                    </p>
                ) : (
                    <div className="table-wrapper">
                        <table>
                            <thead>
                                <tr>
                                    <th>Product</th>
                                    <th>Available</th>
                                    <th>Quantity</th>
                                    <th>Action</th>
                                </tr>
                            </thead>

                            <tbody>
                                {items.map(item => {
                                    const product = productMap.get(
                                        item.productId
                                    );

                                    return (
                                        <tr key={item.productId}>
                                            <td>
                                                {product?.name ??
                                                    `Product ${item.productId}`}
                                            </td>
                                            <td>
                                                {product?.availableQuantity ?? "-"}
                                            </td>
                                            <td>{item.quantity}</td>
                                            <td>
                                                <button
                                                    className="small-button"
                                                    disabled={processing}
                                                    onClick={() =>
                                                        removeItem(item.productId)
                                                    }
                                                >
                                                    Remove
                                                </button>
                                            </td>
                                        </tr>
                                    );
                                })}
                            </tbody>
                        </table>
                    </div>
                )}

                <div className="form-content">
                    <button
                        className="primary-button"
                        onClick={handleSubmit}
                        disabled={
                            processing ||
                            items.length === 0 ||
                            !connected
                        }
                    >
                        {processing
                            ? "Processing Order..."
                            : "Submit Order"}
                    </button>
                </div>
            </section>

            <section className="panel">
                <div className="panel-heading">
                    <h2>Processing Results</h2>
                    <p>
                        Individual responses received from Inventory Service.
                    </p>
                </div>

                <div className="stats-grid">
                    <div className="stat-card">
                        <div className="stat-card-header">
                            Successful
                        </div>
                        <div className="stat-value live-text">
                            {successfulCount}
                        </div>
                    </div>

                    <div className="stat-card">
                        <div className="stat-card-header">
                            Failed
                        </div>
                        <div className="stat-value offline-text">
                            {failedCount}
                        </div>
                    </div>

                    <div className="stat-card">
                        <div className="stat-card-header">
                            Received
                        </div>
                        <div className="stat-value">
                            {results.length}/{items.length}
                        </div>
                    </div>
                </div>

                {processing && (
                    <div className="panel-padding">
                        Waiting for streaming responses...
                    </div>
                )}

                {completed && (
                    <div className="result success-result">
                        Order processing completed.
                    </div>
                )}

                {results.length > 0 && (
                    <div className="table-wrapper">
                        <table>
                            <thead>
                                <tr>
                                    <th>Product</th>
                                    <th>Status</th>
                                    <th>Remaining Stock</th>
                                    <th>Message</th>
                                </tr>
                            </thead>

                            <tbody>
                                {results.map(result => (
                                    <tr key={result.productId}>
                                        <td>
                                            {productMap.get(result.productId)?.name ??
                                                `Product ${result.productId}`}
                                        </td>
                                        <td>
                                            <span
                                                className={
                                                    result.successful
                                                        ? "badge success"
                                                        : "badge warning"
                                                }
                                            >
                                                {result.successful
                                                    ? "Successful"
                                                    : "Failed"}
                                            </span>
                                        </td>
                                        <td>{result.remainingQuantity}</td>
                                        <td>{result.message}</td>
                                    </tr>
                                ))}
                            </tbody>
                        </table>
                    </div>
                )}
            </section>
        </div>
    );
}
