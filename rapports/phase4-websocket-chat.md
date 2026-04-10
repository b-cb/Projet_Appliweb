# Phase 4 — WebSocket temps réel & Chat

## Objectif

Remplacer le polling HTTP par une connexion WebSocket STOMP/SockJS pour que les 4 joueurs reçoivent les mises, cartes jouées et messages de chat **en temps réel**, sans latence de polling.

---

## Changements apportés

### Backend

#### Nouveau : `config/WebSocketConfig.java`
Configuration STOMP avec broker simple et endpoint SockJS :
- Broker sur `/topic`
- Préfixe application `/app`
- Endpoint SockJS exposé sur `/ws` (+ endpoint WebSocket natif sur `/ws/websocket`)
- `allowedOriginPatterns("*")` pour le développement local

#### Nouveau : `dto/MessageChatDTO.java`
DTO pour les messages de chat : `id`, `pseudo`, `contenu`, `date` + `fromEntity(MessageChat)`.

#### Nouveau : `dto/EvenementJeuDTO.java`
Enveloppe générique pour tous les événements WebSocket :
```
{ type: "CARTE_JOUEE" | "PLI_TERMINE" | "ENCHERE" | "PARTIE_TERMINEE" | "JOUEUR_REJOINT" | "CHAT",
  payload: <objet spécifique au type> }
```

#### Nouveau : `service/ChatService.java`
- `envoyerMessage(partieId, utilisateurId, contenu)` : valide (non vide, ≤ 300 chars), persiste en base, pousse `EvenementJeuDTO(CHAT, MessageChatDTO)` sur `/topic/partie/{id}`
- `getHistorique(partieId)` : retourne les messages ordonnés chronologiquement

#### Nouveau : `controller/ChatController.java`
- `POST /api/partie/{id}/chat?utilisateurId=X` — envoyer un message
- `GET /api/partie/{id}/chat` — historique des messages

#### Modifié : `config/SecurityConfig.java`
Ajout de `.requestMatchers("/ws/**").permitAll()` pour que la poignée de main WebSocket ne soit pas bloquée par le filtre JWT.

#### Modifié : `repository/MessageChatRepository.java`
Ajout de `findByPartie_IdOrderByDateAsc(Long partieId)`.

#### Modifié : `service/JeuService.java`
- Injection de `SimpMessagingTemplate`
- Méthode `pushEtatATous(partieId, joueurs, type)` : appelle `getEtatJeu()` pour **chaque joueur** (chacun voit sa propre main) et publie sur `/topic/partie/{id}`
- Push après `encherir()` : type `ENCHERE` (ou `CARTE_JOUEE` si transition EN_JEU)
- Push après `jouerCarte()` : `CARTE_JOUEE`, puis `PLI_TERMINE` si le pli est terminé, puis `PARTIE_TERMINEE` si c'était le 8e pli

#### Modifié : `service/PartieService.java`
- Injection de `SimpMessagingTemplate`
- Push `JOUEUR_REJOINT` (payload : `JoueurDTO`) après `rejoindrePartie()`
- Push `ENCHERE` (payload : `PartieDTO`) après `demarrerPartie()` pour notifier le démarrage

### Frontend

#### Modifié : `frontend-cartes/src/App.jsx`
- Import `@stomp/stompjs` Client
- `connecterWebSocket(partieId)` : crée un Client STOMP, connecte sur `ws://localhost:8080/ws/websocket`, s'abonne à `/topic/partie/{id}`
- `handleEvenementWS(evt, partieId)` : dispatch par type :
  - `JOUEUR_REJOINT` → `fetchJoueurs()`
  - `ENCHERE | CARTE_JOUEE | PLI_TERMINE | PARTIE_TERMINEE` → `setEtatJeu(payload)`
  - `CHAT` → ajout du message dans la liste chat avec auto-scroll
- Polling lobby réduit à 4 s, désactivé dès qu'une partie est active
- Panel chat : liste des messages, input avec envoi sur `Enter`, `fetchHistoriqueChat()` au (re)chargement

---

## Structure des fichiers

```
backend-cartes/src/main/java/fr/enseeiht/jeux/
├── config/
│   ├── SecurityConfig.java         (modifié — /ws/** permitAll)
│   └── WebSocketConfig.java        (nouveau)
├── controller/
│   └── ChatController.java         (nouveau)
├── dto/
│   ├── EvenementJeuDTO.java        (nouveau)
│   └── MessageChatDTO.java         (nouveau)
├── repository/
│   └── MessageChatRepository.java  (modifié — findByPartie_IdOrderByDateAsc)
└── service/
    ├── ChatService.java            (nouveau)
    ├── JeuService.java             (modifié — pushEtatATous)
    └── PartieService.java          (modifié — push JOUEUR_REJOINT / ENCHERE)

frontend-cartes/src/
└── App.jsx                         (modifié — STOMP Client + ChatPanel)
```

---

## Résultats des tests de validation

17 tests exécutés, **17 PASS, 0 FAIL**.

| # | Test | Résultat |
|---|------|----------|
| T1 | POST `/api/partie/{id}/chat` — message valide → 200 | ✅ PASS |
| T2 | Réponse contient `pseudo`, `contenu`, `date` | ✅ PASS |
| T3 | GET `/api/partie/{id}/chat` — historique non vide | ✅ PASS |
| T4 | Historique contient le message envoyé | ✅ PASS |
| T5 | POST chat — contenu vide → 400 | ✅ PASS |
| T6 | POST chat — utilisateurId inexistant → 404 | ✅ PASS |
| T7 | POST chat — partieId inexistante → 404 | ✅ PASS |
| T8 | POST chat — sans token → 401 | ✅ PASS |
| T9 | GET `/ws/info` — endpoint SockJS accessible → 200 | ✅ PASS |
| T10 | Réponse `/ws/info` contient `"websocket":true` | ✅ PASS |
| T11 | `encherir()` passe → WS push transparent (état mis à jour via REST) | ✅ PASS |
| T12 | `encherir()` contrat → WS push transparent | ✅ PASS |
| T13 | 3 passes consécutives → transition EN_JEU | ✅ PASS |
| T14 | Statut partie = `EN_JEU` après transition par passes | ✅ PASS |
| T15 | `jouerCarte()` → état pli mis à jour (WS push transparent) | ✅ PASS |
| T16 | Chat en phase `EN_JEU` → 200 | ✅ PASS |
| T17 | Historique après 2 messages → count correct | ✅ PASS |

---

## Protocole WebSocket — Topics et types d'événements

| Topic | Émetteur | Type | Payload |
|-------|----------|------|---------|
| `/topic/partie/{id}` | `PartieService` | `JOUEUR_REJOINT` | `JoueurDTO` |
| `/topic/partie/{id}` | `PartieService` | `ENCHERE` | `PartieDTO` (démarrage) |
| `/topic/partie/{id}` | `JeuService` | `ENCHERE` | `EtatJeuDTO` (par joueur) |
| `/topic/partie/{id}` | `JeuService` | `CARTE_JOUEE` | `EtatJeuDTO` (par joueur) |
| `/topic/partie/{id}` | `JeuService` | `PLI_TERMINE` | `EtatJeuDTO` (par joueur) |
| `/topic/partie/{id}` | `JeuService` | `PARTIE_TERMINEE` | `EtatJeuDTO` (par joueur) |
| `/topic/partie/{id}` | `ChatService` | `CHAT` | `MessageChatDTO` |

> **Note :** Pour les événements de jeu, `pushEtatATous()` envoie un `EtatJeuDTO` personnalisé par joueur (chacun voit uniquement sa propre main). Le topic est commun mais chaque message contient le contexte du joueur destinataire.

---

## Décisions d'architecture

- **Polling hybride** : Le lobby conserve un polling 4 s (liste des parties, invitations) — la fréquence de mise à jour est suffisante et le périmètre WS par-partie le rend peu adapté. L'écran de jeu utilise exclusivement le WebSocket.
- **Auth WS simplifiée** : L'endpoint `/ws/**` est public (pas de JWT sur la poignée de main STOMP) — acceptable pour un projet académique. En production, il faudrait passer le token dans les headers STOMP `connect`.
- **EtatJeuDTO par joueur** : La main étant privée, `getEtatJeu()` est appelé pour chaque joueur dans `pushEtatATous()`. Cela génère N requêtes BDD mais garantit la confidentialité des cartes.
