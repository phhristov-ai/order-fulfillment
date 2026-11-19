Order Fulfillment – Microservices with Spring Boot and gRPC

A microservices-based order fulfillment application built with Spring Boot, gRPC, Protocol Buffers, React, and Docker Compose.

The project demonstrates how multiple services communicate using gRPC, how REST APIs expose business functionality to a frontend, and how different gRPC communication patterns can be used in a real-world business workflow.

Architecture

The application consists of four main modules:

Module	Technology	Responsibility
inventory-api	Protocol Buffers, gRPC	Shared service contract and generated Java classes
inventory-service	Spring Boot, gRPC	Product information, stock checks, and reservations
order-service	Spring Boot, REST, gRPC	Order processing and communication with Inventory Service
frontend	React, TypeScript, Vite	User interface for inventory and order management
Communication
The React frontend communicates with Order Service through HTTP REST and WebSockets.
Order Service communicates with Inventory Service through gRPC.
Inventory Service publishes stock updates through server-side streaming.
Protocol Buffers define the contract shared between the gRPC client and server.
Architecture diagram
text
                   React Frontend
                     (Port 3000)
                         |
                  REST / WebSocket
                         |
                         v
                    Order Service
                     (Port 8080)
                         |
                       gRPC
                         |
                         v
                  Inventory Service
                     (Port 9090)
                         |
                         v
                   Product Repository

Technologies

Backend

Java 21
Spring Boot
Spring gRPC
gRPC
Protocol Buffers
Maven

Frontend

React
TypeScript
Vite

Infrastructure

Docker
Docker Compose
Nginx
Getting Started
Prerequisites

Install the following:

JDK 21
Maven
Node.js and npm (for local frontend development)
Docker Desktop with Docker Compose
Running the application with Docker Compose

The easiest way to start the entire application is using Docker Compose.

From the project root, execute:

bash
docker compose up --build


This builds and starts all three applications.

The applications will be available at:

Application	URL
React Frontend	http://localhost:3000
Order Service	http://localhost:8080
Swagger UI	http://localhost:8080/swagger-ui/index.html
Inventory gRPC Server	localhost:9090

To stop the application:

bash
docker compose down


To rebuild the applications:

bash
docker compose up --build -d

Running individual services locally

The applications can also be started independently.

Build the Maven modules from the project root:

bash
mvn clean install


Start Inventory Service:

bash
cd inventory-service
mvn spring-boot:run


Start Order Service in another terminal:

bash
cd order-service
mvn spring-boot:run


Start the frontend:

bash
cd frontend
npm install
npm run dev


When running locally, Order Service connects to Inventory Service through localhost:9090.

When running inside Docker Compose, it connects through the Docker service hostname inventory-service:9090.

Project Functionality
Inventory Service

Inventory Service exposes a gRPC API defined in inventory.proto.

Implemented operations:

Get product information.
Check product availability.
Reserve stock.
Watch stock changes using server-side streaming.
Reserve multiple products using client-side streaming.
Process order items using bidirectional or client-streaming communication patterns, depending on the RPC implementation.
Order Service

Order Service exposes REST endpoints and acts as a gRPC client.

Implemented functionality:

Retrieve product information.
Check stock availability.
Create orders.
Reserve multiple products.
Process multiple order items.
Subscribe to stock updates and forward them to frontend clients through WebSockets.
Frontend

The React application provides an interface for:

Viewing inventory.
Checking stock availability.
Reserving products.
Performing batch reservations.
Processing orders.
Monitoring stock updates.
Testing

The project includes unit tests and integration tests.

The tests cover:

Inventory gRPC service methods.
Stock subscription management.
gRPC client-server communication.
Order REST controllers.
Order processing functionality.

Run all Maven tests from the project root:

bash
mvn test


Build all modules:

bash
mvn clean install

Learning Roadmap

The project is also intended as a practical learning exercise for understanding gRPC and microservice communication.

Stage 1 – Create a basic gRPC server
Create Inventory Service.
Define inventory.proto.
Generate Java classes.
Implement GetProduct.
Test it using grpcurl.

Goal: Understand the basics of gRPC and Protocol Buffers.

Stage 2 – Implement the gRPC client
Create Order Service.
Import generated classes.
Configure a gRPC channel.
Create a blocking stub.
Call GetProduct from Order Service.

Goal: Understand how a gRPC client communicates with a remote service.

Stage 3 – Implement business functionality
Add CheckStock.
Add ReserveStock.
Implement POST /orders.
Handle unsuccessful stock reservations.
Introduce business-level error handling.

Goal: Understand how gRPC fits into a real business workflow.

Stage 4 – Experiment with failures
Stop Inventory Service.
Make Order Service call an unavailable service.
Configure deadlines.
Handle gRPC status codes.
Experiment with connection failures.

Goal: Understand that remote procedure calls are not the same as local method calls.

Stage 5 – Server-side streaming

Implement:

protobuf
rpc WatchStock(ProductRequest)
    returns (stream StockUpdate);


Whenever stock changes, the server sends an update to subscribed clients.

Goal: Understand server-side streaming and asynchronous event notifications.

Stage 6 – Protobuf contract evolution
Add fields to Protobuf messages.
Regenerate Java classes.
Experiment with adding new RPC methods.
Change message structures.
Test compatibility between different contract versions.

Goal: Understand Protobuf compatibility and contract evolution.

Stage 7 – Client-side streaming

Implement an RPC where the client sends multiple messages and the server returns a single response.

Example:

protobuf
rpc ReserveMultipleStocks(stream ReserveStockRequest)
    returns (ReserveMultipleStocksResponse);


Goal: Understand client-side streaming and batch processing.

Stage 8 – Bidirectional streaming

Implement an RPC where both client and server exchange multiple messages independently.

Example:

protobuf
rpc ProcessOrderStream(stream OrderItemRequest)
    returns (stream OrderItemResponse);


Goal: Understand bidirectional streaming and asynchronous communication.

Stage 9 – Advanced gRPC reliability

Explore:

Deadlines and timeouts.
Cancellation propagation.
Retry policies.
Keepalive.
Graceful shutdown.
Channel connectivity.
Flow control.
Exponential backoff.

Goal: Understand how to build reliable communication between distributed services.

Future Improvements

Potential extensions:

Add persistent database storage.
Introduce authentication and authorization.
Add centralized configuration.
Implement service health checks.
Add observability with distributed tracing and metrics.
Introduce automated CI/CD pipelines.
Add integration tests using a fully containerized environment.
License

This project is intended for educational purposes and experimentation with microservices, Spring Boot, and gRPC.