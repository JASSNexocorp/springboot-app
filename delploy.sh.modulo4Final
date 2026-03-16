#!/usr/bin/env bash
set -euo pipefail

# ===== CONFIGURACIÓN =====
APP_NAME="spring-boot-app"
APP_DIR="/opt/spring-boot-app"
JAR_NAME="app.jar"
JAVA_OPTS="-Xms512m -Xmx1024m"
SPRING_PROFILE="prod"
PORT="${2:-8080}"  # Puerto como segundo parámetro (por defecto 8080)
NEW_JAR_PATH="$1"

# ===== VALIDACIÓN DE PARÁMETROS =====
if [[ -z "${NEW_JAR_PATH:-}" ]]; then
  echo "Uso: deploy.sh <ruta-al-nuevo-jar> <puerto>"
  echo "Ejemplo: deploy.sh /tmp/app-new.jar 8081"
  exit 1
fi

echo "🚀 Desplegando ${APP_NAME} en puerto ${PORT}"
echo "➡️  Nuevo artefacto: ${NEW_JAR_PATH}"
cd "$APP_DIR"

# ===== DETENER APLICACIÓN EN EL PUERTO ESPECÍFICO =====
echo "🔍 Buscando aplicación corriendo en puerto ${PORT}..."
PID=$(lsof -ti:${PORT} 2>/dev/null || true)

if [[ -n "$PID" ]]; then
  echo "🛑 Deteniendo aplicación en puerto ${PORT} (PID=${PID})"
  kill "$PID"
  
  # Esperar a que se detenga (máximo 15 segundos)
  for i in {1..15}; do
    if ! kill -0 "$PID" 2>/dev/null; then
      echo "✅ Aplicación detenida correctamente"
      break
    fi
    sleep 1
  done
  
  # Si no se detuvo, forzar cierre
  if kill -0 "$PID" 2>/dev/null; then
    echo "⚠️  Aplicación no se detuvo, forzando cierre..."
    kill -9 "$PID"
  fi
else
  echo "ℹ️  No hay aplicación corriendo en puerto ${PORT}"
fi

# ===== HACER BACKUP DE LA VERSIÓN ACTUAL =====
if [[ -f "$JAR_NAME" ]]; then
  TIMESTAMP=$(date +%Y%m%d%H%M%S)
  
  # Guardar en carpeta versions con timestamp
  echo "📦 Creando backup histórico..."
  cp "$JAR_NAME" "versions/${APP_NAME}-${TIMESTAMP}.jar"
  
  # Guardar backup para rollback rápido
  cp "$JAR_NAME" "${JAR_NAME}.bak"
  echo "✅ Backup creado: ${JAR_NAME}.bak"
fi

# ===== DESPLEGAR NUEVO JAR =====
echo "📥 Desplegando nuevo JAR..."
cp "$NEW_JAR_PATH" "$JAR_NAME"
chmod 755 "$JAR_NAME"

# ===== INICIAR APLICACIÓN =====
echo "▶️  Iniciando aplicación en puerto ${PORT}..."
nohup java $JAVA_OPTS \
  -jar "$JAR_NAME" \
  --spring.profiles.active="$SPRING_PROFILE" \
  --server.port="$PORT" \
  > logs/app-${PORT}.log 2>&1 &

NEW_PID=$!
echo "ℹ️  Aplicación iniciada con PID: ${NEW_PID}"

# ===== HEALTH CHECK =====
echo "🔍 Esperando a que la aplicación esté saludable en puerto ${PORT}..."
HEALTHY=false

for i in {1..20}; do
  if curl -sf "http://localhost:${PORT}/health" > /dev/null 2>&1; then
    echo "✅ Aplicación saludable en puerto ${PORT}"
    HEALTHY=true
    break
  fi
  echo "⏳ Intento $i/20..."
  sleep 3
done

# ===== ROLLBACK SI FALLA EL HEALTH CHECK =====
if [[ "$HEALTHY" == "false" ]]; then
  echo ""
  echo "❌ ============================================"
  echo "❌ HEALTH CHECK FALLÓ en puerto ${PORT}"
  echo "❌ ============================================"
  echo ""
  
  # Verificar si existe backup
  if [[ -f "${JAR_NAME}.bak" ]]; then
    echo "🔄 Iniciando ROLLBACK a versión anterior..."
    
    # Detener la versión fallida
    echo "🛑 Deteniendo versión fallida (PID=${NEW_PID})..."
    kill "$NEW_PID" 2>/dev/null || true
    sleep 2
    
    # Restaurar backup
    echo "📦 Restaurando desde backup: ${JAR_NAME}.bak"
    cp "${JAR_NAME}.bak" "$JAR_NAME"
    
    # Reiniciar con versión anterior
    echo "▶️  Reiniciando con versión anterior..."
    nohup java $JAVA_OPTS \
      -jar "$JAR_NAME" \
      --spring.profiles.active="$SPRING_PROFILE" \
      --server.port="$PORT" \
      > logs/app-${PORT}.log 2>&1 &
    
    # Esperar y verificar rollback
    sleep 5
    echo "🔍 Verificando que el rollback funcionó..."
    
    if curl -sf "http://localhost:${PORT}/health" > /dev/null 2>&1; then
      echo ""
      echo "✅ ============================================"
      echo "✅ ROLLBACK EXITOSO en puerto ${PORT}"
      echo "✅ Versión anterior restaurada correctamente"
      echo "✅ ============================================"
      echo ""
      exit 1  # Sale con error para marcar el deploy como fallido en Jenkins
    else
      echo ""
      echo "❌ ============================================"
      echo "❌ ROLLBACK FALLÓ en puerto ${PORT}"
      echo "❌ Se requiere intervención manual"
      echo "❌ ============================================"
      echo ""
      exit 1
    fi
  else
    echo ""
    echo "❌ ============================================"
    echo "❌ No hay backup disponible para rollback"
    echo "❌ Se requiere intervención manual"
    echo "❌ ============================================"
    echo ""
    exit 1
  fi
fi

# ===== DESPLIEGUE EXITOSO =====
echo ""
echo "✅ ============================================"
echo "✅ DESPLIEGUE EXITOSO en puerto ${PORT}"
echo "✅ Aplicación funcionando correctamente"
echo "✅ ============================================"
echo ""
exit 0