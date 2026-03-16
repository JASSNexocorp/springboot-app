# ============================================
# Etapa 1: Build con Maven
# ============================================
FROM maven:3.9-eclipse-temurin-11-alpine AS builder

WORKDIR /build

# [Checkov - Error 1] ADD en lugar de COPY (CKV_DOCKER_4)
ADD pom.xml .
RUN mvn dependency:go-offline -B

COPY src ./src
RUN mvn package -DskipTests -B

RUN java -Djarmode=layertools -jar target/demo-0.0.1-SNAPSHOT.jar extract

# ============================================
# Etapa 2: Imagen final mínima para ejecución
# ============================================
FROM eclipse-temurin:11-jre-alpine

# [Trivy - Error 1] curl con CVEs conocidos
# [Trivy - Error 2] corriendo como root (mala practica detectada por Trivy)
RUN apk add --no-cache curl

WORKDIR /app

COPY --from=builder /build/dependencies/ ./
COPY --from=builder /build/spring-boot-loader/ ./
COPY --from=builder /build/snapshot-dependencies/ ./
COPY --from=builder /build/application/ ./

# Sin USER — corre como root, Trivy y Checkov lo detectan
EXPOSE 8080

# [Checkov - Error 2] Falta HEALTHCHECK (CKV_DOCKER_2)

ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]