# =============================================================
#  RTS Caisse - Backend Spring Boot 3.2 (multi-stage build)
# =============================================================
#  Build :  docker build -t vnsu/rts-caisse-backend:latest .
#  Depuis : rts-caisse-backend/   (la racine du projet Spring)
# =============================================================

# ==============================
#  STAGE 1 : compilation Maven
# ==============================
FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /build

# Cache des dépendances : on copie d'abord pom.xml, on télécharge
# les deps, PUIS on copie le code source. Ainsi un changement de
# code n'invalide pas le téléchargement des deps.
COPY pom.xml ./
RUN mvn -B -e -ntp dependency:go-offline

COPY src ./src
RUN mvn -B -e -ntp clean package -DskipTests \
    && mv target/*.jar target/app.jar


# ==============================
#  STAGE 2 : runtime JRE seul
# ==============================
FROM eclipse-temurin:21-jre-alpine

# Outils minimaux pour le healthcheck (wget) + timezone Dakar
RUN apk add --no-cache wget tzdata \
 && cp /usr/share/zoneinfo/Africa/Dakar /etc/localtime \
 && echo "Africa/Dakar" > /etc/timezone

# Utilisateur non-root (sécurité)
RUN addgroup -S spring && adduser -S spring -G spring
WORKDIR /app

# Copie du jar depuis le stage build
COPY --from=build --chown=spring:spring /build/target/app.jar /app/app.jar

USER spring

# Le profil "docker" est activé via SPRING_PROFILES_ACTIVE
# Le port doit être cohérent avec gateway/default.conf et le healthcheck.
EXPOSE 9090

# Healthcheck : Spring Boot Actuator doit être actif
# (dépendance spring-boot-starter-actuator + management.endpoints.web.exposure.include=health)
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=5 \
    CMD wget -q --spider http://localhost:9090/actuator/health || exit 1

# Options JVM raisonnables pour conteneur :
#   - UseContainerSupport : la JVM lit les limites cgroup (pas la RAM hôte)
#   - MaxRAMPercentage : utilise jusqu'à 75% de la mémoire allouée
ENTRYPOINT ["java", \
    "-XX:+UseContainerSupport", \
    "-XX:MaxRAMPercentage=75.0", \
    "-Djava.security.egd=file:/dev/./urandom", \
    "-jar", "/app/app.jar"]
