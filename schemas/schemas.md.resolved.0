# Schémas du Projet Appliweb

## 1. Schéma de la Base de Données

```mermaid
erDiagram
    UTILISATEUR {
        Long id PK
        String pseudo UK
        String mdp
        int scoreGlobal
        boolean bot
    }

    PARTIE {
        Long id PK
        String statut
        String typeJeu
        int nbJoueursRequis
        String atout
        int scoreA
        int scoreB
        int scoreGlobalA
        int scoreGlobalB
        int tourJoueurIndex
        int contratValeur
        String contratCouleur
        Long preneurId
        int passesConsecutives
        int numPliCourant
        int coinche
        int maxDonnes
        int maxPoints
        int donneActuelle
        String phaseJeu
        String enchereType
        int multiplicateur
        String appelRoi
        Long partenaireId
        String poigneeDeclaree
        boolean petitAuBoutPreneur
        boolean petitSecDetecte
    }

    JOUEUR {
        Long id PK
        int equipe
        int position
        int scorePartie
        Long utilisateur_id FK
        Long partie_id FK
    }

    CARTE {
        Long id PK
        String valeur
        String couleur
    }

    ENCHERE {
        Long id PK
        int contrat
        String couleur
        boolean passe
        String typeBid
        Long partie_id FK
        Long preneur_id FK
    }

    PLI {
        Long id PK
        int numTour
        int gagnantEquipe
        int pointsPli
        int joueurOuvreurIndex
        Long partie_id FK
    }

    MESSAGE_CHAT {
        Long id PK
        String contenu
        LocalDateTime date
        Long utilisateur_id FK
        Long partie_id FK
    }

    INVITATION {
        Long id PK
        String statut
        Long expediteur_id FK
        Long destinataire_id FK
        Long partie_id FK
    }

    %% Tables de jointure Many-to-Many
    JOUEUR_CARTE {
        Long joueur_id FK
        Long carte_id FK
    }

    PLI_CARTE {
        Long pli_id FK
        Long carte_id FK
    }

    PARTIE_CHIEN {
        Long partie_id FK
        Long carte_id FK
    }

    PARTIE_ECARTE {
        Long partie_id FK
        Long carte_id FK
    }

    %% Relations
    UTILISATEUR ||--o{ JOUEUR : "joue en tant que"
    PARTIE ||--o{ JOUEUR : "contient"
    JOUEUR }o--o{ CARTE : "JOUEUR_CARTE"
    PARTIE ||--o{ PLI : "contient"
    PLI }o--o{ CARTE : "PLI_CARTE"
    PARTIE }o--o{ CARTE : "PARTIE_CHIEN (chien)"
    PARTIE }o--o{ CARTE : "PARTIE_ECARTE (écartés)"
    PARTIE ||--o{ ENCHERE : "contient"
    JOUEUR ||--o{ ENCHERE : "fait"
    PARTIE ||--o{ MESSAGE_CHAT : "contient"
    UTILISATEUR ||--o{ MESSAGE_CHAT : "envoie"
    PARTIE ||--o{ INVITATION : "génère"
    UTILISATEUR ||--o{ INVITATION : "expéditeur"
    UTILISATEUR ||--o{ INVITATION : "destinataire"
```

---

## 2. Schéma de l'Architecture de l'Application

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

        subgraph HOOKS["Hooks"]
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

    %% Flux
    CLIENT -- "HTTP REST" --> COMMS
    CLIENT -- "WebSocket STOMP" --> COMMS
    COMMS -- "REST" --> CONTROLLERS
    COMMS -- "STOMP" --> WSC
    WSC -- "broadcast état" --> COMMS
    REPOS --> DB
```

---

## Résumé des flux principaux

| Action | Protocole | Endpoint |
|---|---|---|
| Login / Register | REST POST | `/api/auth/login`, `/api/auth/register` |
| Créer / rejoindre une partie | REST POST | `/api/parties`, `/api/parties/{id}/rejoindre` |
| Jouer une carte | REST POST | `/api/parties/{id}/jouer` |
| Faire une enchère | REST POST | `/api/parties/{id}/enchere` |
| Recevoir la mise à jour d'état | WebSocket STOMP | `/topic/partie/{id}` |
| Envoyer un message chat | REST POST | `/api/chat/{partieId}` |
| Inviter un joueur | REST POST | `/api/invitations` |
