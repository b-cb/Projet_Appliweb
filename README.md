# Projet_Appliweb — Multiplayer Card Game Web Application

## Overview

`Projet_Appliweb` is an academic multiplayer card-game web prototype supporting Coinche and Tarot. It combines a React frontend with a Spring Boot backend and uses REST, JWT, WebSocket/STOMP, JPA, and an H2 database.

The project is intended for local development, coursework, and technical demonstration. It is not a production-ready service.

## Academic / Prototype Status

This repository contains an academic prototype, not a hardened production application.

It is intended for:

- local development;
- classroom or project demonstrations;
- experimentation with full-stack architecture, real-time communication, persistence, authentication, and game logic.

Security-sensitive settings must be provided through environment variables. Do not expose the application to an untrusted network without additional authentication, authorization, transport-security, deployment, and operational hardening.

## Features

Visible features include:

- user registration and login;
- a lobby listing available games;
- game creation, joining, deletion, and start workflows;
- Coinche gameplay;
- Tarot gameplay for three to five players;
- games with human players or rule-based bots;
- player invitations;
- in-game chat;
- bidding and card-play workflows;
- score calculation and multi-deal game state;
- real-time game-state and chat updates;
- persistent users, games, players, cards, bids, tricks, messages, and invitations.

## Technical Stack

### Frontend

- React 19
- Vite 8
- React Router 7
- STOMP.js 7
- JavaScript
- CSS
- Fetch API

### Backend

- Java 21
- Spring Boot 4
- Spring MVC
- Spring Security
- Spring Data JPA
- Spring WebSocket
- JJWT
- Maven and Maven Wrapper

### Database

- H2 for local development and tests
- Hibernate / JPA entities and repositories
- relational entities and many-to-many join tables

PostgreSQL appears in architecture documentation as a possible or planned production database, but it is not configured as a runtime dependency in the current Maven project.

## Architecture

The application is split into two main modules:

- the React frontend handles login, lobby, Coinche/Tarot pages, game controls, chat, and client-side state;
- the Spring Boot backend provides authentication, REST controllers, business services, game logic, JPA repositories, and WebSocket event publication.

Authenticated operations use a REST API with JWT bearer tokens. WebSocket/STOMP topics distribute shared events and per-player game-state updates. WebSocket authentication and subscription authorization still require deeper hardening before any non-local use.

Architecture and data-model diagrams are available in:

- [`schema_architecture.md`](schema_architecture.md)
- [`schema_bdd.md`](schema_bdd.md)
- [`schemas/`](schemas/)

Some diagrams may require version-alignment updates as the implementation evolves.

## Security Notes

- `JWT_SECRET` must be supplied through the environment for direct backend startup.
- `start.sh` generates an ephemeral local secret when `JWT_SECRET` is absent and OpenSSL is available.
- Never commit passwords, tokens, private keys, session identifiers, or environment files.
- The H2 console is disabled by default and is intended only for local development.
- CORS and WebSocket origins default to explicit localhost origins.
- Current CORS and WebSocket settings are development-oriented, not production-safe.
- STOMP connection authentication and authorization of private per-player topic subscriptions require additional work.
- TLS, deployment isolation, rate limiting, security monitoring, and production database hardening are outside the current prototype scope.
- Any secret that was previously exposed publicly must be considered compromised and rotated, even after history cleanup.

## Environment Variables

| Variable | Required | Purpose |
|---|---|---|
| `JWT_SECRET` | Required for direct backend startup | HMAC key used to sign and validate JWTs |
| `JWT_TEST_SECRET` | Optional | Overrides the test-only JWT key |
| `H2_CONSOLE_ENABLED` | Optional, default `false` | Enables the local H2 console when set to `true` |
| `SERVER_ADDRESS` | Optional, default `127.0.0.1` | Backend bind address |
| `APP_HOST` | Optional, default `127.0.0.1` | Vite bind address used by `start.sh` |
| `APP_CORS_ALLOWED_ORIGINS` | Optional | Comma-separated allowed frontend origins for REST/CORS |
| `APP_WEBSOCKET_ALLOWED_ORIGINS` | Optional | Comma-separated allowed frontend origins for WebSocket handshakes |

Example for local development only:

```bash
export JWT_SECRET="replace-with-a-long-random-local-development-secret"
```

A random value can be generated locally with:

```bash
openssl rand -hex 32
```

Do not copy a real secret into documentation, source files, issue trackers, or commits.

## Local Setup

### Recommended: start both modules

From the repository root:

```bash
./start.sh
```

The script:

- generates an ephemeral local JWT secret if necessary;
- starts the Spring Boot backend;
- installs frontend dependencies if `node_modules/` is absent;
- starts Vite on the local interface;
- records only the processes it started.

Default local URLs:

- frontend: `http://localhost:5173`
- backend: `http://localhost:8080`

Stop both processes cleanly with:

```bash
./stop.sh
```

### Backend only

```bash
cd backend-cartes
export JWT_SECRET="$(openssl rand -hex 32)"
./mvnw spring-boot:run
```

### Frontend only

```bash
cd frontend-cartes
npm ci --include=dev
npm run dev -- --host 127.0.0.1
```

### Optional local H2 console

The H2 console is disabled by default. For a temporary local session:

```bash
H2_CONSOLE_ENABLED=true JWT_SECRET="$(openssl rand -hex 32)" ./start.sh
```

Do not enable it on an untrusted network.

## Running Tests

### Backend

```bash
cd backend-cartes
./mvnw test
```

The backend includes authentication, API integration, game-service integration, Tarot, and Spring-context tests.

### Frontend

No dedicated frontend test suite is currently configured. The available validation commands are:

```bash
cd frontend-cartes
npm ci --include=dev
npm run build
npm run lint
```

The build and lint commands validate different concerns; a successful build does not imply a clean lint result.

## Project Structure

```text
Projet_Appliweb/
├── backend-cartes/       # Spring Boot backend, JPA entities, services, controllers, tests
├── frontend-cartes/      # React/Vite frontend, pages, components, hooks, card assets
├── schemas/              # Additional architecture/data-model documentation
├── schema_architecture.md
├── schema_bdd.md
├── start.sh              # Local startup helper
└── stop.sh               # PID-based graceful shutdown helper
```

## Contributors

Visible Git history contains contributions from:

- Batiste Comet-Barthe
- Abel Faress
- Maxence Hourde

Commit counts, line counts, and blame attribution are only indicators of visible repository activity. They do not measure the full value of a contribution and may omit pair programming, design discussions, debugging, testing, or work completed outside Git.

## Known Limitations

- Academic prototype; not production-ready.
- Local H2 database setup.
- WebSocket authentication and per-player subscription authorization need additional hardening.
- No deployment-scale, load, or adversarial-security validation.
- No frontend automated test suite is currently configured.
- CORS and WebSocket configuration is intended for local development.
- JWTs are stored in browser local storage, which requires careful XSS risk management.
- Architecture documentation may not always match current dependency versions.
- PostgreSQL support is documented as a possible direction but is not currently configured.
- Dependency and lint warnings require periodic review.