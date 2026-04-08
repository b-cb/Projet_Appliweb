# 🃏 Projet Application Web - Jeu de Cartes en Ligne

Ce dépôt contient le code source de notre application web de jeu de cartes multijoueur (Belote / Coinche). 

Le projet permet de jouer en ligne et intègre des fonctionnalités avancées telles que l'identification des joueurs, un système d'invitations, un mode de jeu fluide, un chat en direct, ainsi qu'un historique et un classement.

## 🏗️ Architecture du Projet

L'application respecte le modèle MVC (Modèle-Vue-Contrôleur) avec une séparation nette entre le client et le serveur.

### 1. Front-end (Single Page Application)
Pour offrir une expérience fluide sans rechargement de page, le client web est développé sous la forme d'une Single Page Application (SPA). 
* **Communication Asynchrone :** L'interface utilise la *Fetch API* de JavaScript pour envoyer des requêtes HTTP au serveur.
* **Synchronisation :** Le client interroge régulièrement l'API REST pour mettre à jour le tapis de jeu et simuler le temps réel.

### 2. Back-end (Spring Boot & REST)
Le serveur d'application est propulsé par **Spring Boot**. 
* **Façade REST :** Les contrôleurs Spring exposent la logique métier sous forme d'API REST pour traiter les actions des joueurs.
* **Persistance des données (JPA) :** La gestion des données en base relationnelle est assurée par l'ORM JPA (Java Persistence API). Les accès à la base de données se font au travers d'interfaces `Repository` qui fournissent les opérations CRUD.

## 🗄️ Modèle de Données

Notre modèle de données relationnel est structuré autour des entités principales suivantes :

1. **Joueur** : Gère l'authentification et les statistiques.
2. **Partie** : Représente une instance de jeu (statut, atout, scores).
3. **Participation (Main)** : Entité de liaison définissant l'équipe du joueur et sa main.
4. **Carte** : Définit la valeur et la couleur d'une carte.
5. **Pli** : Regroupe les cartes posées sur la table lors d'un tour.
6. **Enchere** : Stocke le contrat annoncé en début de partie.
7. **MessageChat** : Permet de conserver l'historique des discussions.
8. **Invitation** : Gère les requêtes pour rejoindre une table.

## 🚀 Installation et Lancement

Le projet est divisé en deux répertoires distincts : `frontend-cartes` (React) et `backend-cartes` (Spring Boot).

### Prérequis
* Java (JDK 17+)
* Node.js et npm

### Démarrage rapide
Un script bash est fourni à la racine pour démarrer simultanément l'API Spring Boot et le serveur de développement React en arrière-plan.

```bash
# 1. Donner les droits d'exécution aux scripts
chmod +x start.sh stop.sh

# 2. Lancer l'application complète
./start.sh# Projet_Appliweb
