import { NavLink, Outlet } from "react-router-dom";
import {
    LayoutDashboard,
    Package,
    ShoppingCart,
    Boxes
} from "lucide-react";

const navigation = [
    {
        name: "Dashboard",
        path: "/",
        icon: LayoutDashboard,
    },
    {
        name: "Inventory",
        path: "/inventory",
        icon: Package,
    },
    {
        name: "Order Processing",
        path: "/orders",
        icon: ShoppingCart,
    },
];

export default function Layout() {
    return (
        <div className="app-layout">

            <aside className="sidebar">

                <div className="brand">
                    <Boxes size={25} />
                    <span>Order Fulfillment</span>
                </div>

                <div className="nav-label">
                    MAIN MENU
                </div>

                <nav>
                    {navigation.map((item) => {
                        const Icon = item.icon;

                        return (
                            <NavLink
                                key={item.path}
                                to={item.path}
                                end={item.path === "/"}
                                className={({ isActive }) =>
                                    isActive ? "nav-item active" : "nav-item"
                                }
                            >
                                <Icon size={19} />
                                <span>{item.name}</span>
                            </NavLink>
                        );
                    })}
                </nav>

                <div className="sidebar-footer">
                    <span className="status-dot">
                        Services available
                    </span>
                </div>

            </aside>

            <main className="main-content">

                <header className="topbar">
                    <span>Inventory Management System</span>
                    <span className="environment">
                        LOCAL DEVELOPMENT
                    </span>
                </header>

                <div className="page-content">
                    <Outlet />
                </div>

            </main>
        </div>
    );
}