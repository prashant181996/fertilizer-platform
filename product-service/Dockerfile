FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY . .
RUN mvn -q -pl product-service -am -DskipTests package
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /workspace/product-service/target/product-service-1.0.0.jar app.jar
EXPOSE 8081
ENTRYPOINT ["java","-XX:MaxRAMPercentage=75.0","-jar","/app/app.jar"]
