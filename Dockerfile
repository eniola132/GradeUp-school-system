# Build the Spring Boot application in an isolated Java 17 environment.
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /workspace

COPY pom.xml ./
COPY .mvn .mvn
COPY mvnw ./
RUN chmod +x mvnw && ./mvnw -B dependency:go-offline

COPY src src
RUN ./mvnw -B -DskipTests package

# Run only the packaged application in the final image.
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /workspace/target/gradeup-1.0.0.jar /app/gradeup.jar

EXPOSE 10000
CMD ["sh", "-c", "exec java -Dserver.port=${PORT:-8081} -jar /app/gradeup.jar"]
