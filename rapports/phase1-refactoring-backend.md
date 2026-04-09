# Phase 1 — Refactoring Backend

**Date :** 2026-04-09
**Statut :** Terminee

---

## Objectif

Transformer le backend monolithique (un seul `GameController.java` de 309 lignes contenant toute la logique) en une architecture en couches propre : Controllers / Services / DTOs, avec gestion d'erreurs centralisee et CORS restrictif.

---

## Changements realises

### 1. Suppression du controleur monolithique

| Fichier supprime | Raison |
|------------------|--------|
| `controller/GameController.java` (309 lignes) | Remplace par 4 controleurs specialises |

### 2. Nouveaux controleurs (package `controller/`)

| Fichier | Endpoints | Responsabilite |
|---------|-----------|----------------|
| `AuthController.java` | `POST /api/auth/connexion`, `POST /api/auth/inscrire` | Authentification avec validation `@NotBlank @Size(min=3, max=20)` |
| `PartieController.java` | `POST /api/partie/creer`, `GET /api/parties`, `GET /api/partie/{id}`, `POST /api/partie/{id}/rejoindre`, `POST /api/partie/{id}/demarrer`, `GET /api/partie/{id}/joueurs` | Gestion des parties |
| `InvitationController.java` | `POST /api/invitation/envoyer`, `GET /api/invitation/recues`, `POST /api/invitation/{id}/accepter`, `POST /api/invitation/{id}/refuser`, `GET /api/invitation/partie/{id}` | Systeme d'invitations |
| `UtilisateurController.java` | `GET /api/utilisateur/{id}`, `GET /api/utilisateurs` | Consultation des profils |

Les controleurs ne contiennent plus de logique metier, uniquement la delegation aux services et la conversion en DTOs.

### 3. Couche service (package `service/`)

| Fichier | Responsabilite |
|---------|----------------|
| `AuthService.java` | Connexion (trouve ou cree l'utilisateur), inscription (verifie unicite du pseudo) |
| `PartieService.java` | Creation de partie, rejoindre (avec verifications : partie ouverte, pas pleine, pas de doublon), demarrer (cree 32 cartes, melange, distribue 8 par joueur), lister joueurs |
| `InvitationService.java` | Envoi (verifie expediteur != destinataire), acceptation (change statut + rejoint la partie via `PartieService`), refus |

### 4. DTOs (package `dto/`)

| DTO | Champs exposes | Champs masques |
|-----|---------------|----------------|
| `UtilisateurDTO` | id, pseudo, scoreGlobal | `mdp` (mot de passe) |
| `JoueurDTO` | id, equipe, position, pseudo, utilisateurId | entite `Utilisateur` complete, entite `Partie` |
| `PartieDTO` | id, statut, atout, scoreA, scoreB, nombreJoueurs | liste de `Joueur` (evite la serialisation circulaire) |
| `InvitationDTO` | id, statut, pseudoExpediteur, expediteurId, pseudoDestinataire, destinataireId, partieId | entites `Utilisateur` et `Partie` completes |

Chaque DTO dispose d'une methode `static fromEntity(...)` pour la conversion.

### 5. Gestion d'erreurs centralisee (package `exception/`)

| Fichier | Role |
|---------|------|
| `BusinessException.java` | Exception metier -> HTTP 400 |
| `ResourceNotFoundException.java` | Entite introuvable -> HTTP 404 |
| `GlobalExceptionHandler.java` | `@RestControllerAdvice` qui intercepte toutes les exceptions |

Exceptions gerees par le handler :

| Type d'exception | Code HTTP | Exemple |
|-----------------|-----------|---------|
| `ResourceNotFoundException` | 404 | Partie #999 introuvable |
| `BusinessException` | 400 | La partie est deja pleine |
| `ConstraintViolationException` | 400 | pseudo : must not be blank |
| `MethodArgumentNotValidException` | 400 | Erreur de validation sur @RequestBody |
| `MissingServletRequestParameterException` | 400 | Parametre manquant : pseudo |
| `Exception` (generique) | 500 | Erreur interne (avec log serveur) |

### 6. Configuration CORS (package `config/`)

| Avant | Apres |
|-------|-------|
| `@CrossOrigin(origins = "*")` sur le controleur | `CorsConfig.java` avec `WebMvcConfigurer` |
| Toutes les origines autorisees | Uniquement `http://localhost:5173` et `http://127.0.0.1:5173` |

### 7. Repository ameliore

| Fichier | Modification |
|---------|-------------|
| `UtilisateurRepository.java` | Ajout de `findByPseudo(String pseudo)` — remplace le `findAll().stream().filter(...)` inefficace de l'ancien controleur |

### 8. Frontend mis a jour

| Fichier | Modifications |
|---------|--------------|
| `App.jsx` | Endpoint de connexion : `/api/utilisateur/connexion` -> `/api/auth/connexion` |
| | Champs DTO joueur : `j.utilisateur?.pseudo` -> `j.pseudo` |
| | Champs DTO invitation : `inv.expediteur?.pseudo` -> `inv.pseudoExpediteur`, `inv.partie?.id` -> `inv.partieId` |
| | Ajout de `encodeURIComponent(pseudo)` pour le parametre d'URL |

---

## Bug corrige en cours de validation

**Probleme :** `POST /api/auth/connexion?pseudo=` retournait HTTP 500 au lieu de 400.

**Cause :** L'annotation `@NotBlank` sur un `@RequestParam` (validation de methode via `@Validated`) lance une `ConstraintViolationException`, pas une `MethodArgumentNotValidException`. Le `GlobalExceptionHandler` ne gerait pas ce type d'exception, donc elle tombait dans le handler generique (500).

**Correction :** Ajout d'un handler `@ExceptionHandler(ConstraintViolationException.class)` dans `GlobalExceptionHandler.java` qui retourne HTTP 400 avec le message de validation.

---

## Tests de validation

Tous les tests sont executes via `curl` contre le backend demarre avec `./mvnw spring-boot:run`.

### Auth

| # | Commande | Resultat attendu | Statut |
|---|---------|-------------------|--------|
| 1 | `POST /api/auth/connexion?pseudo=alice` | 200 — `{"id":1,"pseudo":"alice","scoreGlobal":0}` (pas de champ mdp) | OK |
| 2 | `POST /api/auth/connexion?pseudo=` | 400 — `{"erreur":"...must not be blank..."}` | OK (apres fix) |
| 3 | `POST /api/auth/connexion` (sans parametre) | 400 — `{"erreur":"Paramètre manquant : pseudo"}` | OK |
| 4 | `POST /api/auth/inscrire?pseudo=bob` | 200 — `{"id":2,"pseudo":"bob","scoreGlobal":0}` | OK |
| 5 | `POST /api/auth/inscrire?pseudo=bob` (doublon) | 400 — `{"erreur":"Le pseudo 'bob' est déjà pris."}` | OK |

### Utilisateurs

| # | Commande | Resultat attendu | Statut |
|---|---------|-------------------|--------|
| 6 | `GET /api/utilisateurs` | 200 — liste de UtilisateurDTO | OK |
| 7 | `GET /api/utilisateur/1` | 200 — UtilisateurDTO d'alice | OK |
| 8 | `GET /api/utilisateur/999` | 404 — `{"erreur":"Utilisateur #999 introuvable."}` | OK |

### Parties

| # | Commande | Resultat attendu | Statut |
|---|---------|-------------------|--------|
| 9 | `POST /api/partie/creer` | 200 — PartieDTO statut OUVERTE | OK |
| 10 | `GET /api/parties` | 200 — liste de PartieDTO | OK |
| 11 | `GET /api/partie/1` | 200 — PartieDTO | OK |
| 12 | `GET /api/partie/999` | 404 — `{"erreur":"Partie #999 introuvable."}` | OK |
| 13 | `POST /api/partie/1/rejoindre?utilisateurId=1` | 200 — JoueurDTO | OK |
| 14 | `POST /api/partie/1/rejoindre?utilisateurId=1` (doublon) | 400 — `{"erreur":"Cet utilisateur est déjà dans la partie."}` | OK |
| 15 | `GET /api/partie/1/joueurs` | 200 — liste de JoueurDTO | OK |

### Invitations

| # | Commande | Resultat attendu | Statut |
|---|---------|-------------------|--------|
| 16 | `POST /api/invitation/envoyer?expediteurId=1&destinataireId=2&partieId=1` | 200 — InvitationDTO | OK |
| 17 | `POST /api/invitation/envoyer?expediteurId=1&destinataireId=1&partieId=1` | 400 — `{"erreur":"Impossible de s'inviter soi-même."}` | OK |
| 18 | `GET /api/invitation/recues?utilisateurId=2` | 200 — liste d'InvitationDTO | OK |
| 19 | `POST /api/invitation/1/accepter` | 200 — JoueurDTO (bob rejoint la partie) | OK |
| 20 | `POST /api/invitation/1/accepter` (deja traitee) | 400 — `{"erreur":"Cette invitation a déjà été traitée."}` | OK |

---

## Structure des fichiers apres Phase 1

```
backend-cartes/src/main/java/fr/enseeiht/jeux/
    BackendCartesApplication.java
    config/
        CorsConfig.java              (NOUVEAU)
    controller/
        AuthController.java          (NOUVEAU — remplace GameController)
        InvitationController.java    (NOUVEAU — remplace GameController)
        PartieController.java        (NOUVEAU — remplace GameController)
        UtilisateurController.java   (NOUVEAU — remplace GameController)
    dto/
        InvitationDTO.java           (NOUVEAU)
        JoueurDTO.java               (NOUVEAU)
        PartieDTO.java               (NOUVEAU)
        UtilisateurDTO.java          (NOUVEAU)
    exception/
        BusinessException.java       (NOUVEAU)
        GlobalExceptionHandler.java  (NOUVEAU)
        ResourceNotFoundException.java (NOUVEAU)
    modele/
        (inchange)
    repository/
        UtilisateurRepository.java   (MODIFIE — ajout findByPseudo)
        (autres inchanges)
    service/
        AuthService.java             (NOUVEAU)
        InvitationService.java       (NOUVEAU)
        PartieService.java           (NOUVEAU)

frontend-cartes/src/
    App.jsx                          (MODIFIE — endpoints et champs DTO)
```
