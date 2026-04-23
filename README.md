# Projet Application Web — Belote Coinchée en Ligne

Jeu de cartes multijoueur (Belote coinchée, 4 joueurs, 2 équipes) développé dans le cadre du cours Application Web à l'ENSEEIHT.

**Stack :** React 19 (Vite) + Spring Boot 4 / Java 21 + H2 (fichier persistant) + Spring Security / JWT

---

## Lancement rapide

### Prérequis

- Java 21+
- Node.js 18+ et npm

### Option A — Script tout-en-un (recommandé)

```bash
chmod +x start.sh
./start.sh
```

Le script démarre le backend (port 8080) et le frontend (port 5173) simultanément, affiche les URLs locales et réseau, et arrête proprement les deux serveurs sur Ctrl+C.

### Option B — Démarrage manuel (si le script pose problème)

**Terminal 1 — Backend :**
```bash
cd backend-cartes
./mvnw spring-boot:run
```

Attendre le message `Started BackendCartesApplication` (environ 10-15 secondes).

**Terminal 2 — Frontend :**
```bash
cd frontend-cartes
npm install          # uniquement au premier lancement
node scripts/generate-tarot-sprite.js > public/tarot-cards.svg  # regénère le sprite si besoin
npm run dev -- --host
```

**Accès :**
- Frontend : http://localhost:5173
- Backend API : http://localhost:8080
- Console H2 (dev) : http://localhost:8080/h2-console (JDBC URL : `jdbc:h2:file:./data/cartesdb`, user : `sa`, pas de mot de passe)

> **Note :** La base de données est persistante (`backend-cartes/data/cartesdb.mv.db`). Si vous changez le schéma (ajout de colonnes), supprimez ce fichier avant de relancer.

---

## Jouer une partie — Guide pas à pas

### 1. Créer des comptes

Depuis http://localhost:5173, cliquer sur "Pas de compte ? S'inscrire" et créer 4 comptes (pseudo 3-20 caractères, mot de passe min. 4 caractères). Il faut 4 joueurs dans des onglets/navigateurs différents.

### 2. Créer une partie

Avec l'un des comptes, cliquer sur **+ Créer une partie** dans le lobby. Le créateur est automatiquement ajouté à la partie.

### 3. Inviter ou rejoindre

Les 3 autres joueurs peuvent :
- Cliquer sur **Rejoindre** directement depuis la liste du lobby
- Ou recevoir une invitation via le panel "Inviter un joueur" dans le détail de la partie

### 4. Démarrer la partie

Quand 4 joueurs sont présents, le bouton **Démarrer la partie** apparaît. La partie passe en phase d'enchères.

### 5. Phase d'enchères

Les joueurs enchérissent à tour de rôle. Le joueur en position 0 ouvre.

- **Enchérir** : choisir une valeur (80 à 160, multiple de 10) et une couleur (Coeur, Carreau, Trefle, Pique), puis cliquer "Enchérir"
- **Passer** : cliquer "Passer"
- Après 3 passes consécutives depuis la dernière enchère réelle, la partie passe en phase de jeu avec l'atout fixé

### 6. Phase de jeu

Chaque joueur voit ses 8 cartes. Les cartes jouables sont cliquables (les autres sont désactivées). Les règles Belote sont appliquées automatiquement :

| Règle | Description |
|-------|-------------|
| Suivi de couleur | Vous devez jouer la couleur demandée si vous l'avez |
| Coupure | Si vous n'avez pas la couleur demandée mais avez de l'atout, vous devez couper |
| Montée | Si vous jouez atout alors que l'atout est demandé, vous devez jouer plus fort si vous pouvez |

### 7. Résultat

Après 8 plis, les scores sont calculés automatiquement :

- **Contrat rempli** : le preneur a fait son contrat — les scores des plis sont conservés
- **Contrat chuté** : les défenseurs gagnent `160 + valeur_contrat` pts, le preneur marque 0
- Le `scoreGlobal` des joueurs de l'équipe gagnante est incrémenté

---

## Rendu des cartes

Les cartes sont rendues à partir de deux sprites SVG dans `frontend-cartes/public/` :

| Sprite | Contenu | Source |
|--------|---------|--------|
| `svg-cards.svg` | 52 cartes ordinaires + dos | package npm `svg-cards@4` |
| `tarot-cards.svg` | 22 atouts (1-21 + Excuse) + 4 Cavaliers | généré par `scripts/generate-tarot-sprite.js` |

Pour régénérer `tarot-cards.svg` (après modification du style des cartes) :

```bash
cd frontend-cartes
node scripts/generate-tarot-sprite.js > public/tarot-cards.svg
```

Aucune dépendance npm supplémentaire n'est nécessaire : le script utilise Node.js natif.

---

## Architecture

```
Projet_Appliweb/
├── backend-cartes/          # Spring Boot 4 / Java 21
│   ├── src/main/java/fr/enseeiht/jeux/
│   │   ├── config/          # CORS, SecurityConfig, JwtAuthFilter
│   │   ├── controller/      # AuthController, PartieController,
│   │   │                    # InvitationController, UtilisateurController, JeuController
│   │   ├── dto/             # UtilisateurDTO, JoueurDTO, PartieDTO, InvitationDTO,
│   │   │                    # CarteDTO, EnchereDTO, EtatJeuDTO, ResultatDTO, AuthRequest/Response
│   │   ├── exception/       # GlobalExceptionHandler, BusinessException, ResourceNotFoundException
│   │   ├── modele/          # Utilisateur, Partie, Joueur, Carte, Pli, Enchere,
│   │   │                    # Invitation, MessageChat (8 entités JPA)
│   │   ├── repository/      # Interfaces Spring Data JPA
│   │   └── service/         # AuthService, JwtService, PartieService,
│   │                        # InvitationService, JeuService
│   ├── data/                # Base H2 persistante (gitignorée)
│   └── src/main/resources/
│       └── application.properties
├── frontend-cartes/         # React 19 + Vite
│   └── src/
│       └── App.jsx          # SPA unique (lobby + jeu)
├── rapports/                # Rapports de phase (markdown)
├── start.sh / stop.sh
└── Projet_Appli_Web-1.pdf   # Sujet
```

### Entités JPA (8 entités — exigence : > 7)

| Entité | Rôle |
|--------|------|
| `Utilisateur` | Compte joueur (pseudo, mdp BCrypt, scoreGlobal) |
| `Partie` | Instance de jeu (statut, atout, scores, suivi du tour) |
| `Joueur` | Liaison Utilisateur ↔ Partie (équipe, position, main) |
| `Carte` | Carte à jouer (valeur, couleur) |
| `Pli` | Groupe de 4 cartes jouées dans un tour |
| `Enchere` | Contrat ou passe d'un joueur en phase d'enchères |
| `Invitation` | Demande d'un joueur à rejoindre une partie |
| `MessageChat` | Message de chat en jeu (modèle prêt, endpoint Phase 4) |

---

## État d'avancement

| Phase | Contenu | Statut |
|-------|---------|--------|
| Phase 1 | Refactoring backend (contrôleurs, services, DTOs, gestion d'erreurs) | Terminée |
| Phase 2 | BDD persistante H2 + Authentification JWT + BCrypt | Terminée |
| Phase 3 | Logique de jeu complète (enchères, plis, scores, fin de partie) | Terminée |
| Phase 4 | WebSocket temps réel (STOMP/SockJS) + Chat | A faire |
| Phase 5 | Refactoring frontend (composants, React Router, UI jeu) | A faire |
| Phase 6 | Tests formels (JUnit, MockMvc) | A faire |

Les rapports de chaque phase sont dans [rapports/](rapports/).

---

## Alignement avec le sujet

| Exigence du sujet | Couverture |
|-------------------|------------|
| MVC architecture | Contrôleurs → Services → Repositories |
| SpringBoot back-end | Spring Boot 4 / Java 21 |
| Plus de 7 entités | 8 entités JPA |
| Performance consideration | API stateless (JWT), pas de session serveur, polling 3s (WebSocket Phase 4) |
| Players identification | Inscription + connexion JWT + BCrypt (Phase 2) |
| Invitations between players | `InvitationController` + `InvitationService` (Phase 1) |
| Real-time game | Polling 3s actuel — WebSocket STOMP prévu Phase 4 |
| Chat | Entité `MessageChat` en place — endpoint prévu Phase 4 |
| History / Ranking | `scoreGlobal` par utilisateur — endpoint classement prévu Phase 5 |

**Deadline soumission finale : 19/05/2026**
