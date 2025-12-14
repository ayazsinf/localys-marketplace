# Phase-1 Auth Starter
### Spring Boot 3 + Angular 16/21 style + JWT + Keycloak ready + i18n

This repository is a **full-stack starter** for modern web applications:

- **Backend:** Java 21, Spring Boot 3, Spring Security, JWT, PostgreSQL
- **Frontend:** Angular, responsive e-commerce style UI, filters & auth integration
- **Auth:** Custom JWT authentication, ready for **Keycloak** integration
- **Extras:** i18n (en / fr / tr), Docker & docker-compose setup

> 🧩 Goal: have a realistic, production-style starter that can evolve into a full e-commerce / platform project.

---

## 1. Features

### 🔐 Authentication & Authorization

- Register new users (username, email, password, role)
- Login and receive **JWT** from the backend
- JWT stored on the client & sent via HTTP interceptor
- Secured endpoints on backend (e.g. `/api/hello`)
- Custom exception handler for clean error messages

### 🧱 Backend

- Java 21 + Spring Boot 3.3
- Spring Web, Spring Data JPA, Spring Security
- PostgreSQL via Docker
- Layered architecture:
  - `controller` / `api`
  - `service`
  - `repository`
  - `model` (entities + enums)
  - `config` (security, JWT filter, password encoding)
- Global `@ControllerAdvice` for error handling
- Profiles: `local`, `docker`

### 🎨 Frontend (Angular)

- Angular app with navbar + sidebar + product grid
- **Product listing** with:
  - Category filter
  - Rating filter
  - Price range filter
  - Sort options (price low→high, high→low, rating, name A–Z)
  - Product card hover effects & “Quick view” modal
- **Auth UX:**
  - Login / Register modals (popup)
  - Dynamic navbar:
    - “Sign in / Create account” when logged out
    - “Welcome {{ username }}” + logout icon when logged in
- i18n structure for `en`, `fr`, `tr`

### 🌍 Internationalization (i18n)

- Translation files under `frontend/src/assets/i18n/`
  - `en.json`
  - `fr.json`
  - `tr.json`
- Ready to plug into the UI components (navbar, texts, messages)

### 🧱 Keycloak Ready

- `keycloak/phase1-realm.json` with base realm configuration
- `docker-compose.yml` contains a Keycloak service
- Currently using **custom JWT** auth, but the project is structured to migrate to Keycloak in a second phase.

---

## 2. Tech Stack

**Backend**

- Java 21
- Spring Boot 3.3.x
- Spring Web / Spring Security / Spring Data JPA
- PostgreSQL
- JWT (jjwt)

**Frontend**

- Angular
- SCSS
- Font Awesome icons
- Angular Material (dialog & theming infrastructure)

**Infrastructure**

- Docker
- Docker Compose
- Keycloak
- Git / GitHub

---

## 3. Project Structure

```text
firstTest/
├─ backend/              # Spring Boot backend (REST API + security)
│  ├─ src/main/java/com/example/app
│  │  ├─ api/           # Example controllers (e.g. HelloController)
│  │  ├─ config/        # SecurityConfig, JwtFilter, PasswordConfig
│  │  ├─ controller/    # AuthController
│  │  ├─ dto/           # RegisterUserRequest, LoginRequest
│  │  ├─ exceptions/    # GlobalExceptionHandler, custom exceptions
│  │  ├─ model/         # UserEntity, CustomUserDetails, enums
│  │  ├─ repository/    # UserRepository
│  │  ├─ service/       # AuthService, CustomUserDetailsService
│  │  └─ util/          # JwtUtil
│  └─ src/main/resources
│     ├─ application.yml
│     ├─ application-local.yml
│     └─ application-docker.yml
│
├─ frontend/             # Angular application
│  ├─ src/app
│  │  ├─ components/    # Navbar, Sidebar, Product Card, Quick View
│  │  ├─ pages/         # Home, Products, Categories, About, Contact
│  │  ├─ service/       # AuthService, ProductService, CartService, SearchService
│  │  ├─ guard/         # AuthGuard
│  │  ├─ interceptor/   # AuthInterceptor
│  │  └─ modules/       # TS models (Product, RegisterRequest, etc.)
│  ├─ src/assets/i18n/  # Translation files (en, fr, tr)
│  └─ Dockerfile
│
├─ keycloak/
│  └─ phase1-realm.json # Realm configuration
│
├─ docs/
│  └─ 01-auth-basics.md # Notes & documentation
│
├─ docker-compose.yml
├─ .gitignore
└─ README.md
