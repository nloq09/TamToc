FROM maven:3.9.11-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
COPY src src
RUN --mount=type=cache,target=/root/.m2 mvn -B -Dmaven.wagon.http.retryHandler.count=3 package
FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S game && adduser -S game -G game
WORKDIR /app
COPY --from=build /app/target/server-0.1.0.jar app.jar
USER game
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
