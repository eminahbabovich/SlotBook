FROM eclipse-temurin:21-jdk AS build
WORKDIR /app

# pom prije izvornog koda: zavisnosti se skidaju samo kad se pom promijeni
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN ./mvnw -B dependency:go-offline

COPY src src
# testovi se vrte u CI-ju, a unutar docker builda nema Dockera za Testcontainers
RUN ./mvnw -B -DskipTests package


FROM eclipse-temurin:21-jre
RUN useradd --system --no-create-home --shell /usr/sbin/nologin app
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar

USER app
EXPOSE 8080
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75"
ENTRYPOINT ["java", "-jar", "app.jar"]
