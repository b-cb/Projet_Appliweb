#!/bin/bash

echo "🛑 Arrêt forcé des serveurs Jeux de Cartes..."

OS="$(uname -s)"

# 1. Arrêt du Back-end
# La commande lsof marche très bien sur Mac et Linux !
if lsof -t -i:8080 > /dev/null 2>&1; then
    echo "🔌 Libération du port 8080 (Back-end Spring Boot)..."
    kill -9 $(lsof -t -i:8080) 2>/dev/null
else
    echo "✅ Le Back-end n'était pas en cours d'exécution."
fi

# 2. Arrêt du Front-end
if lsof -t -i:5173 > /dev/null 2>&1; then
    echo "⚛️ Libération du port 5173 (Front-end React)..."
    kill -9 $(lsof -t -i:5173) 2>/dev/null
else
    echo "✅ Le Front-end n'était pas en cours d'exécution."
fi

# 3. Fermeture des ports (Uniquement sur Linux)
if [ "$OS" = "Linux" ]; then
    echo "🔒 [Linux] Fermeture des ports 5173 et 8080 dans le pare-feu..."
    sudo ufw delete allow 5173/tcp > /dev/null 2>&1
    sudo ufw delete allow 8080/tcp > /dev/null 2>&1
    echo "✅ Ports refermés avec succès."
fi

echo "🧹 Nettoyage terminé ! Tes processus sont arrêtés et tes ports sont sécurisés."