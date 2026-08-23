#!/bin/bash

set -u

ROOT_DIR="$(cd "$(dirname "$0")" && pwd)"
PID_FILE="$ROOT_DIR/.appliweb.pids"
APP_HOST="${APP_HOST:-127.0.0.1}"

cd "$ROOT_DIR" || exit 1

echo "🚀 Lancement du projet Jeux de Cartes en mode développement local..."

if [ -f "$PID_FILE" ]; then
    echo "❌ Un fichier PID existe déjà : $PID_FILE"
    echo "   Lance ./stop.sh ou vérifie les processus avant de redémarrer."
    exit 1
fi

# Secret éphémère local si JWT_SECRET n'est pas déjà défini.
if [ -z "${JWT_SECRET:-}" ]; then
    if command -v openssl >/dev/null 2>&1; then
        export JWT_SECRET="$(openssl rand -hex 32)"
        echo "🔐 JWT_SECRET temporaire généré pour cette exécution."
        echo "   Définis JWT_SECRET dans ton environnement pour conserver les sessions entre redémarrages."
    else
        echo "❌ JWT_SECRET est absent et openssl n'est pas disponible."
        echo "   Définis JWT_SECRET avec une valeur aléatoire d'au moins 32 octets."
        exit 1
    fi
fi

# Écoute locale et origines explicites par défaut.
export SERVER_ADDRESS="${SERVER_ADDRESS:-127.0.0.1}"
export APP_CORS_ALLOWED_ORIGINS="${APP_CORS_ALLOWED_ORIGINS:-http://localhost:5173,http://127.0.0.1:5173}"
export APP_WEBSOCKET_ALLOWED_ORIGINS="${APP_WEBSOCKET_ALLOWED_ORIGINS:-http://localhost:5173,http://127.0.0.1:5173}"

cleanup() {
    echo ""
    echo "🛑 Arrêt des serveurs..."
    for pid in "${BACKEND_PID:-}" "${FRONTEND_PID:-}"; do
        if [ -n "$pid" ] && kill -0 "$pid" 2>/dev/null; then
            kill -TERM "$pid" 2>/dev/null || true
        fi
    done
    wait "${BACKEND_PID:-}" "${FRONTEND_PID:-}" 2>/dev/null || true
    rm -f "$PID_FILE"
}

trap cleanup INT TERM EXIT

# 1. Lancement du backend Spring Boot
 echo "☕ Démarrage du Back-end (Spring Boot)..."
(
    cd backend-cartes || exit 1
    exec ./mvnw spring-boot:run
) &
BACKEND_PID=$!

# 2. Lancement du frontend React/Vite
 echo "⚛️ Démarrage du Front-end (React)..."
(
    cd frontend-cartes || exit 1
    if [ ! -d "node_modules" ]; then
        echo "📦 Installation des dépendances npm (premier lancement)..."
        npm install
    fi
    exec npm run dev -- --host "$APP_HOST"
) &
FRONTEND_PID=$!

printf '%s\n%s\n' "$BACKEND_PID" "$FRONTEND_PID" > "$PID_FILE"

echo ""
echo "✅ Les deux serveurs démarrent en local :"
echo "   Front-end : http://localhost:5173"
echo "   Back-end  : http://localhost:8080"
echo ""
echo "ℹ️  Pour un accès LAN volontaire, configure explicitement APP_HOST,"
echo "   SERVER_ADDRESS, APP_CORS_ALLOWED_ORIGINS et APP_WEBSOCKET_ALLOWED_ORIGINS."
echo "⚠️  Appuie sur Ctrl+C pour arrêter proprement les deux processus."

wait
