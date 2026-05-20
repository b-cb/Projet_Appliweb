 # Rapport de Projet - Belote & Tarot ENSEEIHT
*Préparation pour l'oral*

## 1. Introduction & Objectifs du Projet
* **Thème :** Jeu de cartes en ligne multijoueur (Coinche et Tarot).
* **Objectif :** Développer une application web complète respectant le cahier des charges :
  * Architecture MVC.
  * Back-end SpringBoot.
  * Complexité significative (plus de 7 entités).
  * Prise en compte des performances.

## 2. Architecture Globale
Le projet suit une architecture classique **Client-Serveur** basée sur le patron **MVC (Modèle-Vue-Contrôleur)**. Le système est découpé en deux projets distincts qui communiquent via une API REST et des WebSockets :
* **Backend (Serveur / Contrôleur + Modèle) :** Java 21, SpringBoot 3.x (Port 8080).
* **Frontend (Vue / Client) :** React propulsé par Vite (Port 5173).

## 3. Choix Techniques Backend (SpringBoot)
### Modèle de Données (Les Entités)
Pour respecter la consigne de complexité, nous avons modélisé le domaine avec **8 entités** persistées en base :
1. `Utilisateur` : Gestion des comptes (pseudo, mdp, score global, flag bot).
2. `Joueur` : Représente la participation temporaire d'un utilisateur à une partie spécifique (cartes en main, équipe, position).
3. `Partie` : L'entité centrale (machine à état du jeu, score de la manche, atout, tour, historique).
4. `Carte` : Représentation basique d'une carte (couleur, valeur).
5. `Pli` : Historique d'un tour de table (cartes jouées, ouvreur, gagnant).
6. `Enchere` : Historique des déclarations avant le jeu.
7. `Invitation` : Gestion de la mise en relation entre joueurs dans le lobby.
8. `MessageChat` : Historique du chat en direct associé à une partie.

### Base de Données
* **H2 Database :** Base de données relationnelle embarquée. Configurée en mode fichier (`jdbc:h2:file:./data/cartesdb`) pour assurer la persistance des données entre les redémarrages, tout en gardant l'application facile à déployer (pas de serveur SQL externe à configurer).
* **JPA / Hibernate :** ORM utilisé pour mapper nos objets Java vers les tables relationnelles.

### Sécurité & Authentification
* **Spring Security & JWT (JSON Web Tokens) :** L'authentification est *stateless* (sans état) via JWT. Lors de la connexion, le serveur génère un token signé avec une clé secrète. Le client stocke ce token et l'inclut dans l'en-tête `Authorization: Bearer <token>` de chaque requête HTTP REST.
* Le mot de passe est haché en base de données avec **BCrypt**.

### Communication Temps Réel (Le cœur du jeu)
* **WebSockets avec STOMP :** Solution technique majeure pour les performances. Au lieu d'utiliser du *long-polling* HTTP (qui surcharge le serveur), le serveur pousse activement les mises à jour via `SimpMessagingTemplate`.
* **Sécurité anti-triche :** Les clients s'abonnent à un topic personnel (`/topic/partie/{id}/joueur/{userId}`). Le serveur génère une vue de l'état du jeu (`EtatJeuDTO`) spécifique à chaque joueur, contenant uniquement **ses propres cartes**. Il est impossible de tricher en inspectant le trafic réseau car le client ne reçoit jamais les cartes des adversaires.

### IA / Bots
* Un `BotService` asynchrone (`@Async`) permet de remplacer des joueurs par des algorithmes. Il analyse l'état du jeu et joue automatiquement si c'est son tour, avec un délai (`Thread.sleep`) pour simuler le comportement humain.

## 4. Choix Techniques Frontend (React)
* **Framework :** React via Vite (pour des builds très rapides).
* **Routage :** `react-router-dom` pour une Single Page Application (SPA).
* **Gestion d'état :** Utilisation intensive des Hooks React (`useState`, `useEffect`, `useCallback`) et d'un contexte global pour l'authentification (`AuthContext`).
* **WebSockets :** Utilisation de la librairie `@stomp/stompjs` avec un hook personnalisé (`useWebSocket`) pour lier la réception des messages STOMP au re-rendu de l'interface React.
* **Design & UX :** CSS natif soigné avec animations fluides (déplacement des cartes) et design responsive, conformément aux attentes d'une application moderne.

## 5. Fonctionnalités Implémentées
* **Identification :** Inscription, connexion, gestion des sessions.
* **Lobby & Invitations :** Liste des parties ouvertes, liste des joueurs connectés, système d'invitation (accepter/refuser).
* **Jeu en temps réel :** 
  * Moteur de **Coinche** (à 4 joueurs).
  * Moteur de **Tarot** complet (à 3, 4 et 5 joueurs avec appel du Roi et Poignées).
  * Vérification stricte des règles coté serveur (obligation de fournir, de couper, de monter).
* **Chat :** Chat intégré en direct par partie.
* **Classement :** Enregistrement des victoires par utilisateur.

---

## 6. Questions Potentielles du Professeur & Réponses

**Q1 : Pourquoi utiliser JWT plutôt que des sessions classiques (Cookies/JSESSIONID) ?**
> *Réponse :* JWT est *stateless* (sans état coté serveur). Le serveur n'a pas besoin de maintenir une session en mémoire RAM pour chaque utilisateur, ce qui améliore les performances et la "scalabilité". C'est également la norme pour les API REST couplées à des Single Page Applications (comme React), car cela évite les problèmes complexes liés aux Cookies (CSRF, CORS).

**Q2 : Comment gérez-vous la triche ? Comment empêcher un joueur d'utiliser l'inspecteur du navigateur pour voir les cartes des autres ?**
> *Réponse :* La sécurité est gérée côté serveur (Backend). L'objet `Partie` en base connaît toutes les cartes, mais lorsqu'on l'envoie au frontend, on utilise le pattern DTO (Data Transfer Object). La méthode `getEtatJeu` filtre les données : elle ne renseigne la liste `maMain` qu'avec les cartes du joueur qui a fait la requête. L'inspecteur web du joueur ne contient donc physiquement que ses propres cartes.

**Q3 : Comment avez-vous répondu à la consigne sur la "prise en compte des performances" ?**
> *Réponse :* 
> 1. L'utilisation de WebSockets (STOMP) au lieu du polling HTTP évite d'ouvrir/fermer des milliers de connexions HTTP par minute, réduisant drastiquement l'overhead réseau et CPU.
> 2. Les algorithmes de bots s'exécutent de manière asynchrone (`@Async`).
> 3. Côté React, l'utilisation de `useCallback` et de timeouts intelligents empêche l'interface de se re-rendre inutilement en cas d'avalanche d'événements réseau.
> 4. Utilisation de DTOs pour ne pas surcharger la bande passante avec des objets persistants entiers.

**Q4 : Quel "design pattern" avez-vous principalement utilisé en dehors du MVC global ?**
> *Réponse :* Le pattern **DTO (Data Transfer Object)**. Nous ne renvoyons jamais nos entités JPA (qui ont des relations bidirectionnelles `@OneToMany`, `@ManyToOne`) directement en JSON. Cela créerait des boucles infinies de sérialisation et exposerait la structure de notre base. Nous les mappons dans des objets DTO purs (ex: `EtatJeuDTO`, `JoueurDTO`).

**Q5 : S'il fallait rajouter un nouveau jeu de cartes, serait-ce facile avec votre architecture ?**
> *Réponse :* Oui. L'entité `Partie` est très générique (avec le champ `typeJeu`). Nous avons déjà abstrait les moteurs de règles dans des classes séparées (`JeuService` pour la Coinche, `TarotService` pour le Tarot). Il suffirait de créer un `PokerService` et un `PokerController` qui manipulent l'entité `Partie` pour l'état, tout en réutilisant l'infrastructure WebSocket, Chat et Utilisateurs déjà existante.

**Q6 : Comment fonctionnent concrètement les Bots ?**
> *Réponse :* Le `BotService` s'abonne aux événements. Quand un pli ou une enchère se termine, la méthode `jouerSiTourDuBot(partieId)` est appelée. Si le joueur actuel est un bot, la méthode s'endort 900ms (pour l'UX, sinon le bot joue instantanément et on ne voit rien), analyse les cartes légales jouables dans sa main, et utilise exactement la même méthode `jouerCarte` que si c'était un utilisateur humain. Ensuite, la fonction se rappelle elle-même récursivement au cas où le joueur suivant est aussi un bot.
