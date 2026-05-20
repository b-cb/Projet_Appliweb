# Schéma de la Base de Données

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

## Tables de jointure Many-to-Many

| Table | Description |
|---|---|
| `joueur_carte` | Cartes en main d'un joueur |
| `pli_carte` | Cartes jouées dans un pli |
| `partie_chien` | Cartes du chien (Tarot) |
| `partie_ecarte` | Cartes écartées par le preneur (Tarot) |
