# Rapport d'architecture — Jeux de cartes en ligne

---

## Préambule — Architecture globale

L'application est une plateforme multijoueur de jeux de cartes (Belote coinchée, Tarot français)
construite selon une architecture **client-serveur découplée** avec communication temps réel par WebSocket.

```
┌─────────────────────────────────────────────────────────┐
│  Frontend — React 19 + Vite (port 5173)                 │
│  React Router · AuthContext · STOMP/SockJS              │
└─────────────────────┬─────────────────────┬─────────────┘
                      │ HTTP + JWT           │ WebSocket/STOMP
┌─────────────────────▼─────────────────────▼─────────────┐
│  Backend — Spring Boot 3 / Java 21 (port 8080)          │
│  Spring Security · JWT · STOMP broker                   │
│  JPA/Hibernate → H2 fichier (persistant)                │
└─────────────────────────────────────────────────────────┘
```

**Séparation des responsabilités backend** :
- `controller/` — validation HTTP, délégation aux services, sérialisation JSON
- `service/` — logique métier transactionnelle (`JeuService`, `TarotService`, `PartieService`, `BotService`…)
- `dto/` — projections exposées au client (jamais d'entités brutes)
- `modele/` — entités JPA (`Utilisateur`, `Partie`, `Joueur`, `Carte`, `Pli`, `Enchere`)
- `repository/` — accès données JPA (Spring Data)
- `exception/` — `BusinessException` (400) et `ResourceNotFoundException` (404) centralisées via `@ControllerAdvice`
- `config/` — Spring Security, WebSocket, CORS

**Deux modes de jeu coexistent** sans interférence. Le champ `Partie.typeJeu` (`"COINCHE"` | `"TAROT"`)
et `nbJoueursRequis` (4 pour Coinche, 3/4/5 pour Tarot) servent de discriminant à tous les niveaux :
distribution des cartes, règles de jeu, scoring.

---

## Partie Backend

### 1. Authentification

**Inscription** (`POST /api/auth/inscrire`) : `AuthController` délègue à `AuthService.inscrire(pseudo,
motDePasse)`. Le service vérifie l'unicité du pseudo via `UtilisateurRepository.findByPseudo`, encode
le mot de passe avec `BCryptPasswordEncoder` et persiste l'entité `Utilisateur`. Le mot de passe en
clair ne transite pas au-delà du service.

**Connexion** (`POST /api/auth/connexion`) : même délégation vers `AuthService.connexion`.
`passwordEncoder.matches()` compare le clair au hash BCrypt stocké. En cas de succès,
`JwtService.genererToken(utilisateur)` produit un token HMAC-SHA256 signé (payload : `sub` = userId,
`pseudo`, expiration 24h). La réponse retourne `{ utilisateur: UtilisateurDTO, token: "..." }`.
Le message d'erreur est **intentionnellement identique** pour pseudo inconnu et mauvais mot de passe
(défense contre l'énumération d'utilisateurs).

**Filtre JWT** : `JwtAuthFilter extends OncePerRequestFilter` s'exécute sur chaque requête. Il extrait
le header `Authorization: Bearer <token>`, le valide via `JwtService.validerToken()`, puis injecte un
`UsernamePasswordAuthenticationToken` dans le `SecurityContextHolder`. Spring Security repose ensuite
sur ce contexte pour appliquer les autorisations.

**Configuration Security** (`SecurityConfig`) :
- `/api/auth/**` — endpoints publics (inscription, connexion)
- Tout le reste — protégé (`authenticated()`)
- Session stateless — aucun cookie de session
- CORS restreint à `http://localhost:5173`

---

### 2. Création et démarrage de partie

**Deux chemins de création** coexistent dans `PartieController` :

**Chemin standard** (`POST /api/partie/creer`) : crée une `Partie` en statut `OUVERTE` avec `typeJeu`
et `nbJoueursRequis` issus du body. Les joueurs rejoignent ensuite une à une via
`POST /api/partie/{id}/rejoindre`. Quand le quota est atteint, l'organisateur appelle
`POST /api/partie/{id}/demarrer`.

**Chemin avec bots** (`POST /api/partie/creer?avecBots=true`) : `PartieService.creerEtDemarrerAvecBots()`
enchaîne en une transaction la création, la jonction automatique des comptes bot (Bot_1 à Bot_4, créés
au démarrage par `BotInitializer`), et le démarrage.

**`PartieService.demarrerPartie()`** :
1. Vérifie que `joueurs.size() == partie.getNbJoueursRequis()`
2. Bifurque selon `typeJeu` :
   - **COINCHE** : crée 52 cartes standard, mélange, distribue 8 par joueur, assigne les équipes
     (positions paires = équipe 1, impaires = équipe 2), `statut → EN_ENCHERE`
   - **TAROT** : crée 78 cartes (4 couleurs × 14 + 22 atouts dont l'Excuse), mélange, distribue N
     cartes par joueur selon le nombre de joueurs (3j : 24, 4j : 18, 5j : 15), met le reste dans
     `partie.chien`, `statut → EN_ENCHERE`, `phaseJeu → null`
3. Pousse l'événement `PARTIE_DEMARREE` via STOMP à tous les joueurs de la partie

**Machine à états** de `Partie` (champs `statut` × `phaseJeu`) :

```
OUVERTE
  → EN_ENCHERE / null      ← après demarrerPartie()
  → EN_ENCHERE / CHIEN     ← enchère gagnée (Petite/Garde)
  → EN_ENCHERE / CHIEN_VU  ← enchère Garde sans
  → EN_ENCHERE / APPEL_ROI ← enchère gagnée en 5j (avant chien)
  → EN_JEU / JEU           ← après écart ou Garde contre directe
  → TERMINEE               ← dernier pli joué
```

Pour la Coinche, `phaseJeu` reste `null` ; le statut seul suffit.

---

### 3. WebSocket — état du jeu en temps réel

**Configuration STOMP** (`WebSocketConfig`) :
- Endpoint SockJS : `/ws`
- Deux topics par partie :
  - `/topic/partie/{id}` — broadcast public (tous les joueurs de la partie)
  - `/topic/partie/{id}/joueur/{userId}` — canal privé par joueur (main de cartes, état personnalisé)

**Format des messages** : chaque push WebSocket passe par `EvenementJeuDTO` — un wrapper avec un champ
`type` (enum : `ETAT_JEU`, `JOUEUR_REJOINT`, `CHAT`, `PARTIE_DEMARREE`) et un payload `Object`.

**DTOs personnalisés** : `JeuService.getEtatJeu()` et `TarotService.getEtatJeuTarot()` construisent un
DTO distinct par joueur :
- `maMain` — uniquement les cartes de CE joueur (jamais les mains adverses)
- `pliCourant` — les cartes sur la table avec le pseudo de qui les a jouées
- `monJoueurId`, `tourJoueurId`, `estPreneur`, `estPartenaire` — signaux booléens permettant au frontend
  de savoir quand et quoi afficher
- Pour Tarot : `chien` (visible en phase CHIEN/CHIEN_VU), `pointsPreneurX2`, `boutsPreneur`,
  `seuilCourant`

**Déclenchement du push** : chaque action de jeu (jouer une carte, enchérir, écarter) appelle
`broadcastEtatJeu(partieId)` dans les services. Cette méthode itère sur tous les joueurs de la partie
et pousse via `SimpMessagingTemplate.convertAndSend()` le DTO personnalisé de chaque joueur sur son
canal privé.

---

### 4. Orchestration des joueurs bots

Les bots (`Bot_1` à `Bot_4`) sont des `Utilisateur` persistés en base au démarrage de l'application
via `BotInitializer` (`@Component` + `CommandLineRunner`). Ils ont des hashes BCrypt factices et sont
identifiables via leur pseudo.

**Chaîne de déclenchement** (`BotService` pour Coinche, `TarotBotService` pour Tarot) :

1. Chaque action d'un joueur humain (ou bot) enregistre un callback via
   `TransactionSynchronizationManager.registerSynchronization(afterCommit(...))` — le tour bot ne
   s'exécute qu'après la validation de la transaction courante, évitant les lectures de données
   partiellement écrites.
2. `jouerTour()` est annoté `@Async` — il s'exécute dans un thread séparé avec un délai de 1,2
   secondes (`Thread.sleep(1200)`), simulant un temps de réflexion.
3. Le bot identifie sa phase (`statut` × `phaseJeu`), exécute son action (enchérir, écarter, jouer
   une carte), puis l'`afterCommit` du bot re-schedule le tour suivant si ce n'est pas encore la fin.

**Stratégie bot Tarot (MVP simplifié)** :
- Enchères : toujours passer
- Appel du Roi (5j) : appelle la première couleur dont il ne détient pas le Roi
- Écart : écarte les cartes à moindre valeur (jamais bouts, jamais Rois)
- Jeu : joue la première carte valide selon les règles de suivi

Cette approche garantit que les bots ne bloquent jamais le jeu et que les transitions de phase
s'enchaînent correctement sans intervention humaine.

---

## Partie Frontend

### 5. Architecture des composants

```
src/
├── pages/
│   ├── LoginPage.jsx          — formulaire inscription/connexion
│   ├── LobbyPage.jsx          — liste des parties ouvertes, création, options bots/mode
│   ├── GamePage.jsx           — routeur de jeu (→ TarotGamePage si TAROT, sinon Coinche)
│   └── TarotGamePage.jsx      — interface de jeu Tarot complète (table, main, overlays)
├── components/
│   ├── CardImage.jsx          — rendu SVG (svg-cards) pour les 52 cartes standard
│   ├── TrumpCard.jsx          — rendu CSS natif pour les 22 atouts Tarot + Excuse
│   ├── HandCards.jsx          — main Coinche (cartes jouables / non jouables)
│   ├── BiddingPanel.jsx       — enchères Coinche (contrat, couleur, passe, SA, TA)
│   ├── TarotBiddingPanel.jsx  — enchères Tarot (Passe/Petite/Garde/Garde sans/Garde contre)
│   ├── ChienPanel.jsx         — affichage du chien + sélection de l'écart dans la main
│   ├── RoiSelector.jsx        — appel du Roi en 5 joueurs
│   ├── PlayerTable.jsx        — table de jeu Coinche (pli, derniers plis, adversaires)
│   └── ChatPanel.jsx          — messagerie en jeu
├── hooks/
│   ├── AuthContext.jsx        — contexte global d'authentification (token + utilisateur)
│   └── useWebSocket.js        — connexion STOMP, abonnements, dispatch des événements
└── services/
    └── api.js                 — fonctions fetch wrappées (toutes injectent le header JWT)
```

**Routage** (React Router) :
- `/` → `LoginPage` (publique)
- `/lobby` → `LobbyPage` (protégée)
- `/partie/:id` → `GamePage` → délègue à `TarotGamePage` ou page Coinche selon `typeJeu`

---

### 6. Flux du token d'authentification

**Connexion** : `LoginPage` envoie les identifiants à `/api/auth/connexion`. La réponse
`{ utilisateur, token }` est stockée dans `localStorage` (token) et dans le contexte React
(`AuthContext`).

**Propagation** : `AuthContext` expose `{ utilisateur, token, logout }` via `useAuth()`. Toutes les
fonctions de `api.js` injectent automatiquement le header `Authorization: Bearer <token>` sur chaque
requête HTTP.

**Protection des routes** : un composant `ProtectedRoute` dans `App.jsx` vérifie la présence du token
et redirige vers `/` si absent. La protection réelle est assurée par le filtre JWT backend.

**Déconnexion** : `logout()` supprime le token du `localStorage` et réinitialise le contexte,
déclenchant la redirection automatique via React Router.

---

### 7. Hook WebSocket (useWebSocket)

`useWebSocket` gère tout le cycle de vie STOMP :

**Connexion** (`connecter(partieId, userId, callbacks)`) :
1. Crée un client STOMP via SockJS sur `/ws`
2. À la connexion réussie, s'abonne à deux topics :
   - `/topic/partie/{partieId}` — événements publics (`JOUEUR_REJOINT`, `CHAT`, `PARTIE_DEMARREE`)
   - `/topic/partie/{partieId}/joueur/{userId}` — état personnalisé du joueur (`EtatJeuDTO`)

Les callbacks fournis par la page (`onEtatJeu`, `onJoueurRejoint`, `onChat`) sont appelés selon le
type de l'événement reçu.

**Gestion des plis** (dans `TarotGamePage`) : un mécanisme de buffer différé (`bufferTimerRef`) retarde
de 2 secondes l'application d'un nouvel état quand le pli courant vient de se terminer (transition
pliCourant non vide → vide). Cela laisse le temps aux joueurs de voir les cartes du pli avant
qu'elles disparaissent de la table.

**Déconnexion** (`deconnecter()`) : désactive le client STOMP et libère les abonnements. Appelée au
retour au lobby ou à la déconnexion de l'utilisateur.

---

## Tests

### `AuthServiceTest` — tests unitaires (Mockito, sans Spring)

| Test | Fonctionnalité ciblée |
|------|----------------------|
| `inscrire_pseudoDisponible_retourneUtilisateur` | Inscription OK : hash BCrypt appliqué, entité sauvegardée |
| `inscrire_pseudoDuplique_lanceException` | Unicité du pseudo : `BusinessException` si déjà pris, `save()` jamais appelé |
| `inscrire_motDePasseHashe` | Le champ `mdp` stocke le hash, jamais le clair |
| `connexion_identifiantsValides_retourneUtilisateur` | Connexion OK avec bons identifiants |
| `connexion_pseudoInconnu_lanceException` | Pseudo absent → exception |
| `connexion_mauvaisMdp_lanceException` | Mauvais mot de passe → exception |
| `connexion_messageErreurGenerique` | Message d'erreur identique pour les deux cas (anti-énumération) |

---

### `ApiIntegrationTest` — tests d'intégration HTTP (MockMvc + Spring Security)

Ces tests exercent la couche HTTP complète (filtre JWT inclus) via MockMvc avec H2 en mémoire.

| Groupe | Fonctionnalités ciblées |
|--------|------------------------|
| Inscription | Créer un compte → 201 + token JWT dans la réponse |
| Connexion | Bons identifiants → 200 + token ; mauvais mot de passe → 401 |
| Accès protégé | Requête sans token → 401 ; avec token valide → 200 |
| Gestion de parties | Créer une partie → 201 ; rejoindre → 200 ; dépasser le quota → 400 |
| Démarrage | 4 joueurs présents → statut EN_ENCHERE ; moins de joueurs → 400 |

---

### `JeuServiceIntegrationTest` — règles Belote coinchée (Spring Boot + H2)

| Groupe | Fonctionnalités ciblées |
|--------|------------------------|
| État du jeu | Chaque joueur reçoit 8 cartes ; statut initial EN_ENCHERE |
| Enchères | Tour obligatoire ; contrat doit surpasser le précédent ; tous passent → nouvelle donne ; coinche/surcoinche |
| Règles de jeu | Obligation de suivre la couleur ; obligation de couper ; montée à l'atout ; jouer hors tour → exception |
| Plis et scoring | Valet d'atout > As hors-atout ; calcul des points par carte ; dernier pli +10 pts ; contrat rempli/chuté → scores corrects |

---

### `TarotTest` — logique de scoring + intégration Tarot (Spring Boot + H2)

#### Bloc 1 — `TarotScoringService` (tests unitaires purs, sans BDD)

| Classe imbriquée | Fonctionnalités ciblées |
|-----------------|------------------------|
| `ValeurCartes` | Bouts = 9×2 ; Rois = 9×2 ; Dames = 7×2 ; Cavaliers = 5×2 ; Valets = 3×2 ; atouts ordinaires = 1×2 ; total 78 cartes = 182×2 (91 pts réels) |
| `SeuilsMultiplicateurs` | Seuils par bouts (0→56, 1→51, 2→41, 3→36) ; seuil plafonné à 3 ; multiplicateurs (Petite×1, Garde×2, Garde sans×4, Garde contre×6) ; inconnu → 1 par défaut |
| `CalculScore` | Contrat juste (écart = 0) → score = 25× ; contrat avec écart positif ; contrat chuté → score négatif ; bonus Petit au bout (+10×mult) ; Garde contre ×6 ; 3 bouts abaissent le seuil |
| `CompteBouts` | `compterBouts` détecte les 3 bouts dans une liste ; 0 bout si aucun ; `isBout` précis sur les 3 cartes bouts uniquement |

#### Bloc 2 — `TarotService` (tests d'intégration avec BDD)

| Classe imbriquée | Fonctionnalités ciblées |
|-----------------|------------------------|
| `Distribution4Joueurs` | 18 cartes/joueur ; chien = 6 cartes ; total = 78 ; statut EN_ENCHERE, phaseJeu null ; `getEtatJeuTarot` retourne la main correcte |
| `Distribution3Joueurs` | 24 cartes/joueur ; chien = 6 cartes ; total = 78 |
| `EncheresTarot` | Joueur hors tour → exception ; surenchère obligatoire (PETITE après GARDE → exception) ; typeBid vide → exception ; tous passent → exception "donne annulée" ; GARDE_CONTRE → EN_JEU immédiat (sans chien) ; PETITE + 3 passes → phase CHIEN ; GARDE_SANS + 3 passes → phase CHIEN_VU |

---

### `BackendCartesApplicationTests`

Test de smoke : vérifie que le contexte Spring Boot se charge sans erreur au démarrage. Valide la
cohérence de la configuration globale (beans, JPA, Security, WebSocket).
