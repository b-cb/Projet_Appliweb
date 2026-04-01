#!/bin/bash

echo "🚀 Lancement du projet Jeux de Cartes..."

# 1. Lancement du backend (Spring Boot)
echo "☕ Démarrage du Back-end (Spring Boot)..."
cd backend-cartes
# On utilise le wrapper maven inclus dans ton dossier
./mvnw spring-boot:run &
BACKEND_PID=$!
cd ..

# 2. Lancement du frontend (React/Vite)
echo "⚛️ Démarrage du Front-end (React)..."
cd frontend-cartes
npm run dev &
FRONTEND_PID=$!
cd ..

echo "✅ Les deux serveurs sont en cours de démarrage !"
echo "🌍 Front-end : http://localhost:5173"
echo "🔌 Back-end API : http://localhost:8080"
echo "⚠️ Appuie sur Ctrl+C pour tout arrêter proprement."

# 3. Capture du Ctrl+C pour tuer les deux processus
trap "echo -e '\n🛑 Arrêt des serveurs...'; kill $BACKEND_PID $FRONTEND_PID 2>/dev/null; return 2>/dev/null || exit" SIGINT

# On attend pour garder le terminal actif
wait
