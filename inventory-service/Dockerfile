# Build stage
FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /workspace

COPY pom.xml .
COPY inventory-api inventory-api
COPY inventory-service inventory-service
COPY order-service order-service

RUN mvn -pl inventory-service -am clean package -DskipTests

# Runtime stage
FROM eclipse-temurin:21-jre

WORKDIR /app

COPY --from=build /workspace/inventory-service/target/*.jar app.jar

EXPOSE 9090

ENTRYPOINT ["java", "-jar", "app.jar"]
