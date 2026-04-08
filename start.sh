#!/bin/bash

echo "🚀 Lancement du projet Jeux de Cartes..."

# Récupérer l'IP locale de la machine
LOCAL_IP=$(hostname -I | awk '{print $1}')

# 1. Lancement du backend (Spring Boot) — écoute sur 0.0.0.0 par défaut
echo "☕ Démarrage du Back-end (Spring Boot)..."
cd backend-cartes
./mvnw spring-boot:run &
BACKEND_PID=$!
cd ..

# 2. Lancement du frontend (React/Vite) — --host pour écouter sur 0.0.0.0
echo "⚛️ Démarrage du Front-end (React)..."
cd frontend-cartes
npm run dev -- --host &
FRONTEND_PID=$!
cd ..

echo ""
echo "✅ Les deux serveurs sont en cours de démarrage !"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "🖥️  Accès local :"
echo "   Front-end : http://localhost:5173"
echo "   Back-end  : http://localhost:8080"
echo ""
echo "🌐 Accès réseau (autres PC) :"
echo "   Front-end : http://${LOCAL_IP}:5173"
echo "   Back-end  : http://${LOCAL_IP}:8080"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "⚠️ Appuie sur Ctrl+C pour tout arrêter proprement."

# 3. Capture du Ctrl+C pour tuer les deux processus
trap "echo -e '\n🛑 Arrêt des serveurs...'; kill $BACKEND_PID $FRONTEND_PID 2>/dev/null; return 2>/dev/null || exit" SIGINT

# On attend pour garder le terminal actif
wait
