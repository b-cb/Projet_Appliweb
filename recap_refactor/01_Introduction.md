# Dossier de Refactorisation : Architecture Clean Code

## 1. Contexte et Objectifs

Au début de ce processus, le backend de l'application de Jeux de Cartes s'articulait autour de services monolithiques très lourds :
- `TarotService.java` : ~1300 lignes.
- `CoincheService.java` : ~1050 lignes.

Ces deux classes étaient devenues ce que l'on appelle en ingénierie logicielle des **"God Objects" (Classes Divines)**. Elles géraient simultanément :
1. La validation des règles métier strictes (obligation de monter, de couper, etc.).
2. Le cycle des enchères et des contrats.
3. Le stockage et la récupération en base de données (JPA/Hibernate).
4. La construction des DTO (Data Transfer Objects) pour le Frontend.
5. La diffusion en temps réel via WebSockets.

### Problèmes rencontrés :
- **Lisibilité** : La navigation dans le code devenait difficile. Modifier une règle nécessitait de parcourir des centaines de lignes de code gérant le réseau ou la base de données.
- **Maintenabilité** : Ajouter une nouvelle fonctionnalité risquait d'introduire des bugs dans des parties du système qui n'avaient rien à voir (ex: casser les websockets en modifiant le système d'enchères).
- **Testabilité** : Tester les règles du jeu nécessitait de moquer (mock) tout le système de WebSockets et la base de données.

## 2. Solution Adoptée : Séparation des Responsabilités (SRP)

L'objectif principal de cette refactorisation a été d'appliquer le principe de **Responsabilité Unique (Single Responsibility Principle - SRP)** du "Clean Code".

La logique a été découpée de manière chirurgicale en sous-composants spécialisés et injectés sous forme de dépendances (`@Service` Spring).

Les services principaux (`TarotService` et `CoincheService`) ont été vidés de leur logique de calcul pour devenir de purs **Orchestrateurs**. Leur rôle se limite désormais à coordonner les autres services de manière lisible (ex: "*vérifie les règles*, puis *sauvegarde en base*, puis *envoie via WebSockets*").
