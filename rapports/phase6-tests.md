# Phase 6 — Tests formels (JUnit 5 + MockMvc)

## Objectif

Couvrir la logique métier et les endpoints REST par des tests automatisés reproductibles. Les tests valident les règles d'authentification, les règles de jeu Belote coinchée et le comportement HTTP de l'API, avec isolation complète via une base H2 en mémoire.

---

## Résultat global

```
Tests run: 39, Failures: 0, Errors: 0, Skipped: 0 — BUILD SUCCESS
```

| Classe de test | Catégorie | Tests | Durée |
|---|---|---|---|
| `AuthServiceTest` | Unitaire (Mockito) | 7 | ~0.2 s |
| `JeuServiceIntegrationTest` | Intégration (@SpringBootTest) | 14 | ~5.6 s |
| `ApiIntegrationTest` | Intégration MockMvc | 17 | ~7.8 s |
| `BackendCartesApplicationTests` | Smoke test | 1 | ~2.9 s |
| **Total** | | **39** | **~16 s** |

---

## Infrastructure de test

### Base de données

Tous les tests utilisent H2 en mémoire (isolée de la BDD de développement) via `src/test/resources/application.properties` :

```properties
spring.datasource.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1
spring.jpa.hibernate.ddl-auto=create-drop
```

Le schéma est recréé et détruit à chaque contexte de test. L'annotation `@DirtiesContext(classMode = AFTER_EACH_TEST_METHOD)` garantit l'isolation entre tests d'intégration.

### Dépendances de test

Fournies par `spring-boot-starter-data-jpa-test` et `spring-boot-starter-webmvc-test` (pom.xml existant) :
- JUnit 5 (Jupiter)
- Mockito + MockitoExtension
- Spring MockMvc + `MockMvcBuilders`
- AssertJ (assertions fluentes)

### Application de Spring Security dans MockMvc

L'injection du `FilterChainProxy` (bean Spring Security) dans `MockMvcBuilders` permet de tester le filtrage JWT sans dépendance supplémentaire :

```java
@Autowired private FilterChainProxy securityFilterChain;

mockMvc = MockMvcBuilders.webAppContextSetup(context)
        .addFilters(securityFilterChain)
        .build();
```

---

## Classe 1 — `AuthServiceTest` (7 tests unitaires)

**Approche :** tests purs avec `@ExtendWith(MockitoExtension.class)`. `UtilisateurRepository` et `PasswordEncoder` sont mockés — aucune base de données n'est utilisée.

| Test | Ce qui est vérifié |
|------|--------------------|
| `inscrire_pseudoDisponible_retourneUtilisateur` | L'utilisateur est sauvegardé avec les bons champs |
| `inscrire_pseudoDuplique_lanceException` | `BusinessException` levée si le pseudo existe déjà ; `save()` jamais appelé |
| `inscrire_motDePasseHashe` | Le mdp stocké est le hash BCrypt, jamais le texte clair |
| `connexion_identifiantsValides_retourneUtilisateur` | Retourne l'utilisateur si pseudo + mdp corrects |
| `connexion_pseudoInconnu_lanceException` | `BusinessException` si le pseudo est absent |
| `connexion_mauvaisMdp_lanceException` | `BusinessException` si le mot de passe ne correspond pas |
| `connexion_messageErreurGenerique` | Le message d'erreur est identique pour pseudo inconnu et mauvais mdp (pas d'énumération des comptes) |

---

## Classe 2 — `JeuServiceIntegrationTest` (14 tests d'intégration)

**Approche :** contexte Spring complet (`@SpringBootTest`) avec H2 en mémoire. Chaque test part d'une partie fraîche (4 joueurs créés en BDD, partie démarrée avec 8 cartes distribuées).

### État du jeu

| Test | Ce qui est vérifié |
|------|--------------------|
| `getEtatJeu_chaquejoueurA8Cartes` | Chaque joueur reçoit exactement 8 cartes après distribution |
| `getEtatJeu_statutInitialEnEnchere` | Le statut après `demarrerPartie` est `EN_ENCHERE` |
| `getEtatJeu_mainsDistinctes` | Les mains de deux joueurs différents n'ont aucune carte en commun |
| `getEtatJeu_utilisateurHorsPartie_lanceException` | Un utilisateur non inscrit dans la partie ne peut pas consulter l'état |

### Enchères

| Test | Ce qui est vérifié |
|------|--------------------|
| `encherir_unContratPuis3Passes_passageEnJeu` | Après 1 enchère réelle + 3 passes, le statut passe à `EN_JEU` et l'atout est fixé |
| `encherir_horsDeTour_lanceException` | Enchérir hors de son tour est refusé |
| `encherir_contratTropBas_lanceException` | Un contrat < 80 est invalide |
| `encherir_surenchereTropBasse_lanceException` | Une surenchère doit dépasser le contrat courant |
| `encherir_couleurInvalide_lanceException` | Une couleur non reconnue est rejetée |

### Règles de jeu (Belote coinchée)

| Test | Ce qui est vérifié |
|------|--------------------|
| `jouerCarte_suivi_couleurObligatoire` | Si un joueur a la couleur demandée, il est obligé de la jouer |
| `jouerCarte_horsDeTour_lanceException` | Jouer hors de son tour est refusé |
| `jouerCarte_carteHorsMain_lanceException` | Une carte absente de la main est rejetée |
| `jouerCarte_partieEnEnchere_lanceException` | Jouer une carte en phase d'enchères est refusé |
| `premierPliComplet_pliCourantVide` | Après que les 4 joueurs ont joué, le `numPliCourant` passe à 2 |

---

## Classe 3 — `ApiIntegrationTest` (17 tests MockMvc)

**Approche :** contexte Spring complet + MockMvc avec `FilterChainProxy`. Les tests d'authentification obtiennent de vrais tokens JWT via l'endpoint `/api/auth/inscrire`, qu'ils réutilisent dans les en-têtes `Authorization: Bearer`.

### Authentification — Inscription

| Test | Endpoint | Statut attendu |
|------|----------|----------------|
| `inscrire_identifiantsValides_retourne201` | `POST /api/auth/inscrire` | 201 + token JWT + pseudo |
| `inscrire_pseudoDuplique_retourne400` | `POST /api/auth/inscrire` | 400 |
| `inscrire_corpsVide_retourne400` | `POST /api/auth/inscrire` | 400 |

### Authentification — Connexion

| Test | Endpoint | Statut attendu |
|------|----------|----------------|
| `connexion_identifiantsValides_retourne200` | `POST /api/auth/connexion` | 200 + token JWT |
| `connexion_mauvaisMdp_retourne400` | `POST /api/auth/connexion` | 400 |
| `connexion_pseudoInexistant_retourne400` | `POST /api/auth/connexion` | 400 |

### Sécurité — Endpoints protégés

| Test | Endpoint | Statut attendu |
|------|----------|----------------|
| `getParties_sansToken_retourne401` | `GET /api/parties` | 401 |
| `creerPartie_sansToken_retourne401` | `POST /api/partie/creer` | 401 |
| `getEtat_sansToken_retourne401` | `GET /api/partie/{id}/etat` | 401 |

### Gestion des parties

| Test | Endpoint | Statut attendu |
|------|----------|----------------|
| `getParties_avecToken_retourne200` | `GET /api/parties` | 200 + liste JSON |
| `creerPartie_avecToken_retourne200` | `POST /api/partie/creer` | 200 + statut OUVERTE |
| `rejoindrePartie_partieExistante_retourne200` | `POST /api/partie/{id}/rejoindre` | 200 + pseudo du joueur |
| `demarrerPartie_moinsDe4Joueurs_retourne400` | `POST /api/partie/{id}/demarrer` | 400 |
| `getPartie_inexistante_retourne404` | `GET /api/partie/99999` | 404 |
| `supprimerPartie_partieOuverte_retourne204` | `DELETE /api/partie/{id}` | 204 |

### Endpoints de jeu

| Test | Endpoint | Statut attendu |
|------|----------|----------------|
| `encherir_partieNonExistante_retourne404` | `POST /api/partie/99999/encherir` | 404 |
| `jouerCarte_partieInexistante_retourne404` | `POST /api/partie/99999/jouer` | 404 |

---

## Fichiers créés

| Fichier | Rôle |
|---------|------|
| `src/test/java/fr/enseeiht/jeux/AuthServiceTest.java` | Tests unitaires du service d'authentification |
| `src/test/java/fr/enseeiht/jeux/JeuServiceIntegrationTest.java` | Tests d'intégration des règles Belote |
| `src/test/java/fr/enseeiht/jeux/ApiIntegrationTest.java` | Tests MockMvc des endpoints REST |
| `src/test/resources/application.properties` | Configuration H2 en mémoire pour les tests |

---

## Lancement des tests

```bash
cd backend-cartes
./mvnw test
```

Résultat attendu :
```
[INFO] Tests run: 39, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```
