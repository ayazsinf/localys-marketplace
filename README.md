# Localys Marketplace

### Multi-vendor B2B/B2C platform with Spring Boot 3 + Angular + JWT + Keycloak-ready

**Localys** is a full-stack marketplace starter:

- **Backend:** Java 21, Spring Boot 3, Spring Security, JWT, PostgreSQL
- **Frontend:** Angular, modern marketplace UI (products, filters, seller flows)
- **Auth:** Custom JWT authentication, ready to be migrated to **Keycloak**
- **Extras:** i18n (en / fr / tr), Docker & docker-compose setup

> 🎯 Goal: build a real-world **multi-vendor marketplace** where sellers manage their own catalog and orders, and
> customers can browse, filter and purchase products.

---

## 1. What is Localys?

Localys is designed as a **B2B/B2C hybrid marketplace**:

- Sellers have their own area (dashboard) to:
  - Manage products
  - See and manage orders
  - Interact with customers (later: messaging)
- Customers can:
  - Register & login
  - Browse products with filters & sorting
  - Add to cart, place orders (payment integration planned)
- Admin can:
  - Manage users & sellers
  - Moderate products & categories

This project is the **V2 evolution** of a classic e-commerce starter (single-store B2C), now turned into a *
*multi-vendor platform**.

---

## 2. Features (current & planned)

### 🔐 Authentication & Roles

- User registration & login with JWT
- Spring Security configuration for:
  - `ROLE_CUSTOMER`
  - `ROLE_SELLER`
  - `ROLE_ADMIN` (planned)
- Global exception handling with clean JSON error responses

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
- Profiles: `local`, `docker`

**Planned marketplace entities:**

- `User` (with roles)
- `SellerProfile`
- `Product`, `ProductCategory`
- `Order`, `OrderItem`
- (Later) `Message` / `Conversation` for buyer–seller chats

### 🎨 Frontend (Angular)

- Angular app with:
  - Navbar (brand, search, auth, cart)
  - Sidebar filters
  - Product list with:
    - Category filter
    - Rating filter
    - Price range filter
    - Sorting (price, rating, name)
  - Product cards with hover effects & “Quick view” modal
- Auth UX:
  - Login / Register popup dialogs
  - Dynamic navbar:
    - “Sign in / Create account” when logged out
    - “Welcome {{ username }} 👋” + logout icon when logged in
- i18n structure for `en`, `fr`, `tr`

**Planned seller UI:**

- Seller Dashboard (`/seller`)
  - My products
  - My orders
  - Profile / store settings

### 🌍 Internationalization (i18n)

- Trans
