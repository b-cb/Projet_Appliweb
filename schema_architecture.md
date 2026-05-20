# Schéma de l'Architecture de l'Application

```mermaid
graph TB
    subgraph CLIENT["🖥️ Navigateur — Frontend React (Vite)"]
        direction TB
        LP[LoginPage]
        LOB[LobbyPage]
        GP[GamePage\nCoinche]
        TGP[TarotGamePage\nTarot]

        subgraph COMPS["Composants"]
            BP[BiddingPanel]
            TBP[TarotBiddingPanel]
            CP2[ChienPanel]
            PP[PlayerTable]
            HC[HandCards]
            CI[CardImage]
            RS[RoiSelector]
            CHAT[ChatPanel]
        end

        subgraph HOOKS["Services"]
            API_JS["api.js\n(axios + JWT)"]
        end

        GP --> BP
        GP --> PP
        GP --> HC
        GP --> CHAT
        TGP --> TBP
        TGP --> CP2
        TGP --> PP
        TGP --> HC
        TGP --> CI
        TGP --> RS
        TGP --> CHAT
    end

    subgraph COMMS["🔄 Couche de Communication"]
        REST["REST / HTTP\n(JSON + JWT Bearer)"]
        WS["WebSocket STOMP\n/ws endpoint\nTopic: /topic/partie/{id}"]
    end

    subgraph BACKEND["⚙️ Backend — Spring Boot"]
        direction TB

        subgraph SECURITY["Sécurité"]
            JWF[JwtAuthFilter]
            SC[SecurityConfig]
            JS[JwtService]
        end

        subgraph CONTROLLERS["Controllers REST"]
            AC[AuthController\n/api/auth]
            UC[UtilisateurController\n/api/utilisateurs]
            PC[PartieController\n/api/parties]
            IC[InvitationController\n/api/invitations]
            CC[ChatController\n/api/chat]
        end

        subgraph WS_CONFIG["WebSocket"]
            WSC[WebSocketConfig\n/ws broker /topic]
        end

        subgraph SERVICES["Services Métier"]
            AUS[AuthService]
            PS[PartieService]
            JEU[JeuService\n⭐ logique de jeu]
            INV[InvitationService]
            CHATS[ChatService]
            BOT[BotService\nIA bots]
        end

        subgraph REPOS["Repositories JPA"]
            UR[UtilisateurRepo]
            PR[PartieRepo]
            JR[JoueurRepo]
            CR[CarteRepo]
            ER[EnchereRepo]
            PLR[PliRepo]
            MR[MessageChatRepo]
            IR[InvitationRepo]
        end

        JWF --> SC
        AC --> AUS
        PC --> PS
        PC --> JEU
        IC --> INV
        CC --> CHATS
        PS --> PR
        PS --> JR
        PS --> CR
        JEU --> PR
        JEU --> JR
        JEU --> CR
        JEU --> ER
        JEU --> PLR
        JEU --> BOT
        INV --> IR
        INV --> PR
        CHATS --> MR
        AUS --> UR
    end

    subgraph DB["🗄️ Base de Données H2 / PostgreSQL"]
        TABLES["Tables :\nUTILISATEUR · PARTIE · JOUEUR\nCARTE · ENCHERE · PLI\nMESSAGE_CHAT · INVITATION\n+ tables de jointure"]
    end

    CLIENT -- "HTTP REST" --> COMMS
    CLIENT -- "WebSocket STOMP" --> COMMS
    COMMS -- "REST" --> CONTROLLERS
    COMMS -- "STOMP" --> WSC
    WSC -- "broadcast état" --> COMMS
    REPOS --> DB
```

## Flux principaux

| Action | Protocole | Endpoint |
|---|---|---|
| Login / Register | REST POST | `/api/auth/login`, `/api/auth/register` |
| Créer / rejoindre une partie | REST POST | `/api/parties`, `/api/parties/{id}/rejoindre` |
| Jouer une carte | REST POST | `/api/parties/{id}/jouer` |
| Faire une enchère | REST POST | `/api/parties/{id}/enchere` |
| Recevoir la mise à jour d'état | WebSocket STOMP | `/topic/partie/{id}` |
| Envoyer un message chat | REST POST | `/api/chat/{partieId}` |
| Inviter un joueur | REST POST | `/api/invitations` |

## Stack technique

| Couche | Technologie |
|---|---|
| Frontend | React 18, Vite, STOMP.js |
| Backend | Spring Boot 3, Spring Security, Spring Data JPA |
| Authentification | JWT (JSON Web Token) |
| Temps réel | WebSocket + STOMP |
| Base de données | H2 (dev) / PostgreSQL (prod) |
| ORM | Hibernate / JPA |
