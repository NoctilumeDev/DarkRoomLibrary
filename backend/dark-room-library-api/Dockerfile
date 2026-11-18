FROM maven:3.9.9-eclipse-temurin-17 AS maven-dist

FROM eclipse-temurin:25.0.5_8-jdk-noble AS build

# Keep the Maven distribution fixed while updating the Java toolchain.
COPY --from=maven-dist /usr/share/maven /usr/share/maven
ENV PATH="/usr/share/maven/bin:${PATH}"

WORKDIR /workspace
COPY pom.xml .
RUN mvn -B -ntp dependency:go-offline

COPY src ./src
RUN mvn -B -ntp -DskipTests package

FROM eclipse-temurin:25.0.5_8-jre-alpine

RUN addgroup -S app \
    && adduser -S app -G app \
    && mkdir -p /app/upload/pic \
    && chown -R app:app /app

WORKDIR /app
COPY --from=build --chown=app:app /workspace/target/dark-room-library-api-*.jar /app/app.jar

USER app
EXPOSE 20606

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
