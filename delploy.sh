#!/bin/bash

# ============================================================
# deploy.sh
# Recibe 2 argumentos desde Jenkins:
#   $1 = version nueva  ej: crayolito/proyecto-final-modulo5:0640-16-03-2026
#   $2 = version anterior ej: crayolito/proyecto-final-modulo5:0530-15-03-2026
#
# Flujo:
#   1. Matar contenedores en 8081 y 8082
#   2. Levantar version nueva en 8081, version anterior en 8082
#   3. Health check con reintentos en 8081
#   4. Si OK  → escribir version nueva en versions.env
#   5. Si FALLA → rollback: leer ultima version limpia del archivo
#                            levantar esa version en 8081
#                            NO escribir nada en versions.env
# ============================================================

VERSIONS_FILE=/home/modulo5diplomado/deploy/versions.env
LOG_FILE=/home/modulo5diplomado/deploy/deploy.log

# Argumentos que manda Jenkins
VERSION_NUEVA=$1
VERSION_ANTERIOR=$2

# Reintentos para el health check
MAX_INTENTOS=10
SEGUNDOS_ESPERA=10

FECHA=$(date '+%Y-%m-%d %H:%M:%S')

# ---- Escribe en pantalla y en log al mismo tiempo ----
log() {
    echo "$1"
    echo "$1" >> "${LOG_FILE}"
}

log ""
log "================================================"
log "  DEPLOY INICIADO: ${FECHA}"
log "  Version nueva    : ${VERSION_NUEVA}"
log "  Version anterior : ${VERSION_ANTERIOR}"
log "================================================"

# ============================================================
# PASO 1 — Matar contenedores que esten corriendo en 8081 y 8082
# ============================================================
log ""
log "--- Liberando puertos ---"

CONT_8081=$(docker ps --filter "publish=8081" --format "{{.ID}}" 2>/dev/null)
if [ -n "${CONT_8081}" ]; then
    docker stop ${CONT_8081} > /dev/null
    docker rm   ${CONT_8081} > /dev/null
    log "Contenedor en 8081 detenido y eliminado"
else
    log "Puerto 8081 libre"
fi

CONT_8082=$(docker ps --filter "publish=8082" --format "{{.ID}}" 2>/dev/null)
if [ -n "${CONT_8082}" ]; then
    docker stop ${CONT_8082} > /dev/null
    docker rm   ${CONT_8082} > /dev/null
    log "Contenedor en 8082 detenido y eliminado"
else
    log "Puerto 8082 libre"
fi

# ============================================================
# PASO 2 — Levantar los 2 contenedores
# ============================================================
log ""
log "--- Levantando contenedores ---"

docker run -d \
    --name app-8081 \
    --restart unless-stopped \
    -p 8081:8080 \
    ${VERSION_NUEVA} > /dev/null
log "Contenedor 8081 iniciado con ${VERSION_NUEVA}"

docker run -d \
    --name app-8082 \
    --restart unless-stopped \
    -p 8082:8080 \
    ${VERSION_ANTERIOR} > /dev/null
log "Contenedor 8082 iniciado con ${VERSION_ANTERIOR}"

# ============================================================
# PASO 3 — Health check con reintentos en 8081
# ============================================================
log ""
log "--- Health check 8081 (max ${MAX_INTENTOS} intentos) ---"

INTENTO=0
SALIO_OK=0

while [ ${INTENTO} -lt ${MAX_INTENTOS} ]; do
    INTENTO=$((INTENTO + 1))

    STATUS=$(curl -s -o /dev/null -w "%{http_code}" --max-time 5 http://localhost:8081/health 2>/dev/null || echo "000")

    log "Intento ${INTENTO}/${MAX_INTENTOS} — HTTP: ${STATUS}"

    if [ "${STATUS}" = "200" ]; then
        SALIO_OK=1
        break
    fi

    sleep ${SEGUNDOS_ESPERA}
done

# ============================================================
# PASO 4 — Segun resultado del health check
# ============================================================
if [ ${SALIO_OK} -eq 1 ]; then

    log ""
    log "Health check OK"
    echo "${VERSION_NUEVA}" >> "${VERSIONS_FILE}"
    log "Version registrada en historial: ${VERSION_NUEVA}"
    log ""
    log "================================================"
    log "  DEPLOY EXITOSO"
    log "  8081 corriendo: ${VERSION_NUEVA}"
    log "  8082 corriendo: ${VERSION_ANTERIOR}"
    log "================================================"

else

    log ""
    log "Health check FALLO en 8081 — iniciando rollback"

    docker stop app-8081 > /dev/null 2>&1 || true
    docker rm   app-8081 > /dev/null 2>&1 || true
    docker stop app-8082 > /dev/null 2>&1 || true
    docker rm   app-8082 > /dev/null 2>&1 || true
    log "Contenedores bajados"

    # Leer ultima version limpia del historial (la nueva no se escribio)
    VERSION_ROLLBACK=$(tail -1 "${VERSIONS_FILE}")
    log "Version de rollback: ${VERSION_ROLLBACK}"

    docker run -d \
        --name app-8081 \
        --restart unless-stopped \
        -p 8081:8080 \
        ${VERSION_ROLLBACK} > /dev/null
    log "Rollback aplicado en 8081 con ${VERSION_ROLLBACK}"

    log ""
    log "================================================"
    log "  DEPLOY FALLO — ROLLBACK APLICADO"
    log "  8081 restaurado: ${VERSION_ROLLBACK}"
    log "  versions.env NO fue modificado"
    log "================================================"

    exit 1

fi