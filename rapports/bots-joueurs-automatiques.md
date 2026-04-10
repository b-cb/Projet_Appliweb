# Joueurs automatiques (Bots)

## Objectif

Permettre à un utilisateur seul de lancer immédiatement une partie complète à 4 joueurs en cliquant "🤖 Avec bots" dans le lobby, sans avoir besoin de 3 autres navigateurs ou onglets.

---

## Approche retenue

Les bots sont des **vrais comptes `Utilisateur`** en base de données (`Bot_1`, `Bot_2`, `Bot_3`), marqués `isBot = true`. Ils participent à la partie exactement comme des humains via les mêmes endpoints `JeuService`. Le `BotService` joue à leur place de façon **asynchrone** (`@Async`) dès que c'est leur tour.

Cette approche évite de dupliquer la logique de jeu — les règles Belote (suivi couleur, montée atout, calcul des points) s'appliquent identiquement aux bots.

---

## Fichiers créés / modifiés

### Backend

| Fichier | Rôle |
|---------|------|
| `modele/Utilisateur.java` | Ajout du champ `boolean bot` + getter/setter |
| `config/BotInitializer.java` | `ApplicationRunner` qui crée `Bot_1/2/3` au démarrage si absents |
| `service/BotService.java` | Logique de jeu automatique (`@Async`) |
| `service/PartieService.java` | Nouvelle méthode `creerEtDemarrerAvecBots(utilisateurId)` + injection `BotService` |
| `service/JeuService.java` | Injection `BotService` + appel `botService.jouerSiTourDuBot()` après `encherir()` et `jouerCarte()` |
| `controller/PartieController.java` | `POST /api/partie/creer?avecBots=true&utilisateurId=X` |
| `BackendCartesApplication.java` | Ajout de `@EnableAsync` |

### Frontend

| Fichier | Rôle |
|---------|------|
| `services/api.js` | `creerPartieAvecBots(token, utilisateurId)` |
| `pages/LobbyPage.jsx` | Bouton "🤖 Avec bots" + fonction `creerAvecBots()` |
| `App.css` | Styles `.btn-bots` (violet) et `.create-btns` |

---

## Séquence d'une partie avec bots

```
[Humain clique "Avec bots"]
        │
        ▼
POST /api/partie/creer?avecBots=true&utilisateurId=X
        │
        ▼
PartieService.creerEtDemarrerAvecBots()
  1. creerPartie()          → OUVERTE
  2. rejoindrePartie(humain) → position 0, équipe 1
  3. rejoindrePartie(Bot_1)  → position 1, équipe 2
  4. rejoindrePartie(Bot_2)  → position 2, équipe 1
  5. rejoindrePartie(Bot_3)  → position 3, équipe 2
  6. demarrerPartie()        → EN_ENCHERE, cartes distribuées
        │
        ▼
Frontend → navigate("/partie/:id")
        │
[L'humain voit ses cartes, c'est son tour d'enchérir en position 0]
        │
[L'humain enchérit ou passe]
        │
        ▼
JeuService.encherir() → push WebSocket → botService.jouerSiTourDuBot() [async]
        │
        ▼
BotService (thread séparé, délai 400ms puis 600ms entre coups) :
  - Si EN_ENCHERE : premier bot sans contrat → annonce 80 Coeur, sinon passe
  - Si EN_JEU     : joue la première carte légale de sa main
        │
        ▼
[Répète jusqu'à ce que ce soit de nouveau le tour de l'humain]
```

---

## Stratégie des bots

La stratégie est intentionnellement **minimale** (objectif : test fonctionnel, pas IA) :

| Phase | Comportement |
|-------|-------------|
| **Enchères** | Le premier bot à parler annonce **80 à Coeur** si aucun contrat n'existe encore. Les bots suivants passent systématiquement. |
| **Jeu** | Joue la **première carte légale** de sa main (index 0). Si cette carte viole une règle (suivi couleur, montée atout), essaie la suivante jusqu'à en trouver une valide. |

Cela garantit que la partie se termine toujours sans blocage, tout en respectant les règles Belote.

---

## Gestion des dépendances circulaires

`JeuService` et `BotService` s'appellent mutuellement (le bot appelle `jeuService.encherir()` / `jouerCarte()`, et JeuService appelle `botService.jouerSiTourDuBot()`). De même entre `PartieService` et `BotService`.

Résolution : `@Lazy` sur l'injection de `BotService` dans `JeuService` et `PartieService`. Spring instancie le bean BotService uniquement à la première utilisation, cassant le cycle.

---

## Résultats des builds

```
Backend  : ./mvnw compile → BUILD SUCCESS (0 erreur)
Frontend : npm run build  → ✓ built in 140ms (0 erreur)
```

---

## Utilisation

1. Se connecter avec son compte
2. Dans le lobby, cliquer **"🤖 Avec bots"** (bouton violet à côté de "+ Créer")
3. Le jeu démarre immédiatement — on arrive directement sur l'écran de jeu
4. La partie se joue en enchérissant et en jouant ses cartes ; les 3 bots répondent automatiquement dans les secondes qui suivent

> **Note :** Les bots `Bot_1`, `Bot_2`, `Bot_3` apparaissent dans la liste des utilisateurs mais ne peuvent pas se connecter (mot de passe inutilisable). Leurs parties ne comptent pas dans leur `scoreGlobal`.
