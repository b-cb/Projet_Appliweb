# Rapport Technique Détaillé : Application Web "Jeux de Cartes" (Coinche & Tarot)

## 1. Vue d'ensemble de l'Architecture
L'application est découpée en deux parties distinctes qui communiquent via des API REST et des WebSockets :
- **Backend** : Une API Java Spring Boot qui gère toute la logique métier, la mémorisation de l'état des parties, les règles complexes des jeux (Belote, Coinche, Tarot à 3, 4, et 5 joueurs), et l'intelligence artificielle basique (bots).
- **Frontend** : Une application React (Single Page Application) construite avec Vite, conçue pour réagir en temps réel aux événements de jeu.

---

## 2. Infrastructure Backend (Java/Spring Boot 3)

### 2.1. Frameworks et Bibliothèques Principales
- **Spring Boot 3.2+** : Le socle de l'application (serveur Tomcat embarqué).
- **Spring Security & JWT (`io.jsonwebtoken`)** : Pour la sécurisation des appels REST et l'authentification "sans état" (stateless).
- **Spring WebSocket / STOMP** : Essentiel pour la communication asynchrone (ex: forcer l'actualisation de la page quand c'est à un autre joueur de jouer).
- **Spring Data JPA & Hibernate** : ORM pour la persistance des données.
- **Base de données H2** : Le choix technique est une DB fichier locale (`./data/cartesdb`). Elle permet d'être autonome tout en conservant les données d'un redémarrage à l'autre.

### 2.2. Modèle de Données (Couche Entité)
Les entités persistantes reproduisent l'état complet du jeu :
- **[Utilisateur](file:///home/batiste/Documents/Projet_Appliweb/frontend-cartes/src/services/api.js#119-124)** : Comptes (identifiant, pseudo, mot de passe hashé).
- **[Joueur](file:///home/batiste/Documents/Projet_Appliweb/backend-cartes/src/main/java/fr/enseeiht/jeux/service/PartieService.java#230-236)** : L'avatar d'un utilisateur dans une partie spécifique, contient sa position (`0` à `4`), son `equipe` (`1` ou `2`), et ses `cartesEnMain` (sauvegardées via des tables de liaison).
- **[Partie](file:///home/batiste/Documents/Projet_Appliweb/backend-cartes/src/main/java/fr/enseeiht/jeux/modele/Partie.java#7-267)** : L'entité centrale monstrueuse. Elle gère :
  - **Infos de base** : `typeJeu` (COINCHE, TAROT), statut (ATTENTE, EN_JEU, TERMINEE), `phaseJeu` (enchères, écart, jeu libre).
  - **Multi-Manche** : `donneActuelle`, `maxDonnes`, `maxPoints`, `scoreGlobalA`/`scoreGlobalB`.
  - **Spécificités Coinche / Belote** : atout, contrat, belote/rebelote, preneur.
  - **Spécificités Tarot** : `chien`, `ecartes`, `poigneeDeclaree`, `petitAuBoutPreneur`, appel au roi (pour Tarot à 5).
- **[Pli](file:///home/batiste/Documents/Projet_Appliweb/backend-cartes/src/main/java/fr/enseeiht/jeux/dto/EtatJeuDTO.java#123-141)** & **[Carte](file:///home/batiste/Documents/Projet_Appliweb/backend-cartes/src/main/java/fr/enseeiht/jeux/dto/EtatJeuDTO.java#134-135)** : Historisation de chaque tour de jeu.

### 2.3. Logique Métier (Couche de Service)
La logique métier est séparée pour isoler la complexité des différents jeux :
- **[JeuService](file:///home/batiste/Documents/Projet_Appliweb/backend-cartes/src/main/java/fr/enseeiht/jeux/service/JeuService.java#18-816) (La Coinche)** : Gère le déroulement et l'application stricte des règles (ex. [verifierReglesCouleur](file:///home/batiste/Documents/Projet_Appliweb/backend-cartes/src/main/java/fr/enseeiht/jeux/service/JeuService.java#438-519) applique l'obligation de monter, de couper, et intègre la notion de "partenaire maître"). Gère également la redistribution.
- **[TarotService](file:///home/batiste/Documents/Projet_Appliweb/backend-cartes/src/main/java/fr/enseeiht/jeux/service/TarotService.java#31-1248) & [TarotScoringService](file:///home/batiste/Documents/Projet_Appliweb/backend-cartes/src/main/java/fr/enseeiht/jeux/service/TarotScoringService.java#22-171)** : S'occupe du flux spécifique du Tarot (distribution de 78 cartes, chien de 3 ou 6 cartes, phase d'écart, poignées simples/doubles/triples, calcul de points lié aux 'Bouts').
- **[PartieService](file:///home/batiste/Documents/Projet_Appliweb/backend-cartes/src/main/java/fr/enseeiht/jeux/service/PartieService.java#15-409)** : L'orchestrateur de haut niveau chargé de créer les tables virtuelles, de gérer le lobby, et d'initialiser (ou de rajouter) des bots.
- **`BotService` / `TarotBotService`** : L'implémentation de l'IA. Les bots lisent l'état (la table et leur propre main), utilisent des heuristiques simples (ex. essayer de prendre, se débarrasser des mauvaises cartes, jouer de l'atout si possible), et postent leurs actions avec un délai (`Thread.sleep`) pour simuler un humain sans bloquer l'API.

### 2.4. Couche Temps Réel (WebSockets)
Il était impératif que les joueurs voient les actions des autres sans recharger. 
- *Configuration* : [WebSocketConfig](file:///home/batiste/Documents/Projet_Appliweb/backend-cartes/src/main/java/fr/enseeiht/jeux/config/WebSocketConfig.java#9-29) paramètre un broker de messages STOMP avec SockJS (fallback HTTP).
- *Flux d'information* : Lors d'une action REST classique (ex. Jouer une carte en POST), si l'état change, le contrôleur utilise `SimpMessagingTemplate` pour "pusher" un `EvenementJeuDTO`. 
- *Sujets de souscription (Channels)* : 
  - `/topic/partie/{id}` : Pour le chat et les invitations générales (données publiques).
  - `/topic/partie/{id}/joueur/{uid}` : Pour pousser l'état total du jeu (*State Synchronization*), afin que les mains des adversaires ne transitent jamais sur le réseau, évitant l'anti-jeu (triche par inspection des paquets réseau).

---

## 3. Infrastructure Frontend (React/Vite)

### 3.1. Structure et Outils
- **React 18/19 & Vite** : Fast Developer Experience avec HMR (Hot Module Replacement).
- **CSS Modulaire/Vanilla** : Utilisation de variables CSS (`var(--primary)`, `var(--bg-fiel)`) plutôt que Tailwind, permettant un design riche ("glassmorphism", feutre de casino de carte vert, animations 3D des cartes).
- **`react-router-dom`** : Pour gérer la navigation logique (Login -> Lobby -> Table de Coinche/Tarot).

### 3.2. Gestion du Temps Réel ([useWebSocket.js](file:///home/batiste/Documents/Projet_Appliweb/frontend-cartes/src/hooks/useWebSocket.js))
Crucial pour l'expérience :
- Un hook customisant `@stomp/stompjs` avec auto-reconnexion. Il injecte un callback `onEtatJeu` qui modifie l'état central du composant page-mère ([GamePage](file:///home/batiste/Documents/Projet_Appliweb/frontend-cartes/src/pages/GamePage.jsx#14-34) / [TarotGamePage](file:///home/batiste/Documents/Projet_Appliweb/frontend-cartes/src/pages/TarotGamePage.jsx#77-393)).
- **Bufferisation** : Un timer `setTimeout` retient la transition vers le pli suivant pendant environ 2 secondes pour que l'humain ait le temps de voir et d'analyser le pli précédement bouclé, sinon les cartes disparaîtraient instantanément.

### 3.3. Hiérarchie des Composants 
Le front applique un pattern "Conteneur Intelligent / Composants Présentationnels" :
- **LobbyPage** : Organise le matchmaking, crée les parties (système de sélection d'objectif multi-manche : "score" vs "nombre de donnes").
- **GamePage / TarotGamePage** : Reçoit l'état, vérifie s'il est au tour du joueur, et s'occupe de l'Interface de Table via un système de "chaises" rotatif à la position absolue, adaptatif selon le format (ex. layout pentagonal pour le Tarot à 5).
- **CardImage.jsx** : Utilise des SVGs natifs de cartes françaises standard ou Tarot (à partir d'assets ou de génération vectorielle) pour l'esthétique finale.
- **BiddingPanel / ChienPanel** : Composants éphémères montés conditionnellement selon la `phaseJeu` du Backend.

---

## 4. Choix Techniques Exceptionnels & Résolutions de Problèmes Locaux

### Accès LAN Sécurisé
Originellement restreint à `localhost`, Spring Boot a été reconfiguré via un `CorsConfigurationSource` global avec `allowedOriginPatterns("*")`. De cette manière, les mobiles et tablettes sur le même WiFi peuvent accéder au web server de dev via d'autres IPs LAN (ex:`192.168.1.X`).

### Résolution de Dépendances Circulaires (Antipattern Backend)
Pendant le développement du Multi-Manche, [PartieService](file:///home/batiste/Documents/Projet_Appliweb/backend-cartes/src/main/java/fr/enseeiht/jeux/service/PartieService.java#15-409) (gestion de base) et [JeuService](file:///home/batiste/Documents/Projet_Appliweb/backend-cartes/src/main/java/fr/enseeiht/jeux/service/JeuService.java#18-816) (gestion du tour par tour) s'appelaient mutuellement pour "relancer" une donne. Ceci a été résolu en basculant la logique complète de la "mise à zéro" ([redemarrerDonneCoinche](file:///home/batiste/Documents/Projet_Appliweb/backend-cartes/src/main/java/fr/enseeiht/jeux/service/JeuService.java#730-780) / [redemarrerDonneTarot](file:///home/batiste/Documents/Projet_Appliweb/backend-cartes/src/main/java/fr/enseeiht/jeux/service/TarotService.java#1181-1247)) directement au sein des services métiers finaux, s'isolant ainsi de l'orchestrateur.

### Règle du "Partenaire Maître"
Spécificité culturelle de la Coinche : la logique de vérification de jeu oblige à évaluer en direct le "pseudo-gagnant" provisoire d'un pli ([estPartenaireLeGagnantActuel](file:///home/batiste/Documents/Projet_Appliweb/backend-cartes/src/main/java/fr/enseeiht/jeux/service/JeuService.java#520-560)). Si un allié domine, l'algorithme "court-circuite" les fonctions coercitives du serveur (qui obligent à couper ou surcouper).

### Persistance et H2
À noter que `spring.jpa.hibernate.ddl-auto=update` permet des ajustements lisses lors du dev. Mais quand des refontes totales du schéma ont lieu (ex. ajout de la colonne `poigneeDeclaree`), la méthode la plus stable trouvée est de purger `cartesdb.mv.db`, la base H2 recréant son schéma complet à la reconnexion avec les types correspondants stricts.
