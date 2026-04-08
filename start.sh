#!/bin/bash

echo "🚀 Lancement du projet Jeux de Cartes..."

# --- Détection du système d'exploitation ---
OS="$(uname -s)"

# --- Ouverture des ports (Uniquement sur Linux) ---
if [ "$OS" = "Linux" ]; then
    echo "🔓 [Linux] Ouverture des ports 5173 et 8080 dans ufw..."
    sudo ufw allow 5173/tcp > /dev/null
    sudo ufw allow 8080/tcp > /dev/null
    echo "✅ Ports ouverts !"
elif [ "$OS" = "Darwin" ]; then
    echo "🍏 [macOS] Le pare-feu affichera un pop-up si une autorisation est requise."
fi

# --- Récupérer l'IP locale (Différent sur Mac et Linux) ---
if [ "$OS" = "Darwin" ]; then
    # Essaye de choper l'IP du Wi-Fi (en0) ou de l'Ethernet (en1)
    LOCAL_IP=$(ipconfig getifaddr en0 2>/dev/null || ipconfig getifaddr en1 2>/dev/null)
else
    LOCAL_IP=$(hostname -I | awk '{print $1}')
fi

# 1. Lancement du backend (Spring Boot)
echo "☕ Démarrage du Back-end (Spring Boot)..."
cd backend-cartes
./mvnw spring-boot:run &
BACKEND_PID=$!
cd ..

# 2. Lancement du frontend (React/Vite)
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
trap "echo -e '\n🛑 Arrêt des serveurs...'; kill $BACKEND_PID $FRONTEND_PID 2>/dev/null; exit" SIGINT

wait