# Localys Marketplace

### Classified marketplace inspired by Leboncoin, built with Spring Boot 3 + Angular + JWT

**Localys** is evolving into a simple classified marketplace where users can publish listings, browse offers, and buy or contact other users. The goal is close to a Leboncoin-style experience, but with a smaller and cleaner first scope.

- **Backend:** Java 21, Spring Boot 3, Spring Security, JWT, PostgreSQL
- **Frontend:** Angular marketplace UI with listings, filters, auth, and user flows
- **Auth:** Custom JWT authentication, with a future path to Keycloak if needed
- **Extras:** i18n structure, Docker and docker-compose setup

> Current direction: one normal user role for listing and buying, plus one admin role for moderation.

---

## 1. Product Direction

Localys is no longer planned as a classic multi-vendor B2B/B2C marketplace with separate seller and customer roles. The new version is a **classified ads marketplace**:

- A user can:
  - Create an account and log in
  - Add listings/products
  - Manage their own listings
  - Browse and search published listings
  - Buy or contact the listing owner, depending on the flow we implement
- An admin can:
  - Review submitted listings
  - Approve or reject listings
  - Moderate users, categories, and content later

This keeps the first version simple: every normal account is just a user. A user can both publish and buy.

---

## 2. Roles

The application should currently use only these roles:

- `ROLE_USER`
- `ROLE_ADMIN`

### ROLE_USER

Default role for registered accounts.

A user can:

- Create listings
- Edit or delete their own listings
- View their own listing status
- Browse approved listings
- Buy or contact other users

### ROLE_ADMIN

Administrative role for moderation.

An admin can:

- View pending listings
- Approve listings
- Reject listings
- Manage marketplace data later, such as categories and reported content

### Future Role Expansion

For now, we should not introduce `ROLE_VENDOR`, `ROLE_CUSTOMER`, or `ROLE_APPROVER`.

If the application later needs more detailed permissions, we can add roles such as:

- `ROLE_APPROVER` for listing moderation only
- `ROLE_SUPPORT` for user support
- `ROLE_SUPER_ADMIN` for full platform administration

This should happen only when the need is clear.

---

## 3. Listing Approval Flow

Listings should not be public immediately after creation.

Recommended listing statuses:

- `DRAFT`: created but not submitted yet
- `PENDING_APPROVAL`: submitted by the user and waiting for admin review
- `APPROVED`: visible publicly in the marketplace
- `REJECTED`: rejected by admin and hidden from public listing pages
- `ARCHIVED`: no longer active

Expected flow:

1. User creates a listing.
2. Listing is saved as `PENDING_APPROVAL` when submitted.
3. Admin reviews the listing.
4. Admin approves or rejects it.
5. Only `APPROVED` listings are visible to public users.

---

## 4. Backend

- Java 21 + Spring Boot 3.3
- Spring Web, Spring Data JPA, Spring Security
- PostgreSQL via Docker
- JWT authentication
- Layered architecture:
  - `controller` / `api`
  - `service`
  - `repository`
  - `model` with entities and enums
  - `config` for security, JWT filter, and password encoding
- Profiles:
  - `local`
  - `docker`

Main domain concepts:

- `User`
- `Product` or `Listing`
- `ProductCategory`
- Listing status / approval status
- Later: order, payment, messaging, favorites, reports

---

## 5. Frontend

Angular app with:

- Navbar with brand, search, authentication, and user actions
- Listing grid/list
- Category filter
- Price range filter
- Sorting
- Listing detail page
- Login and register dialogs
- Dynamic navbar for logged-in users
- Admin moderation area planned for pending listings

Planned user pages:

- My listings
- Create listing
- Edit listing
- Listing status view

Planned admin pages:

- Pending listings
- Listing approval/rejection
- User and category management later

---

## 6. Internationalization

The project keeps an i18n structure for:

- English
- French
- Turkish

---

## 7. Current Decision

The current application direction is:

- One normal account type: `ROLE_USER`
- One moderation/admin account type: `ROLE_ADMIN`
- Users can both publish and buy
- Admin approval is required before a listing becomes public
- Extra roles should be postponed until the product needs them
