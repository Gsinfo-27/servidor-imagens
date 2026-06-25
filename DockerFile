# ============================
# ETAPA 1: BUILD (Maven)
# ============================
FROM maven:3.9.2-eclipse-temurin-17 AS builder

WORKDIR /app

# Copia apenas o pom.xml para cache de dependências
COPY pom.xml .

# Baixa dependências sem compilar o código
RUN mvn dependency:go-offline -B

# Copia o código-fonte
COPY src ./src

# Compila e empacota o JAR (skip tests)
RUN mvn package -DskipTests

# ============================
# ETAPA 2: IMAGEM FINAL (JRE leve)
# ============================
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

# Copia o JAR construído na etapa anterior
COPY --from=builder /app/target/*.jar app.jar

# Expõe a porta da sua aplicação
EXPOSE 8088

# Comando para iniciar a API com porta dinâmica
ENTRYPOINT ["sh", "-c", "java -jar app.jar --server.port=${PORT:-8088}"]