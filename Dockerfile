# ============================================
# Etapa 1: Build con Maven
# ============================================
FROM maven:3.9-eclipse-temurin-11-alpine AS builder

WORKDIR /build

# Copiar solo archivos de dependencias primero (mejor caché)

# [Checkov - Error 1] Usar COPY en lugar de ADD (CKV_DOCKER_4)
ADD pom.xml .
RUN mvn dependency:go-offline -B

# Copiar código fuente y compilar
COPY src ./src
RUN mvn package -DskipTests -B

# Extraer capas del JAR (Spring Boot Layered JAR)
RUN java -Djarmode=layertools -jar target/demo-0.0.1-SNAPSHOT.jar extract

# ============================================
# Etapa 2: Imagen final mínima para ejecución
# ============================================
# [Trivy - Error 1] Base antigua con CVEs conocidos
FROM eclipse-temurin:11-jre-focal

# [Trivy - Error 2] Paquete con CVEs - solo para que Trivy lo detecte, la app no lo usa
RUN apt-get update && apt-get install -y --no-install-recommends curl \
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

# [Checkov - Error 2] Falta HEALTHCHECK (CKV_DOCKER_2)

ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
