# Phase 5 — Refactoring Frontend

## Objectif

Décomposer le fichier `App.jsx` monolithique (~660 lignes) en une architecture modulaire avec pages, composants réutilisables, hooks personnalisés et une couche de service API. Ajouter React Router pour une navigation par URL avec protection des routes.

---

## Changements apportés

### Dépendances

- `react-router-dom` ajouté dans `package.json`

### Nouvelle structure

```
frontend-cartes/src/
├── App.jsx                          (33 lignes — uniquement router + AuthProvider)
├── App.css                          (styles globaux partagés)
├── services/
│   └── api.js                       (toutes les fonctions fetch avec token JWT)
├── hooks/
│   ├── AuthContext.jsx              (contexte React + useAuth())
│   ├── useAuth.js                   (conservé mais non utilisé — remplacé par AuthContext)
│   └── useWebSocket.js              (connexion STOMP, abonnements topic commun + personnel)
├── pages/
│   ├── LoginPage.jsx                (formulaire inscription/connexion)
│   ├── LobbyPage.jsx                (liste parties, détail, invitations)
│   └── GamePage.jsx                 (table de jeu, enchères, cartes, résultat)
└── components/
    ├── ChatPanel.jsx                (panel chat avec auto-scroll)
    ├── HandCards.jsx                (main du joueur avec hover effect)
    ├── BiddingPanel.jsx             (interface d'enchères)
    └── PlayerTable.jsx              (table ronde avec 4 joueurs positionnés)
```

### `App.jsx` — Router + routes protégées

```jsx
<AuthProvider>
  <BrowserRouter>
    <Routes>
      <Route path="/"           element={token ? <Navigate to="/lobby" /> : <LoginPage />} />
      <Route path="/lobby"      element={<RouteProtegee><LobbyPage /></RouteProtegee>} />
      <Route path="/partie/:id" element={<RouteProtegee><GamePage /></RouteProtegee>} />
      <Route path="*"           element={<Navigate to="/" />} />
    </Routes>
  </BrowserRouter>
</AuthProvider>
```

`RouteProtegee` redirige vers `/` si le token JWT est absent.

### `services/api.js`

Couche d'accès au backend : chaque fonction gère le header `Authorization: Bearer <token>` et retourne `{ ok, data }` ou directement la donnée. Fonctions exportées : `inscrire`, `connexion`, `fetchParties`, `fetchPartie`, `creerPartie`, `rejoindrePartie`, `demarrerPartie`, `fetchJoueurs`, `fetchEtatJeu`, `encherir`, `jouerCarte`, `fetchHistoriqueChat`, `envoyerMessage`, `fetchUtilisateurs`, `fetchInvitations`, `envoyerInvitation`, `accepterInvitation`, `refuserInvitation`.

### `hooks/AuthContext.jsx`

Contexte React qui fournit `{ utilisateur, token, login, logout }` à toute l'application via `useAuth()`. Gère la persistance dans `localStorage`.

### `hooks/useWebSocket.js`

Hook encapsulant la connexion STOMP avec deux callbacks :
- `connecter(partieId, userId, { onEtatJeu, onJoueurRejoint, onChat })` — s'abonne au topic commun et au topic personnel
- `deconnecter()` — ferme proprement la connexion

### `components/PlayerTable.jsx`

Calcule la position visuelle (haut/bas/gauche/droite) de chaque joueur relativement à "moi" via `positionVisuelle(maPosition, autrePosition)`. Affiche l'indicateur de tour animé et les cartes du pli courant.

### `components/HandCards.jsx`

Affiche les 8 cartes en ligne. Cartes rouges (♥♦) vs noires (♠♣). Hover effect actif uniquement quand `monTour && statut === 'EN_JEU'`.

### `components/BiddingPanel.jsx`

Interface d'enchères autonome avec ses propres états locaux `contrat` et `couleur`. Désactive les valeurs déjà surpassées dans le select.

### `components/ChatPanel.jsx`

Auto-scroll, envoi sur `Enter`, mise en évidence des messages du joueur courant.

### `pages/GamePage.jsx`

- Charge l'état du jeu via `fetchEtatJeu()` au montage
- Connecte le WebSocket et dispatche les événements vers `setEtatJeu` / `setMessages`
- Overlay enchères centré sur la table pendant `EN_ENCHERE`
- Overlay résultat avec bouton "Retour au lobby" à la fin
- Nettoyage WebSocket au démontage (`useEffect` return → `deconnecter()`)

---

## Routes

| URL | Composant | Accès |
|-----|-----------|-------|
| `/` | `LoginPage` | Public (redirige vers `/lobby` si connecté) |
| `/lobby` | `LobbyPage` | JWT requis |
| `/partie/:id` | `GamePage` | JWT requis |
| `*` | Redirect `/` | — |

---

## Résultats des tests de validation

Parcours utilisateur complet testé manuellement (build `vite build` : ✅ 0 erreur, 0 warning).

| # | Test | Résultat |
|---|------|----------|
| T1 | `npm run build` → 0 erreur | ✅ PASS |
| T2 | `/` sans token → LoginPage | ✅ PASS |
| T3 | Connexion → redirect automatique `/lobby` | ✅ PASS |
| T4 | Accès direct `/lobby` sans token → redirect `/` | ✅ PASS |
| T5 | Accès direct `/partie/1` sans token → redirect `/` | ✅ PASS |
| T6 | Créer une partie → apparaît dans la liste | ✅ PASS |
| T7 | Inviter par pseudo (autocomplétion datalist) | ✅ PASS |
| T8 | Parties TERMINEE filtrées du lobby | ✅ PASS |
| T9 | Démarrer → redirect automatique `/partie/:id` | ✅ PASS |
| T10 | GamePage : cartes visibles en phase EN_ENCHERE | ✅ PASS |
| T11 | GamePage : WebSocket reçu → état mis à jour sans rechargement | ✅ PASS |
| T12 | Retour lobby → déconnexion WebSocket propre | ✅ PASS |

---

## Décisions d'architecture

- **AuthContext plutôt que prop drilling** : le token et l'utilisateur sont accessibles dans toutes les pages sans passer par des props en cascade.
- **Un hook par responsabilité** : `useWebSocket` isole toute la logique STOMP, `api.js` isole tous les appels HTTP. `GamePage` ne contient que l'orchestration.
- **Overlays positionnés en CSS** : le panel d'enchères et le résultat sont superposés à la table via `position: absolute` + `z-index`, sans changer la structure HTML.
- **Nettoyage au démontage** : `GamePage` appelle `deconnecter()` dans le `return` du `useEffect`, ce qui évite les subscriptions orphelines lors des navigations rapides.
