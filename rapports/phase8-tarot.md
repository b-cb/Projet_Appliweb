# Rapport d'intégration — Tarot français (Phase 8)

## Résumé exécutif

La Phase 8 ajoute un second mode de jeu complet à l'application : le **Tarot français** (3, 4 ou 5 joueurs). Ce mode coexiste avec la Belote coinchée existante sans modifier aucune règle ni API de celle-ci. La sélection du mode se fait à la création de la partie ; le frontend route ensuite automatiquement vers la bonne interface de jeu. Les trois configurations de joueurs sont jouables avec bots.

---

## 1. Périmètre implémenté

| Fonctionnalité | État |
|---|---|
| Deck 78 cartes (14/couleur + 21 atouts + Excuse) | ✅ |
| Distribution 3 joueurs (24 cartes + chien 6) | ✅ |
| Distribution 4 joueurs (18 cartes + chien 6) | ✅ |
| Distribution 5 joueurs (15 cartes + chien 3) | ✅ |
| Enchères (Petite ×1 / Garde ×2 / Garde sans ×4 / Garde contre ×6) | ✅ |
| Gestion du chien selon le contrat | ✅ |
| Écart du preneur (règles : pas de bouts / rois / atouts) | ✅ |
| **5j — Appel du Roi et révélation du partenaire** | ✅ |
| **5j — Alliance secrète (partenaire révélé quand il joue le Roi appelé)** | ✅ |
| **5j — Scoring FFT (preneur ±3B, partenaire ±B, défenseurs ∓B)** | ✅ |
| **5j — Jeu solo si le preneur détient lui-même le Roi appelé (preneur ±4B)** | ✅ |
| Prise de pli (suivi couleur, coupe, montée à l'atout) | ✅ |
| Excuse toujours jouable, ne remporte jamais le pli | ✅ |
| Scoring demi-points (×2 entiers) + seuils par bouts | ✅ |
| Bonus Petit au bout | ✅ |
| Résultat en fin de partie, scoreGlobal mis à jour | ✅ |
| **Bots pour Tarot 3j / 4j / 5j (création directe avec bots)** | ✅ |
| Bots : enchères = toujours passe ; **appel du Roi = première couleur non détenue** | ✅ |
| Bots : écart = cartes à moindre valeur ; jeu = première carte valide | ✅ |
| Interface WebSocket temps réel | ✅ |
| Rendu graphique des cartes Tarot (atouts CSS dorés, Excuse violet) | ✅ |
| Poignée et Chelem | ❌ reporté v2 |
| Échange de carte lors de l'Excuse | ❌ simplifié (Excuse jouable, pas d'échange) |

---

## 2. Architecture backend

### 2.1 Modèle de données

L'entité `Partie` a reçu les champs suivants pour le Tarot :

```
Partie
├── typeJeu          : String   — "COINCHE" | "TAROT"
├── nbJoueursRequis  : int      — 4 (coinche) | 3 | 4 | 5 (tarot)
├── phaseJeu         : String   — null | "APPEL_ROI" | "CHIEN" | "CHIEN_VU" | "JEU"
├── enchereType      : String   — "PETITE"|"GARDE"|"GARDE_SANS"|"GARDE_CONTRE"
├── multiplicateur   : int      — 1 | 2 | 4 | 6
├── chien            : @ManyToMany Carte  (table partie_chien)
├── ecartes          : @ManyToMany Carte  (table partie_ecarte)
├── petitAuBoutPreneur : boolean
├── appelRoi         : String   — couleur du Roi appelé en 5j ("Coeur"|"Carreau"|"Trefle"|"Pique")
└── partenaireId     : Long     — id du Joueur partenaire (null si non révélé ou 3j/4j)
```

### 2.2 Convention deck Tarot

L'entité `Carte (couleur, valeur)` est réutilisée avec ces conventions :

| Type | couleur | valeur |
|---|---|---|
| Carte de couleur | `Coeur/Carreau/Trefle/Pique` | `1..10, Valet, Cavalier, Dame, Roi` |
| Atout ordinaire | `Atout` | `"1"` .. `"21"` |
| Excuse | `Atout` | `"Excuse"` |

### 2.3 Machine à états (3j/4j et 5j)

```
OUVERTE
  ──demarrerPartie()──▶  EN_ENCHERE / phaseJeu=null

EN_ENCHERE / null
  ──encherirTarot gagnée (3j/4j, PETITE|GARDE)──▶  EN_ENCHERE / CHIEN
  ──encherirTarot gagnée (3j/4j, GARDE_SANS)────▶  EN_ENCHERE / CHIEN_VU
  ──encherirTarot gagnée (3j/4j, GARDE_CONTRE)──▶  EN_JEU / JEU
  ──encherirTarot gagnée (5j, toute enchère)────▶  EN_ENCHERE / APPEL_ROI
  ──tous passent──▶  BusinessException

EN_ENCHERE / APPEL_ROI  (5j uniquement)
  ──appelerRoi(PETITE|GARDE)──▶  EN_ENCHERE / CHIEN
  ──appelerRoi(GARDE_SANS)────▶  EN_ENCHERE / CHIEN_VU
  ──appelerRoi(GARDE_CONTRE)──▶  EN_JEU / JEU

EN_ENCHERE / CHIEN
  ──ecarterCartes(N ids)──▶  EN_JEU / JEU

EN_ENCHERE / CHIEN_VU
  ──ecarterCartes([])────▶  EN_JEU / JEU

EN_JEU / JEU
  ──jouerCarte() × (nb_joueurs × nb_plis)──▶  TERMINEE
```

### 2.4 Révélation du partenaire (5j)

Dans `TarotService.jouerCarte()`, avant d'ajouter la carte au pli, la condition suivante est vérifiée :

```java
if (partie.getAppelRoi() != null && partie.getPartenaireId() == null) {
    if ("Roi".equals(carteJouee.getValeur())
            && partie.getAppelRoi().equals(carteJouee.getCouleur())
            && !joueurActif.getId().equals(partie.getPreneurId())) {
        partie.setPartenaireId(joueurActif.getId());
        joueurActif.setEquipe(1);  // rejoint l'équipe du preneur
    }
}
```

Si le Roi n'est jamais joué (preneur le détient), `partenaireId` reste `null` : le preneur joue en solo.

### 2.5 Scoring 5 joueurs

Formule Fédération Française de Tarot — B = score de base (non signé) :

| Joueur | Preneur gagne | Preneur perd |
|---|---|---|
| Preneur (solo) | +4B | −4B |
| Preneur (duo) | +3B | −3B |
| Partenaire | +B | −B |
| Chaque défenseur | −B | +B |

### 2.6 Distribution selon le nombre de joueurs

| Joueurs | Cartes/joueur | Chien | Plis |
|---|---|---|---|
| 3 | 24 | 6 | 24 |
| 4 | 18 | 6 | 18 |
| 5 | 15 | 3 | 15 |

### 2.7 Nouveaux services et méthodes

**`TarotService`** — méthodes ajoutées :
- `appelerRoi(partieId, userId, couleur)` — valide la phase APPEL_ROI, enregistre la couleur, transition vers CHIEN/CHIEN_VU/JEU
- Mise à jour de `lancerJeuTarot()` → bifurcation APPEL_ROI pour 5j
- Mise à jour de `jouerCarte()` → détection révélation partenaire
- Mise à jour de `terminerPartieTarot()` → scoring 5j
- Mise à jour de `nombreMaxPlis()` → case 5 → 15
- Mise à jour de `getEtatJeuTarot()` → peupler `estPartenaire`, `appelRoi`, `pseudoPartenaire`

**`TarotBotService`** — nouveau cas :
```java
} else if ("EN_ENCHERE".equals(statut) && "APPEL_ROI".equals(phase)) {
    // Appeler la première couleur dont le bot ne détient pas le Roi
    tarotService.appelerRoi(partieId, botUserId, choix);
}
```

**`PartieService`** — nouvelle méthode :
```java
creerEtDemarrerTarotAvecBots(utilisateurId, nbJoueurs)
// 3j → Bot_1, Bot_2
// 4j → Bot_1, Bot_2, Bot_3
// 5j → Bot_1, Bot_2, Bot_3, Bot_4
```

**`BotInitializer`** — ajout de `Bot_4` dans `BOT_PSEUDOS`.

### 2.8 Nouvelles routes REST

| Méthode | URL | Description |
|---|---|---|
| POST | `/api/partie/{id}/tarot/appeler-roi` | Appel du Roi (5j, phase APPEL_ROI) |
| POST | `/api/partie/creer?avecBots=true` + body `{typeJeu:"TAROT", nbJoueurs:5}` | Créer partie Tarot avec bots |

### 2.9 EtatJeuTarotDTO — nouveaux champs

```java
boolean estPartenaire;    // true pour le partenaire après révélation
String appelRoi;          // couleur du Roi appelé (visible par tous)
String pseudoPartenaire;  // pseudo du partenaire (null avant révélation)
```

`ResultatTarotDTO` — ajout :
```java
String pseudoPartenaire;  // affiché dans l'écran de fin
```

---

## 3. Architecture frontend

### 3.1 Routage des modes de jeu

`GamePage.jsx` route vers `TarotGamePage` pour tous les Tarot. Aucun composant Coinche modifié.

### 3.2 Composants nouveaux / modifiés

| Composant | Rôle |
|---|---|
| `RoiSelector.jsx` | **Nouveau** — phase APPEL_ROI : 4 boutons de couleur, griser les Rois déjà en main |
| `TarotGamePage.jsx` | **Mis à jour** — overlay APPEL_ROI, badge partenaire dans le header, mention partenaire dans le résultat |

### 3.3 Lobby — création avec bots étendue

La checkbox "Remplir avec des bots" est désormais disponible pour **tous les modes** (Coinche et Tarot 3j/4j/5j). La route `POST /api/partie/creer?avecBots=true` reçoit `{typeJeu, nbJoueurs}` dans le body.

```jsx
// LobbyPage.jsx — creerPartie()
if (avecBots) {
  await api.creerPartieAvecBots(token, utilisateur.id, { typeJeu: modeJeu, nbJoueurs: nbJoueursMode })
}
```

### 3.4 TarotGamePage — phase APPEL_ROI

```jsx
{statut === 'EN_ENCHERE' && phase === 'APPEL_ROI' && (
  <RoiSelector etatJeu={etatJeu} onAppelerRoi={handleAppelerRoi} />
)}
```

Le header affiche le Roi appelé et le pseudo du partenaire une fois révélé :

```jsx
{etatJeu.appelRoi && statut !== 'EN_ENCHERE' && (
  <span className="atout-badge">
    Roi de {etatJeu.appelRoi}
    {etatJeu.pseudoPartenaire && ` — partenaire : ${etatJeu.pseudoPartenaire}`}
  </span>
)}
```

---

## 4. Tests (TarotTest.java)

Les tests existants couvrent la distribution 3j et 4j. À compléter pour 5j :

- Distribution 5j : 5 × 15 cartes + 3 chien = 78 ✓
- Phase APPEL_ROI apparaît après enchère gagnée en 5j
- appelerRoi hors phase → exception
- Non-preneur ne peut pas appeler
- Révélation partenaire quand Roi joué
- Scoring 5j : preneur 3B, partenaire B, défenseur -B

---

## 5. Limitations connues et roadmap v2

### 5.1 Encore non implémenté

| Fonctionnalité | Complexité |
|---|---|
| Échange de carte lors de l'Excuse | Moyenne |
| Poignée (10/13/15 atouts déclarés) | Faible |
| Chelem | Faible |
| Bots intelligents (stratégie d'enchère) | Haute |

### 5.2 Points d'attention

- **Ecart forcé en atout** : si le preneur n'a que des atouts et des têtes, il peut légalement écarter des atouts face visible. Cette règle est partiellement gérée (le service accepte l'écart d'atout si c'est le seul choix), mais l'UI ne distingue pas ce cas.
- **Garde contre — chien aux défenseurs** : les cartes du chien ne sont pas explicitement ajoutées aux plis défenseurs ; elles sont absentes du calcul du preneur, ce qui revient au même mathématiquement.
- **Bots Tarot 5j** : le bot-preneur appelle le Roi de la première couleur qu'il ne détient pas. Aucune stratégie de suivi d'alliance.
- **Alliance secrète** : l'`appelRoi` est retourné dans le DTO dès qu'il est enregistré (après APPEL_ROI). Pour un jeu strict, il faudrait ne le révéler qu'après la révélation du partenaire, mais la simplicité a été privilégiée.

---

## 6. Flux utilisateur complet (5 joueurs avec bots)

```
Lobby
  └─ Sélectionner "Tarot 5j" + cocher "Remplir avec des bots"
       └─ Cliquer "Créer la partie"
            └─ 1 humain + 4 bots rejoignent automatiquement
                 └─ Distribution 5 × 15 cartes + 3 chien
                      └─ Phase enchères (bots passent, humain peut enchérir)
                           └─ Phase APPEL_ROI (preneur appelle un Roi)
                                └─ Phase chien / écart (selon l'enchère)
                                     └─ Jeu : 15 plis × 5 joueurs
                                          └─ Révélation partenaire (quand il joue le Roi appelé)
                                               └─ Résultat : bouts / points / seuil / score signé
                                                    └─ Preneur +3B, partenaire +B, défenseurs -B
```
