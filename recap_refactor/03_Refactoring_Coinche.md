# Refactorisation du Jeu de Coinche

Faisant écho au refactoring du Tarot, le `CoincheService` a bénéficié d'une restructuration identique, garantissant une cohérence architecturale dans tout le backend `fr.enseeiht.jeux`.

## 1. CoincheReglesService.java
**Rôle :** Arbitrage des plis selon le contrat en cours.
**Méthodes extraites :**
- `verifierReglesCouleur(joueur, carte, pli, atout, joueurs)`
- `estPartenaireLeGagnantActuel(...)`

La Coinche possède des règles très spécifiques qui varient selon que l'on joue un contrat à la "Couleur", à "Sans-Atout", ou à "Tout-Atout". Ce service encapsule l'obligation de monter ou de couper (qui est annulée si le partenaire est maître).

## 2. CoincheEnchereService.java
**Rôle :** Gestionnaire de la phase des enchères et des contrats.
**Méthodes extraites :**
- `traiterEnchere(...)`
- `traiterCoinche(...)` (Gestion spécifique de Coinche et Surcoinche)
- `demarrerJeuDepuisEnchere(...)`
- `doitCommencerJeu(...)`

La logique pour déterminer quand les enchères s'arrêtent (ex: après 3 passes consécutives ou après une Surcoinche) est entièrement déléguée ici. Le service retourne de simples booléens (ex: `finEncheres = true`) à l'orchestrateur pour qu'il sache quand passer la partie au statut `EN_JEU`.

## 3. CoincheEtatService.java
**Rôle :** Générateur de la "Vue" du jeu.
**Méthodes extraites :**
- `getEtatJeu(...)`
- `buildResultat(...)`

Tout comme pour le Tarot, ce composant s'occupe de requêter la base de données pour assembler le `EtatCoincheDTO` final. Il vérifie à qui est le tour, rassemble les plis en cours, et calcule les points finaux en fin de partie (incluant la vérification du contrat rempli ou chuté).

## Défis résolus pendant la migration
Durant l'extraction de ces classes, certaines dépendances croisées ont émergé (par exemple, le besoin d'accéder au système de points). Le code a été restructuré pour injecter proprement les dépendances manquantes, et des erreurs de compilation liées à des problèmes de typage (comme l'utilisation de variables nulles pour l'entier `gagnantEquipe`) ont été scrupuleusement fixées sans altérer la logique du jeu original.
