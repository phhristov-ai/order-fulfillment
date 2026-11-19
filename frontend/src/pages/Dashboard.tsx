import { useEffect, useState } from "react";
import {
    Package,
    Boxes,
    AlertTriangle,
    Activity
} from "lucide-react";

import { getProduct } from "../services/api";
import type { Product } from "../types/inventory";

export default function Dashboard() {

    const [products, setProducts] = useState<Product[]>([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState("");

    useEffect(() => {

        async function loadProducts() {

            try {
                const productIds = [1, 2, 3];

                const results = await Promise.all(
                    productIds.map(id => getProduct(id))
                );

                setProducts(results);

            } catch {
                setError("Could not load inventory from Order Service.");
            } finally {
                setLoading(false);
            }
        }

        void loadProducts();

    }, []);

    const totalStock = products.reduce(
        (sum, product) => sum + product.availableQuantity,
        0
    );

    const lowStock = products.filter(
        product => product.availableQuantity <= 5
    ).length;

    if (loading) {
        return <p>Loading inventory...</p>;
    }

    if (error) {
        return <div className="error-message">{error}</div>;
    }

    return (
        <div>

            <div className="page-heading">
                <div>
                    <h1>Dashboard</h1>
                    <p>Overview of your inventory and stock levels.</p>
                </div>
            </div>

            <section className="stats-grid">

                <StatCard
                    title="Total Products"
                    value={products.length}
                    icon={<Package />}
                />

                <StatCard
                    title="Available Units"
                    value={totalStock}
                    icon={<Boxes />}
                />

                <StatCard
                    title="Low Stock Products"
                    value={lowStock}
                    icon={<AlertTriangle />}
                />

                <StatCard
                    title="Inventory Status"
                    value="Online"
                    icon={<Activity />}
                />

            </section>

            <section className="panel">

                <div className="panel-heading">
                    <div>
                        <h2>Inventory Overview</h2>
                        <p>Current stock across your products</p>
                    </div>
                </div>

                <div className="table-wrapper">

                    <table>
                        <thead>
                            <tr>
                                <th>Product</th>
                                <th>Product ID</th>
                                <th>Price</th>
                                <th>Available Quantity</th>
                                <th>Status</th>
                            </tr>
                        </thead>

                        <tbody>
                            {products.map(product => (

                                <tr key={product.productId}>
                                    <td className="product-name">
                                        {product.name}
                                    </td>

                                    <td>#{product.productId}</td>

                                    <td>
                                        €{product.price.toFixed(2)}
                                    </td>

                                    <td>
                                        {product.availableQuantity}
                                    </td>

                                    <td>
                                        <StockStatus
                                            quantity={product.availableQuantity}
                                        />
                                    </td>
                                </tr>

                            ))}
                        </tbody>
                    </table>

                </div>

            </section>

        </div>
    );
}

function StatCard({
    title,
    value,
    icon
}: Readonly<{
    title: string;
    value: string | number;
    icon: React.ReactNode;
}>) {

    return (
        <div className="stat-card">

            <div className="stat-card-header">
                <span>{title}</span>
                {icon}
            </div>

            <div className="stat-value">
                {value}
            </div>

        </div>
    );
}

function StockStatus({ quantity }: Readonly<{ quantity: number }>) {

    if (quantity <= 5) {
        return <span className="badge warning">Low stock</span>;
    }

    return <span className="badge success">In stock</span>;
}