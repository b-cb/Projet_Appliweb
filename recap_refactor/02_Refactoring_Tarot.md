# Refactorisation du Jeu de Tarot

Le refactorisation du `TarotService` a été la première étape. Le fichier est passé d'un monolithe complexe à un service d'orchestration clair, en déléguant ses tâches à trois nouveaux sous-services.

## 1. TarotReglesService.java
**Rôle :** L'arbitre du jeu.
**Méthodes extraites :**
- `verifierReglesTarot(joueur, carte, pli, ...)`
- `verifierMonteeAtout(...)`
- `ordreCarte(...)`

Ce service s'assure que le joueur a le droit de poser la carte sélectionnée. Il vérifie de manière stricte si le joueur a fourni à la couleur, s'il a coupé s'il n'avait pas la couleur, s'il a monté à l'atout, et gère les exceptions pour l'Excuse. Il ne connaît absolument rien des WebSockets ou de la base de données.

## 2. TarotEnchereService.java
**Rôle :** Le commissaire-priseur.
**Méthodes extraites :**
- `enchirirTarot(...)`
- `appelerRoi(...)` (Spécifique au mode 5 joueurs)
- `ecarterCartes(...)`
- `lancerJeuTarot(...)`

Il gère les transitions de la phase `EN_ENCHERE` vers les phases `APPEL_ROI`, `CHIEN`, puis `EN_JEU`. Toute la logique vérifiant si un contrat est légalement supérieur au précédent, ou si un roi peut être appelé, est encapsulée ici.

## 3. TarotEtatService.java
**Rôle :** Le présentateur de l'état du jeu.
**Méthodes extraites :**
- `getEtatJeuTarot(partieId, utilisateurId)`
- `buildResultatTarot(...)`
- `collecterCartesPreneur(...)`
- `correctionExcuseX2(...)`

Ce service a la responsabilité de construire le `EtatTarotDTO`. C'est une tâche ardue car le DTO nécessite de filtrer les données (cacher les mains adverses, cacher le chien à la défense, assembler le pli courant de manière visuelle pour le Front-End). En extrayant cela, on sépare la logique de *Présentation* de la logique *Métier*. Il contient également la méthode de fin de jeu pour calculer les scores en fonction des bouts accumulés.

## Le nouveau TarotService (Orchestrateur)
Les signatures publiques de `TarotService` (comme `jouerCarte`) n'ont pas changé pour éviter de casser les contrôleurs REST et le bot.
Cependant, le code intérieur d'une méthode comme `jouerCarte` se résume maintenant à :
1. Récupération des entités en base.
2. `tarotReglesService.verifierReglesTarot(...)`
3. Mise à jour de l'entité `Pli` et sauvegarde.
4. Si le pli est fini : évaluation du gagnant, et appel au `scoringService`.
5. Sauvegarde de l'état global.
6. `messagingTemplate.convertAndSend(...)` pour informer le frontend.
