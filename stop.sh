#!/bin/bash

echo "🛑 Arrêt forcé des serveurs Jeux de Cartes..."

# 1. Arrêt du Back-end (qui tourne sur le port 8080)
if lsof -t -i:8080 > /dev/null 2>&1; then
    echo "🔌 Libération du port 8080 (Back-end Spring Boot)..."
    kill -9 $(lsof -t -i:8080) 2>/dev/null
else
    echo "✅ Le Back-end n'était pas en cours d'exécution."
fi

# 2. Arrêt du Front-end (qui tourne sur le port 5173 pour Vite/React)
if lsof -t -i:5173 > /dev/null 2>&1; then
    echo "⚛️ Libération du port 5173 (Front-end React)..."
    kill -9 $(lsof -t -i:5173) 2>/dev/null
else
    echo "✅ Le Front-end n'était pas en cours d'exécution."
fi

echo "🧹 Nettoyage terminé ! Tes ports sont libres."
