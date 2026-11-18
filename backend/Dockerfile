FROM eclipse-temurin:21-jdk AS build
WORKDIR /src

COPY gradlew build.gradle settings.gradle ./
COPY gradle/ ./gradle/
COPY src/ ./src/
RUN chmod +x gradlew && ./gradlew --no-daemon bootJar

FROM eclipse-temurin:21-jre
WORKDIR /app

COPY --from=build /src/build/libs/*.jar ./pulso.jar
EXPOSE 10000
CMD ["java", "-jar", "/app/pulso.jar"]
