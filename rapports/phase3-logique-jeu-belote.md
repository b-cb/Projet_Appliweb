# Phase 3 — Logique de jeu Belote coinchée

**Date :** 2026-04-09
**Statut :** Terminée

---

## Objectif

Implémenter la logique de jeu complète de la Belote coinchée (4 joueurs, 2 équipes) :
1. Phase d'enchères (contrat sur une couleur)
2. Phase de jeu (8 plis, règles de suivi de couleur, obligation de couper et de monter)
3. Évaluation de chaque pli et calcul des scores
4. Résolution finale (contrat rempli ou chuté, scoreGlobal des gagnants)

**Résultat :** Une partie complète peut être jouée de bout en bout via l'API. Le frontend affiche les enchères, la main du joueur, le pli en cours et le résultat final.

---

## Changements réalisés

### 1. Enrichissement des modèles

#### `modele/Partie.java`

Champs ajoutés pour le suivi de l'état du jeu :

| Champ | Type | Rôle |
|-------|------|------|
| `tourJoueurIndex` | int | Position (0-3) du joueur dont c'est le tour |
| `contratValeur` | int | Valeur de l'enchère retenue (80-160) |
| `contratCouleur` | String | Couleur de l'atout retenu |
| `preneurId` | Long | ID du `Joueur` ayant pris le contrat |
| `passesConsecutives` | int | Compteur de passes depuis la dernière enchère réelle |
| `numPliCourant` | int | Numéro du pli en cours (1-8) |

#### `modele/Pli.java`

| Champ | Type | Rôle |
|-------|------|------|
| `gagnantEquipe` | int | Équipe (1 ou 2) ayant remporté ce pli |
| `pointsPli` | int | Points attribués à ce pli |
| `joueurOuvreurIndex` | int | Position du joueur qui a ouvert le pli |

#### `modele/Enchere.java`

| Champ | Type | Rôle |
|-------|------|------|
| `passe` | boolean | true si le joueur a passé (contrat=0, couleur=null) |

### 2. Enrichissement des repositories

| Repository | Méthodes ajoutées |
|-----------|------------------|
| `PliRepository` | `findByPartie_IdOrderByNumTourAsc`, `findByPartie_IdAndNumTour`, `countByPartie_Id` |
| `EnchereRepository` | `findByPartie_IdOrderByIdAsc`, `countByPartie_Id` |

### 3. Nouveaux DTOs

| DTO | Rôle |
|-----|------|
| `CarteDTO` | Représentation d'une carte (id, valeur, couleur) |
| `EnchereDTO` | Une enchère (passe ou contrat+couleur+joueur) |
| `ResultatDTO` | Résultat final (scores, contrat rempli/chuté, vainqueur) |
| `EtatJeuDTO` | Vue complète pour un joueur : ma main, pli courant, tour, enchères, résultat |

`EtatJeuDTO` contient une inner class `CartePliDTO` associant une carte au joueur qui l'a jouée dans le pli courant.

### 4. JeuService — Logique Belote

Fichier : `service/JeuService.java`

#### Tables de valeurs des cartes

```
Atout : Valet=20, 9=14, As=11, 10=10, Roi=4, Dame=3, 8=0, 7=0
Hors atout : As=11, 10=10, Roi=4, Dame=3, Valet=2, 9=0, 8=0, 7=0
```

Ordre de force à l'atout (croissant) : `7, 8, Dame, Roi, 10, As, 9, Valet`
Ordre de force hors atout (croissant) : `7, 8, 9, Valet, Dame, Roi, 10, As`

#### `getEtatJeu(partieId, utilisateurId)`

Retourne un `EtatJeuDTO` complet : statut, scores, tour courant, main du joueur appelant, cartes du pli courant avec leur auteur, historique des enchères, résultat si terminée.

#### `encherir(partieId, utilisateurId, contrat, couleur, passe)`

Validations :
- Vérification que c'est bien le tour du joueur
- Si enchère réelle : contrat entre 80-160, multiple de 10, couleur valide, doit surenchérir sur le contrat précédent
- Si passe : incrémente `passesConsecutives`

Transition automatique vers `EN_JEU` :
- Détecte 3 passes consécutives depuis la dernière enchère réelle
- Initialise l'atout, `numPliCourant=1`, donne l'ouverture au preneur

#### `jouerCarte(partieId, utilisateurId, carteId)`

Validations :
1. Vérification du tour
2. Vérification que la carte est dans la main du joueur
3. **Règles de couleur** :
   - Si le joueur possède la couleur demandée → obligation de suivre
   - S'il n'a pas la couleur demandée mais a de l'atout → obligation de couper
   - S'il joue atout et que la couleur demandée est l'atout → obligation de monter (jouer un atout plus fort si possible)

#### `terminerPli(partie, pli, joueurs)`

Algorithme :
1. Parcourir les 4 cartes dans l'ordre de jeu
2. La première carte à l'atout rencontrée gagne ; si plusieurs atouts, le plus fort gagne
3. Si aucun atout, la carte la plus forte de la couleur ouverte gagne
4. Calculer les points selon les tables ci-dessus
5. Dernier pli (numéro 8) : ajouter 10 points bonus
6. Ajouter les points à l'équipe gagnante, passer au pli suivant

#### `terminerPartie(partie, joueurs)`

- Trouver l'équipe du preneur
- Si son score < contrat → **chute** : défenseurs gagnent `160 + contrat`, preneur 0
- Sinon → contrat rempli : scores déjà calculés restent
- Passe le statut à `TERMINEE`
- Incrémente `scoreGlobal` de chaque joueur de l'équipe gagnante

### 5. Modification de `PartieService.demarrerPartie()`

| Avant | Après |
|-------|-------|
| `partie.setStatut("EN_COURS")` | `partie.setStatut("EN_ENCHERE")` |
| Distribution des cartes uniquement | + `tourJoueurIndex=0`, `passesConsecutives=0`, `numPliCourant=0` |

### 6. JeuController

Fichier : `controller/JeuController.java`

| Endpoint | Description |
|----------|-------------|
| `GET /api/partie/{id}/etat?utilisateurId=X` | État complet du jeu pour cet utilisateur |
| `POST /api/partie/{id}/encherir?utilisateurId=X` | Body : `{passe:true}` ou `{passe:false,contrat:80,couleur:"Coeur"}` |
| `POST /api/partie/{id}/jouer?utilisateurId=X` | Body : `{carteId:42}` |

### 7. Frontend (`App.jsx`)

Changements :
- Nouvel écran de jeu actif (statuts `EN_ENCHERE`, `EN_JEU`, `TERMINEE`)
- Polling de `GET /api/partie/{id}/etat` toutes les 3s quand une partie est active
- Interface d'enchères : sélection contrat (80-160) + couleur + boutons "Enchérir" / "Passer"
- Main du joueur : boutons cliquables pour chaque carte, désactivés si ce n'est pas son tour
- Pli courant : affiche les cartes jouées avec le pseudo et l'équipe de chaque joueur
- Résultat final : contrat, preneur, scores, équipe gagnante
- Bouton "Aller au jeu" depuis le lobby pour les parties en cours

---

## Structure des fichiers après Phase 3

```
backend-cartes/src/main/java/fr/enseeiht/jeux/
    modele/
        Partie.java          (MODIFIÉ — 6 champs ajoutés)
        Pli.java             (MODIFIÉ — 3 champs ajoutés)
        Enchere.java         (MODIFIÉ — champ passe ajouté)
        Carte.java           (inchangé)
        Joueur.java          (inchangé)
        Utilisateur.java     (inchangé)
        Invitation.java      (inchangé)
    repository/
        PliRepository.java   (MODIFIÉ — 3 méthodes ajoutées)
        EnchereRepository.java (MODIFIÉ — 2 méthodes ajoutées)
        autres               (inchangés)
    dto/
        CarteDTO.java        (NOUVEAU)
        EnchereDTO.java      (NOUVEAU)
        EtatJeuDTO.java      (NOUVEAU — avec inner class CartePliDTO)
        ResultatDTO.java     (NOUVEAU)
        autres               (inchangés)
    service/
        JeuService.java      (NOUVEAU — ~300 lignes)
        PartieService.java   (MODIFIÉ — demarrerPartie passe en EN_ENCHERE)
        autres               (inchangés)
    controller/
        JeuController.java   (NOUVEAU)
        autres               (inchangés)

frontend-cartes/src/
    App.jsx                  (MODIFIÉ — écran de jeu complet)
```

---

## Tests de validation

24 tests exécutés via `curl`. Tous passent.

### Lobby et démarrage

| # | Test | Attendu | Résultat |
|---|------|---------|----------|
| T1 | `POST /api/partie/creer` | `{"statut":"OUVERTE"}` | OK |
| T2 | Alice rejoint | `equipe=1, position=0` | OK |
| T3 | Bob rejoint | `equipe=2, position=1` | OK |
| T4 | Charlie rejoint | `equipe=1, position=2` | OK |
| T5 | Dave rejoint | `equipe=2, position=3` | OK |
| T6 | Démarrer la partie | `statut=EN_ENCHERE` | OK |
| T7 | `GET /etat?utilisateurId=1` | `statut=EN_ENCHERE, 8 cartes en main, tourPseudo=alice` | OK |

### Enchères

| # | Test | Attendu | Résultat |
|---|------|---------|----------|
| T8 | Bob enchérit à la place d'Alice | 400 "Ce n'est pas votre tour" | OK |
| T9 | Alice enchère contrat=70 (invalide) | 400 "entre 80 et 160" | OK |
| T10 | Alice enchérit 80 Coeur | `contratValeur=80, tourPseudo=bob` | OK |
| T11 | Bob surenchère 80 (déjà pris) | 400 "doit être supérieur" | OK |
| T12 | Bob passe | `tourPseudo=charlie` | OK |
| T13 | Charlie passe | `tourPseudo=dave` | OK |
| T14 | Dave passe (3 passes → fin enchères) | `statut=EN_JEU, atout=Coeur, tourPseudo=alice` | OK |

### Jeu — règles de validité

| # | Test | Attendu | Résultat |
|---|------|---------|----------|
| T15 | Bob joue hors tour | 400 "Ce n'est pas votre tour" | OK |
| T16 | Alice joue une carte hors main | 400 "pas dans votre main" | OK |
| T18 | Bob joue Pique au lieu de Coeur (a du Coeur) | 400 "Vous devez suivre la couleur" | OK |
| T21-bis | Dave joue 10/Coeur alors qu'il peut monter | 400 "Vous devez monter à l'atout" | OK |
| T23-bis | Charlie joue Carreau sans couper (a un atout) | 400 "Vous devez couper avec un atout" | OK |

### Jeu — plis et scores

| # | Test | Attendu | Résultat |
|---|------|---------|----------|
| T17 | Alice joue Roi/Coeur (1ère carte) | pliCourant=[alice:Roi/Coeur] | OK |
| T19 | Bob joue Dame/Coeur (suit) | pliCourant=[alice:Roi/Coeur, bob:Dame/Coeur] | OK |
| T20 | Charlie joue As/Coeur (atout + suit) | pliCourant=[..., charlie:As/Coeur] | OK |
| T22 | Dave joue Valet/Coeur (monte) → fin pli 1 | `numPliCourant=2, scoreB=38` | OK |

Score pli 1 : Valet(20)+As(11)+Roi(4)+Dame(3) = 38 pts → Équipe B ✓

### Fin de partie

| # | Test | Attendu | Résultat |
|---|------|---------|----------|
| T23 | Après 8 plis | `statut=TERMINEE, sA=0, sB=240` | OK |
| T24 | Contrat chuté (alice preneur, équipe 1 < 80 pts) | `contratRempli=false, gagnant=Equipe2` | OK |
| T25 | scoreGlobal mis à jour | bob=1, dave=1, alice=0, charlie=0 | OK |

**Score de chute :** Équipe A (preneur) → 0 pts. Équipe B → 160+80=240 pts ✓

---

## Points techniques notables

### Règle de montée à l'atout
Quand la couleur ouverte est l'atout et qu'un joueur joue atout, il doit monter (jouer plus fort que le meilleur atout déjà dans le pli) s'il le peut. L'algorithme compare les positions dans `ORDRE_ATOUT = [7, 8, Dame, Roi, 10, As, 9, Valet]`.

### Détection de la fin des enchères
La méthode `doitCommencerJeu()` parcourt la liste des enchères en ordre inverse et compte les passes consécutives depuis la dernière enchère réelle. Si ≥ 3, la partie passe en `EN_JEU`.

### Calcul du gagnant du pli
L'algorithme donne priorité absolue aux cartes à l'atout : si au moins un atout est joué, le plus fort atout gagne. Sinon, la carte la plus forte de la couleur ouverte gagne. Les cartes d'une autre couleur (ni atout, ni couleur ouverte) ne peuvent jamais gagner le pli.

### Chute du contrat
Si le preneur ne fait pas son contrat : son équipe marque 0, les défenseurs marquent `160 + valeur_contrat`. Cela crée une incitation forte à ne pas prendre un contrat risqué.

### Suppression de la BDD au changement de schéma
Avec `ddl-auto=update`, Hibernate ne peut pas ajouter des colonnes `NOT NULL` sur une table avec des lignes existantes. La BDD H2 (`data/cartesdb.mv.db`) a été supprimée avant le démarrage pour forcer la recréation du schéma propre.
