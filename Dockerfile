# ============================================
# Etapa 1: Build con Maven
# ============================================
FROM maven:3.9-eclipse-temurin-11-alpine AS builder

WORKDIR /build

# Copiar solo archivos de dependencias primero (mejor caché)
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copiar código fuente y compilar
COPY src ./src
RUN mvn package -DskipTests -B

# Extraer capas del JAR (Spring Boot Layered JAR)
RUN java -Djarmode=layertools -jar target/demo-0.0.1-SNAPSHOT.jar extract

# ============================================
# Etapa 2: Imagen final mínima para ejecución
# ============================================
# Base con CVEs conocidos (1) - para que Trivy las detecte en el lab
FROM openjdk:11.0.11-jre-slim-buster

# Paquetes con CVEs conocidos (2 y 3) - solo para detección Trivy, no usados por la app
RUN apt-get update && apt-get install -y --no-install-recommends \
    curl \
    git \
    && rm -rf /var/lib/apt/lists/*

# Usuario no-root para seguridad
RUN groupadd -g 1000 appgroup && \
    useradd -u 1000 -g appgroup -m -s /bin/bash appuser

WORKDIR /app

# Copiar capas en orden óptimo (dependencias cambian menos → mejor caché)
COPY --from=builder /build/dependencies/ ./
COPY --from=builder /build/spring-boot-loader/ ./
COPY --from=builder /build/snapshot-dependencies/ ./
COPY --from=builder /build/application/ ./

USER appuser

EXPOSE 8080

ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
