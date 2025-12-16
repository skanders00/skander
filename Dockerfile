# Use Eclipse Temurin for Java 17 (Lightweight Alpine Linux)
FROM eclipse-temurin:17-jdk-alpine

WORKDIR /app

# Copy the built jar
COPY target/*.jar app.jar

# Expose port 8089 (Matches your application.properties)
EXPOSE 8089

ENTRYPOINT ["java", "-jar", "app.jar"]