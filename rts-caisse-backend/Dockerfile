# =============================================================
#  RTS Caisse - Backend Spring Boot
# =============================================================

# ---------- Stage 1 : Build ----------
FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /build

COPY pom.xml .
RUN mvn dependency:go-offline -B

COPY src ./src
RUN mvn clean package -DskipTests -Dmaven.test.skip=true -B

# ---------- Stage 2 : Runtime ----------
FROM eclipse-temurin:21-jre-alpine

# wget pour le healthcheck + timezone
RUN apk add --no-cache wget tzdata && \
    cp /usr/share/zoneinfo/Africa/Dakar /etc/localtime && \
    echo "Africa/Dakar" > /etc/timezone && \
    apk del tzdata

RUN addgroup -S rts && adduser -S rts -G rts

WORKDIR /app
COPY --from=build /build/target/*.jar app.jar
RUN chown -R rts:rts /app
USER rts

EXPOSE 9090

# Healthcheck via /actuator/health (route publique, dédiée à ça)
# Important : SLASH INITIAL obligatoire dans l'URL
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD wget -q --spider http://localhost:9090/actuator/health || exit 1

ENTRYPOINT ["sh", "-c", "java \
    -XX:MaxRAMPercentage=75.0 \
    -Djava.security.egd=file:/dev/./urandom \
    -Duser.timezone=Africa/Dakar \
    -jar app.jar"]
