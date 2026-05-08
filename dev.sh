#!/usr/bin/env bash
set -e

BACKEND_LOG=/tmp/docforge-backend.log
FRONTEND_LOG=/tmp/docforge-frontend.log
BACKEND_PID_FILE=/tmp/docforge-backend.pid
FRONTEND_PID_FILE=/tmp/docforge-frontend.pid

PROJECT_DIR="$(cd "$(dirname "$0")" && pwd)"

stop_backend() {
  if [ -f "$BACKEND_PID_FILE" ]; then
    PIDS=$(cat "$BACKEND_PID_FILE")
    echo "Останавливаю backend (PID: $PIDS)..."
    kill "$PIDS" 2>/dev/null || true
    rm -f "$BACKEND_PID_FILE"
  fi
  # убиваем по имени класса на случай, если pid-файл устарел
  pkill -f "com.docforge.DocForgeApplication" 2>/dev/null || true
  pkill -f "gradlew bootRun" 2>/dev/null || true
}

stop_frontend() {
  if [ -f "$FRONTEND_PID_FILE" ]; then
    PIDS=$(cat "$FRONTEND_PID_FILE")
    echo "Останавливаю frontend (PID: $PIDS)..."
    kill "$PIDS" 2>/dev/null || true
    rm -f "$FRONTEND_PID_FILE"
  fi
  # убиваем vite-процессы проекта
  pkill -f "vite.*DocForge" 2>/dev/null || true
  pkill -f "node.*DocForge.*vite" 2>/dev/null || true
}

start_backend() {
  echo "Запускаю backend..."
  cd "$PROJECT_DIR/backend"
  ./gradlew bootRun > "$BACKEND_LOG" 2>&1 &
  WRAPPER_PID=$!
  echo "$WRAPPER_PID" > "$BACKEND_PID_FILE"

  echo -n "Жду запуска backend"
  for i in $(seq 1 60); do
    if grep -q "Started DocForgeApplication" "$BACKEND_LOG" 2>/dev/null; then
      echo " готов."
      break
    fi
    if ! kill -0 "$WRAPPER_PID" 2>/dev/null; then
      echo ""
      echo "ОШИБКА: backend упал. Лог: $BACKEND_LOG"
      tail -20 "$BACKEND_LOG"
      exit 1
    fi
    echo -n "."
    sleep 2
  done

  # обновляем PID на реальный процесс JVM
  REAL_PID=$(pgrep -f "com.docforge.DocForgeApplication" 2>/dev/null | head -1 || true)
  if [ -n "$REAL_PID" ]; then
    echo "$REAL_PID" > "$BACKEND_PID_FILE"
    echo "Backend PID: $REAL_PID, лог: $BACKEND_LOG"
  fi
}

start_frontend() {
  echo "Запускаю frontend..."
  cd "$PROJECT_DIR/frontend"
  npm run dev > "$FRONTEND_LOG" 2>&1 &
  FRONTEND_PID=$!
  echo "$FRONTEND_PID" > "$FRONTEND_PID_FILE"

  echo -n "Жду запуска frontend"
  for i in $(seq 1 20); do
    if grep -q "Local:" "$FRONTEND_LOG" 2>/dev/null; then
      echo " готов."
      break
    fi
    echo -n "."
    sleep 1
  done

  URL=$(grep "Local:" "$FRONTEND_LOG" 2>/dev/null | awk '{print $NF}' | head -1)
  echo "Frontend PID: $FRONTEND_PID, URL: ${URL:-http://localhost:5173}, лог: $FRONTEND_LOG"
}

status() {
  echo "=== Backend ==="
  if pgrep -f "com.docforge.DocForgeApplication" > /dev/null 2>&1; then
    PID=$(pgrep -f "com.docforge.DocForgeApplication" | head -1)
    echo "  RUNNING (PID: $PID)"
  else
    echo "  STOPPED"
  fi

  echo "=== Frontend ==="
  if pgrep -f "node.*vite" > /dev/null 2>&1; then
    PID=$(pgrep -f "node.*vite" | head -1)
    echo "  RUNNING (PID: $PID)"
  else
    echo "  STOPPED"
  fi

  echo "=== Docker ==="
  docker compose -f "$PROJECT_DIR/docker-compose.yml" ps 2>/dev/null || true
}

logs() {
  TARGET=${2:-all}
  case "$TARGET" in
    backend)  tail -f "$BACKEND_LOG" ;;
    frontend) tail -f "$FRONTEND_LOG" ;;
    *)
      echo "--- Backend ($BACKEND_LOG) ---"
      tail -50 "$BACKEND_LOG" 2>/dev/null || echo "(нет лога)"
      echo ""
      echo "--- Frontend ($FRONTEND_LOG) ---"
      tail -20 "$FRONTEND_LOG" 2>/dev/null || echo "(нет лога)"
      ;;
  esac
}

CMD=${1:-restart}

case "$CMD" in
  start)
    docker compose -f "$PROJECT_DIR/docker-compose.yml" up -d
    start_backend
    start_frontend
    ;;
  stop)
    stop_backend
    stop_frontend
    echo "Приложение остановлено."
    ;;
  restart)
    stop_backend
    stop_frontend
    sleep 1
    docker compose -f "$PROJECT_DIR/docker-compose.yml" up -d
    start_backend
    start_frontend
    ;;
  backend)
    stop_backend
    sleep 1
    start_backend
    ;;
  frontend)
    stop_frontend
    sleep 1
    start_frontend
    ;;
  status)
    status
    ;;
  logs)
    logs "$@"
    ;;
  *)
    echo "Использование: $0 {start|stop|restart|backend|frontend|status|logs [backend|frontend]}"
    echo ""
    echo "  start    — запустить всё (docker + backend + frontend)"
    echo "  stop     — остановить backend и frontend"
    echo "  restart  — перезапустить всё (по умолчанию)"
    echo "  backend  — перезапустить только backend"
    echo "  frontend — перезапустить только frontend"
    echo "  status   — состояние сервисов"
    echo "  logs     — последние строки логов (backend/frontend/all)"
    exit 1
    ;;
esac
