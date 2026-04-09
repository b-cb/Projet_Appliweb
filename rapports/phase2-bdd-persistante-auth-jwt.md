# Phase 2 — Base de donnees persistante + Authentification JWT

**Date :** 2026-04-09
**Statut :** Terminee

---

## Objectif

Deux problemes critiques resolus dans cette phase :
1. La base de donnees H2 en memoire perdait toutes les donnees a chaque redemarrage
2. L'authentification etait inexistante (mot de passe `"default_password"` en dur, pas de verification)

**Resultat :** Les comptes utilisateurs sont persistants (fichier H2), les mots de passe sont hashes avec BCrypt, et toutes les requetes API (sauf auth) sont protegees par un token JWT.

---

## Changements realises

### 1. Base de donnees persistante

| Avant | Apres |
|-------|-------|
| `jdbc:h2:mem:cartesdb` (in-memory) | `jdbc:h2:file:./data/cartesdb` (fichier sur disque) |
| `ddl-auto=create-drop` (schema recree a chaque demarrage) | `ddl-auto=update` (schema mis a jour sans perte) |
| Donnees perdues a chaque redemarrage | Donnees persistantes dans `data/cartesdb.mv.db` |

Fichiers modifies :
- `application.properties` : URL datasource + ddl-auto + proprietes JWT
- `.gitignore` : ajout de `backend-cartes/data/`

### 2. Dependances ajoutees (pom.xml)

| Dependance | Version | Role |
|-----------|---------|------|
| `spring-boot-starter-security` | (via parent 4.0.5) | Spring Security : filtres, BCrypt, SecurityContext |
| `jjwt-api` | 0.12.6 | API JWT (generation, parsing) |
| `jjwt-impl` | 0.12.6 | Implementation JWT (runtime) |
| `jjwt-jackson` | 0.12.6 | Serialisation JSON des claims (runtime) |

### 3. Service JWT (`service/JwtService.java`)

Fonctionnalites :
- `generateToken(userId, pseudo)` : cree un token JWT signe HS512 avec expiration 24h
- `parseToken(token)` : decode et verifie la signature
- `getUserId(token)` : extrait l'ID utilisateur du claim `sub`
- `isValid(token)` : retourne true/false sans lancer d'exception

Configuration dans `application.properties` :
- `jwt.secret` : cle HMAC de 64+ caracteres
- `jwt.expiration` : 86400000 ms (24 heures)

### 4. Filtre JWT (`config/JwtAuthFilter.java`)

- Etend `OncePerRequestFilter` : execute une seule fois par requete
- Extrait le header `Authorization: Bearer <token>`
- Si le token est valide, injecte un `UsernamePasswordAuthenticationToken` dans le `SecurityContext`
- Le `principal` contient le `userId` (type Long), accessible dans les controleurs

### 5. Configuration Security (`config/SecurityConfig.java`)

| Regle | Acces |
|-------|-------|
| `POST /api/auth/**` | Public (permitAll) |
| `GET /h2-console/**` | Public (dev uniquement) |
| Tout le reste (`/api/**`) | Token JWT requis |
| Requete sans token | HTTP 401 `{"erreur":"Authentification requise."}` |

Autres configurations :
- CSRF desactive (API stateless)
- Sessions desactivees (`SessionCreationPolicy.STATELESS`)
- `authenticationEntryPoint` personnalise : retourne 401 JSON au lieu du 403 par defaut

### 6. Hashage BCrypt (`service/AuthService.java`)

| Avant | Apres |
|-------|-------|
| `utilisateur.setMdp("default_password")` | `utilisateur.setMdp(passwordEncoder.encode(motDePasse))` |
| Pas de verification de mot de passe | `passwordEncoder.matches(motDePasse, hash)` |
| Connexion = auto-creation sans mdp | Connexion = verification pseudo + mot de passe |

Methodes :
- `inscrire(pseudo, motDePasse)` : cree un compte avec hash BCrypt, refuse les pseudos dupliques
- `connexion(pseudo, motDePasse)` : verifie le pseudo et le mot de passe, retourne l'utilisateur ou lance `BusinessException`

Le message d'erreur est volontairement generique ("Pseudo ou mot de passe incorrect.") pour ne pas reveler si le pseudo existe.

### 7. Endpoints d'auth (`controller/AuthController.java`)

Les endpoints passent de query params a body JSON :

**Avant (Phase 1) :**
```
POST /api/auth/connexion?pseudo=alice
```

**Apres (Phase 2) :**
```
POST /api/auth/inscrire
Content-Type: application/json
{"pseudo": "alice", "motDePasse": "secret123"}
=> 201 {"utilisateur": {...}, "token": "eyJ..."}

POST /api/auth/connexion
Content-Type: application/json
{"pseudo": "alice", "motDePasse": "secret123"}
=> 200 {"utilisateur": {...}, "token": "eyJ..."}
```

### 8. DTOs d'authentification

| DTO | Champs |
|-----|--------|
| `AuthRequest` | `pseudo` (@NotBlank, @Size 3-20), `motDePasse` (@NotBlank, @Size 4-100) |
| `AuthResponse` | `utilisateur` (UtilisateurDTO), `token` (String JWT) |

### 9. Frontend (`App.jsx`)

| Changement | Detail |
|-----------|--------|
| Formulaire de connexion | Ajout champ mot de passe + bouton inscription/connexion |
| Stockage du token | `localStorage.setItem('token', data.token)` |
| Header Authorization | Toutes les requetes `fetch` incluent `Authorization: Bearer <token>` |
| Deconnexion | Supprime le token du localStorage |
| Toggle inscription/connexion | Bouton pour basculer entre les deux modes |

---

## Structure des fichiers apres Phase 2

```
backend-cartes/
    data/
        cartesdb.mv.db               (NOUVEAU — fichier BDD persistant)
    src/main/java/fr/enseeiht/jeux/
        config/
            CorsConfig.java           (inchange)
            JwtAuthFilter.java        (NOUVEAU)
            SecurityConfig.java       (NOUVEAU)
        controller/
            AuthController.java       (MODIFIE — JSON body + token JWT)
            InvitationController.java (inchange)
            PartieController.java     (inchange)
            UtilisateurController.java(inchange)
        dto/
            AuthRequest.java          (NOUVEAU)
            AuthResponse.java         (NOUVEAU)
            InvitationDTO.java        (inchange)
            JoueurDTO.java            (inchange)
            PartieDTO.java            (inchange)
            UtilisateurDTO.java       (inchange)
        exception/                    (inchange)
        modele/                       (inchange)
        repository/                   (inchange)
        service/
            AuthService.java          (MODIFIE — BCrypt + verification mdp)
            InvitationService.java    (inchange)
            JwtService.java           (NOUVEAU)
            PartieService.java        (inchange)
    src/main/resources/
        application.properties        (MODIFIE — H2 fichier + JWT config)
    pom.xml                           (MODIFIE — spring-security + jjwt)

frontend-cartes/src/
    App.jsx                           (MODIFIE — formulaire mdp + token)

.gitignore                            (MODIFIE — ajout backend-cartes/data/)
```

---

## Tests de validation

21 tests executes via `curl`. Tous passent.

### Inscription et connexion

| # | Test | Attendu | Resultat |
|---|------|---------|----------|
| T1 | `POST /api/auth/inscrire {alice, secret123}` | 201 + token JWT | OK |
| T2 | `POST /api/auth/inscrire {bob, pass456}` | 201 + token JWT | OK |
| T3 | `POST /api/auth/inscrire {alice, autre}` (doublon) | 400 "pseudo deja pris" | OK |
| T4 | `POST /api/auth/connexion {alice, secret123}` | 200 + token JWT | OK |
| T5 | `POST /api/auth/connexion {alice, faux}` | 400 "incorrect" | OK |
| T6 | `POST /api/auth/connexion {inconnu, test}` | 400 "incorrect" | OK |

### Validation des entrees

| # | Test | Attendu | Resultat |
|---|------|---------|----------|
| T7 | Inscription pseudo "ab" (2 chars) | 400 "entre 3 et 20" | OK |
| T8 | Inscription mot de passe vide | 400 "obligatoire" | OK |

### Protection JWT

| # | Test | Attendu | Resultat |
|---|------|---------|----------|
| T9 | `GET /api/parties` sans token | 401 | OK |
| T10 | `GET /api/parties` avec token alice | 200 | OK |
| T11 | `POST /api/partie/creer` sans token | 401 | OK |
| T12 | `POST /api/partie/creer` avec token alice | 200 | OK |
| T13 | `GET /api/parties` avec token invalide | 401 | OK |
| T14 | Rejoindre partie avec token alice | 200 | OK |
| T15 | Envoyer invitation avec token alice | 200 | OK |
| T16 | Invitations recues bob avec token bob | 200 | OK |
| T17 | `GET /api/utilisateurs` sans token | 401 | OK |
| T18 | `GET /api/utilisateurs` avec token (pas de champ mdp) | 200 | OK |

### Persistance BDD

| # | Test | Attendu | Resultat |
|---|------|---------|----------|
| T19 | Arret + redemarrage serveur, connexion alice | 200 + token | OK |
| T20 | Mauvais mot de passe apres redemarrage | 400 "incorrect" | OK |
| T21 | Liste utilisateurs apres redemarrage | 200 [alice, bob] | OK |

---

## Points techniques notables

### Message d'erreur generique pour l'auth
Le service retourne "Pseudo ou mot de passe incorrect." dans tous les cas d'echec (pseudo inexistant OU mauvais mot de passe). Cela empeche un attaquant de deviner si un pseudo existe.

### 401 vs 403
Spring Security retourne 403 (Forbidden) par defaut quand il n'y a pas d'authentification. Un `authenticationEntryPoint` personnalise a ete ajoute dans `SecurityConfig` pour retourner 401 (Unauthorized) avec un corps JSON `{"erreur":"Authentification requise."}`.

### Token JWT HS512
L'algorithme HS512 est utilise automatiquement par JJWT car la cle secrete fait plus de 64 octets. Le payload contient `sub` (userId) et `pseudo`. Expiration : 24h.
