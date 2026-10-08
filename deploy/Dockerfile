FROM maven:3.9-eclipse-temurin-8 AS build
WORKDIR /workspace
COPY . .
RUN mvn -B -ntp -DskipTests package

FROM eclipse-temurin:8-jre
WORKDIR /app
COPY --from=build /workspace/temu-server/target/badfisher-temu.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
