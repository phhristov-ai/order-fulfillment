
import { useCallback, useEffect, useState } from "react";
import { Activity, RefreshCw, Package, ShoppingCart } from "lucide-react";

import {
    getProduct,
    checkStock,
    createOrder,
    reserveMultipleStocks
} from "../services/api";

import { useStockUpdates } from "../hooks/useStockUpdates";

import type {
    Product,
    StockCheckResponse,
    ReserveMultipleStocksResponse
} from "../types/inventory";

export default function Inventory() {
    const [products, setProducts] = useState<Product[]>([]);
    const [loading, setLoading] = useState(true);
    const [busy, setBusy] = useState(false);
    const [error, setError] = useState("");
    const [message, setMessage] = useState("");

    const [selectedProduct, setSelectedProduct] = useState(1);
    const [quantity, setQuantity] = useState(1);

    const [checkResult, setCheckResult] =
        useState<StockCheckResponse | null>(null);

    const [batchResult, setBatchResult] =
        useState<ReserveMultipleStocksResponse | null>(null);

    const [batchQuantities, setBatchQuantities] =
        useState<Record<number, number>>({
            1: 0,
            2: 0,
            3: 0
        });

    const loadProducts = useCallback(async () => {
        setLoading(true);
        setError("");

        try {
            const results = await Promise.all(
                [1, 2, 3].map(id => getProduct(id))
            );
            setProducts(results);
        } catch {
            setError("Could not load products.");
        } finally {
            setLoading(false);
        }
    }, []);

    useEffect(() => {
        void loadProducts();
    }, [loadProducts]);

    const { stock, connected } = useStockUpdates(selectedProduct);

    useEffect(() => {
        if (!stock) return;

        setProducts(previous =>
            previous.map(product =>
                product.productId === stock.productId
                    ? {
                        ...product,
                        availableQuantity: stock.availableQuantity
                    }
                    : product
            )
        );
    }, [stock]);

    async function refreshProduct(productId: number) {
        const updated = await getProduct(productId);

        setProducts(previous =>
            previous.map(product =>
                product.productId === productId ? updated : product
            )
        );
    }

    async function handleStockCheck() {
        setError("");
        setCheckResult(null);

        try {
            const result = await checkStock(selectedProduct, quantity);
            setCheckResult(result);
        } catch {
            setError("Stock check failed.");
        }
    }

    async function handleReservation() {
        setBusy(true);
        setError("");
        setMessage("");

        try {
            await createOrder(selectedProduct, quantity);
            setMessage("Order created and stock reserved successfully.");
            await refreshProduct(selectedProduct);
        } catch {
            setError("Reservation failed.");
        } finally {
            setBusy(false);
        }
    }

    async function handleBatchReservation() {
        const requests = Object.entries(batchQuantities)
            .filter(([, amount]) => amount > 0)
            .map(([id, amount]) => ({
                productId: Number(id),
                quantity: amount
            }));

        if (requests.length === 0) {
            setError("Enter a quantity for at least one product.");
            return;
        }

        setBusy(true);
        setError("");
        setBatchResult(null);

        try {
            const result = await reserveMultipleStocks(requests);
            setBatchResult(result);

            setProducts(previous =>
                previous.map(product => {
                    const reservation = result.results.find(
                        item => item.productId === product.productId
                    );

                    if (!reservation?.successful) return product;

                    return {
                        ...product,
                        availableQuantity: reservation.remainingQuantity
                    };
                })
            );

            setBatchQuantities({ 1: 0, 2: 0, 3: 0 });
        } catch {
            setError("Multiple reservation failed.");
        } finally {
            setBusy(false);
        }
    }

    const totalStock = products.reduce(
        (total, product) => total + product.availableQuantity,
        0
    );

    return (
        <div className="inventory-page">
            <div className="page-heading">
                <div>
                    <h1>Inventory</h1>
                    <p>Manage products and monitor stock levels.</p>
                </div>

                <button
                    className="secondary-button"
                    onClick={loadProducts}
                    disabled={loading}
                >
                    <RefreshCw size={16} />
                    Refresh
                </button>
            </div>

            {error && <div className="error-message">{error}</div>}

            <section className="stats-grid">
                <div className="stat-card">
                    <div className="stat-card-header">
                        Total Products <Package />
                    </div>
                    <div className="stat-value">{products.length}</div>
                </div>

                <div className="stat-card">
                    <div className="stat-card-header">
                        Available Units <ShoppingCart />
                    </div>
                    <div className="stat-value">{totalStock}</div>
                </div>

                <div className="stat-card">
                    <div className="stat-card-header">
                        Live Monitoring <Activity />
                    </div>
                    <div className="stat-value">
                        <span className={connected ? "live-text" : "offline-text"}>
                            {connected ? "Connected" : "Disconnected"}
                        </span>
                    </div>
                </div>
            </section>

            <section className="panel">
                <div className="panel-heading">
                    <div>
                        <h2>Products</h2>
                        <p>Current inventory levels</p>
                    </div>
                </div>

                {loading ? (
                    <p className="panel-padding">Loading products...</p>
                ) : (
                    <div className="table-wrapper">
                        <table>
                            <thead>
                                <tr>
                                    <th>Product</th>
                                    <th>ID</th>
                                    <th>Price</th>
                                    <th>Available</th>
                                    <th>Status</th>
                                    <th>Monitoring</th>
                                </tr>
                            </thead>
                            <tbody>
                                {products.map(product => (
                                    <tr key={product.productId}>
                                        <td className="product-name">{product.name}</td>
                                        <td>#{product.productId}</td>
                                        <td>€{product.price.toFixed(2)}</td>
                                        <td>{product.availableQuantity}</td>
                                        <td>
                                            <StockStatus quantity={product.availableQuantity} />
                                        </td>
                                        <td>
                                            <button
                                                className="small-button"
                                                onClick={() => setSelectedProduct(product.productId)}
                                            >
                                                {selectedProduct === product.productId
                                                    ? "Monitoring"
                                                    : "Monitor"}
                                            </button>
                                        </td>
                                    </tr>
                                ))}
                            </tbody>
                        </table>
                    </div>
                )}
            </section>

            <div className="inventory-actions">
                <section className="panel action-panel">
                    <div className="panel-heading">
                        <h2>Check Stock</h2>
                        <p>Check product availability.</p>
                    </div>

                    <div className="form-content">
                        <label>Product</label>
                        <select
                            value={selectedProduct}
                            onChange={e => {
                                setSelectedProduct(Number(e.target.value));
                                setCheckResult(null);
                            }}
                        >
                            {products.map(product => (
                                <option key={product.productId} value={product.productId}>
                                    {product.name}
                                </option>
                            ))}
                        </select>

                        <label>Quantity</label>
                        <input
                            type="number"
                            min="1"
                            value={quantity}
                            onChange={e => setQuantity(Math.max(1, Number(e.target.value)))}
                        />

                        <button
                            className="primary-button"
                            onClick={handleStockCheck}
                            disabled={busy || products.length === 0}
                        >
                            Check Availability
                        </button>

                        {checkResult && (
                            <div className={`result ${checkResult.available ? "success-result" : "warning-result"}`}>
                                {checkResult.available
                                    ? "Requested quantity is available."
                                    : "Insufficient stock."}
                                <p>Available: {checkResult.availableQuantity}</p>
                            </div>
                        )}
                    </div>
                </section>

                <section className="panel action-panel">
                    <div className="panel-heading">
                        <h2>Reserve Stock</h2>
                        <p>Create an order for one product.</p>
                    </div>

                    <div className="form-content">
                        <label>Product</label>
                        <select
                            value={selectedProduct}
                            onChange={e => setSelectedProduct(Number(e.target.value))}
                        >
                            {products.map(product => (
                                <option key={product.productId} value={product.productId}>
                                    {product.name}
                                </option>
                            ))}
                        </select>

                        <label>Quantity</label>
                        <input
                            type="number"
                            min="1"
                            value={quantity}
                            onChange={e => setQuantity(Math.max(1, Number(e.target.value)))}
                        />

                        <button
                            className="primary-button"
                            onClick={handleReservation}
                            disabled={busy || products.length === 0}
                        >
                            {busy ? "Processing..." : "Reserve Stock"}
                        </button>

                        {message && (
                            <div className="result success-result">{message}</div>
                        )}
                    </div>
                </section>
            </div>

            <section className="panel batch-panel">
                <div className="panel-heading">
                    <h2>Multiple Stock Reservation</h2>
                    <p>Reserve several products using client-streaming gRPC.</p>
                </div>

                <div className="form-content">
                    {products.map(product => (
                        <div className="batch-row" key={product.productId}>
                            <div>
                                <strong>{product.name}</strong>
                                <small>Available: {product.availableQuantity}</small>
                            </div>

                            <input
                                type="number"
                                min="0"
                                value={batchQuantities[product.productId] ?? 0}
                                onChange={e =>
                                    setBatchQuantities(previous => ({
                                        ...previous,
                                        [product.productId]: Math.max(0, Number(e.target.value))
                                    }))
                                }
                            />
                        </div>
                    ))}

                    <button
                        className="primary-button"
                        onClick={handleBatchReservation}
                        disabled={busy || products.length === 0}
                    >
                        {busy ? "Processing..." : "Reserve Multiple Products"}
                    </button>

                    {batchResult && (
                        <div className="result success-result">
                            <strong>Reservation Summary</strong>
                            <p>Successful: {batchResult.successfulReservations}</p>
                            <p>Failed: {batchResult.failedReservations}</p>

                            {batchResult.results.map(result => (
                                <div className="batch-result-row" key={result.productId}>
                                    Product #{result.productId}:{" "}
                                    {result.successful
                                        ? `Reserved. Remaining: ${result.remainingQuantity}`
                                        : result.errorMessage}
                                </div>
                            ))}
                        </div>
                    )}
                </div>
            </section>
        </div>
    );
}

function StockStatus({ quantity }: { quantity: number }) {
    if (quantity <= 5) {
        return <span className="badge warning">Low stock</span>;
    }

    return <span className="badge success">In stock</span>;
}
