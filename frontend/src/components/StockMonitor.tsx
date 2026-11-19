import { useStockUpdates } from "../hooks/useStockUpdates";

interface Props {
    productId: number;
}

function StockMonitor({ productId }: Props) {

    const { stock, connected } = useStockUpdates(productId);

    return (
        <div>
            <h2>Stock Monitor</h2>

            <p>
                Connection: {connected ? "Connected" : "Disconnected"}
            </p>

            <p>Product ID: {productId}</p>

            {stock && (
                <div>
                    <h3>Latest Stock Update</h3>

                    <p>
                        Available quantity: {stock.availableQuantity}
                    </p>
                </div>
            )}
        </div>
    );
}

export default StockMonitor;