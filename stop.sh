#!/bin/bash

set -u

ROOT_DIR="$(cd "$(dirname "$0")" && pwd)"
PID_FILE="$ROOT_DIR/.appliweb.pids"

if [ ! -f "$PID_FILE" ]; then
    echo "ℹ️  Aucun fichier PID trouvé. Aucun processus n'a été arrêté."
    exit 0
fi

# Lire les PID une seule fois : start.sh peut supprimer le fichier pendant son arrêt.
PIDS=""
while IFS= read -r pid; do
    case "$pid" in
        ''|*[!0-9]*)
            echo "⚠️  PID ignoré car invalide : $pid"
            ;;
        *)
            PIDS="$PIDS $pid"
            ;;
    esac
done < "$PID_FILE"

if [ -z "${PIDS// /}" ]; then
    rm -f "$PID_FILE"
    echo "ℹ️  Aucun PID valide trouvé."
    exit 0
fi

echo "🛑 Arrêt des serveurs lancés par start.sh..."

for pid in $PIDS; do
    if kill -0 "$pid" 2>/dev/null; then
        echo "   Envoi de SIGTERM au processus $pid"
        kill -TERM "$pid" 2>/dev/null || true
    fi
done

# Laisser quelques secondes aux processus pour se terminer proprement.
remaining=0
for _ in 1 2 3 4 5; do
    remaining=0
    for pid in $PIDS; do
        if kill -0 "$pid" 2>/dev/null; then
            remaining=1
        fi
    done
    [ "$remaining" -eq 0 ] && break
    sleep 1
done

if [ "$remaining" -eq 0 ]; then
    rm -f "$PID_FILE"
    echo "✅ Processus arrêtés proprement."
else
    echo "⚠️  Un processus est encore actif. Aucun SIGKILL automatique n'a été envoyé."
    echo "   Vérifie les PID dans $PID_FILE avant toute action manuelle."
    exit 1
fi
