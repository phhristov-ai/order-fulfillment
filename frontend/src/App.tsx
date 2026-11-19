import { BrowserRouter, Routes, Route } from "react-router-dom";

import Layout from "./components/Layout";
import Dashboard from "./pages/Dashboard";
import Inventory from "./pages/Inventory";
import OrderProcessing from "./pages/OrderProcessing";

function App() {

    return (
        <BrowserRouter>
            <Routes>

                <Route element={<Layout />}>

                    <Route path="/" element={<Dashboard />} />

                    <Route
                        path="/inventory"
                        element={<Inventory />}
                    />

                    <Route
                        path="/orders"
                        element={<OrderProcessing />}
                    />

                </Route>

            </Routes>
        </BrowserRouter>
    );
}

export default App;