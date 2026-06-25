# ============================
# ETAPA 1: BUILD (Maven)
# ============================
FROM maven:3.9.2-eclipse-temurin-17 AS builder

WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -B
COPY src ./src
RUN mvn package -Dmaven.test.skip=true

# ============================
# ETAPA 2: IMAGEM FINAL (JRE leve)
# ============================
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app
COPY --from=builder /app/target/*.jar app.jar
EXPOSE 8088
ENTRYPOINT ["sh", "-c", "java -jar app.jar --server.port=${PORT:-8088}"]