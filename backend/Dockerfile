FROM maven:3.9.11-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
COPY src src
RUN mvn -B package
FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S game && adduser -S game -G game
WORKDIR /app
COPY --from=build /app/target/server-0.1.0.jar app.jar
USER game
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
