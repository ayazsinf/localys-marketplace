# Phase‑1: Authentication & Authorization (Keycloak + OAuth2/OIDC)

Bu fazın amacı, modern bir uygulamada kimlik doğrulama (AuthN) ve yetkilendirmeyi (AuthZ) **piyasadaki standart yöntemle**
uçtan uca kurmak ve Docker üzerinde çalıştırmaktır.

---

## 1) Authentication vs Authorization

### Authentication (Kimlik Doğrulama)
Kullanıcının “kim olduğunu” doğrular.  
Örnek: kullanıcı adı + şifre → sistem Access Token üretir.

### Authorization (Yetkilendirme)
Kullanıcının “ne yapabileceğini” belirler.  
Örnek:
- ROLE_ADMIN → /api/admin/**
- ROLE_USER → /api/**

Bu projede yetkilendirme, **token içindeki roller üzerinden** yapılır.

---

## 2) Neden Keycloak?

Keycloak açık kaynaklı bir Identity Provider (IdP) / IAM çözümüdür.
OAuth2 ve OpenID Connect (OIDC) üzerinden uygulamalara token verir.

### Avantajlar
- Ücretsiz ve open‑source
- Yönetim paneli ile kullanıcı/rol/izin yönetimi
- SSO, MFA, OTP, sosyal login altyapısı
- Docker/Kubernetes için ilk‑sınıf destek
- Spring Boot ve Angular ile doğal entegrasyon

---

## 3) Piyasadaki Alternatifler

| Ürün | Tip | Artıları | Eksileri |
|---|---|---|---|
| **Auth0** | SaaS | Çok hızlı kurulum, çok gelişmiş UI | Ücretli, vendor lock‑in |
| **AWS Cognito** | Cloud | AWS ekosistemiyle güçlü | Konfigürasyon karmaşık |
| **Okta** | SaaS | Enterprise standardı | Yüksek maliyet |
| **Azure AD B2C** | Cloud | Microsoft ekosistemi | Öğrenme eğrisi yüksek |
| **Custom JWT (in‑app)** | Self‑host | Basit projeler için yeter | SSO/MFA/Panel yok, ölçek zor |

Biz, mimari tecrübe kazanmak ve platform bağımsız kalmak için Keycloak seçiyoruz.

---

## 4) Bu fazda ne kuruyoruz?

### Backend (Spring Boot Resource Server)
- Keycloak’ın verdiği JWT’yi doğrular.
- `issuer-uri` üzerinden Keycloak public key’ini otomatik alır.
- Endpoint yetkilendirmesi:
  - `/api/public/**` → anonim
  - `/api/hello` → login şart
  - `/api/admin/**` → ROLE_ADMIN

### Frontend (Angular SPA)
- `keycloak-js` ile OIDC login/logout (Authorization Code + PKCE S256).
- Token’ı Keycloak yönetir; Angular, backend çağrılarında otomatik gönderir.
- `ngx-translate` ile EN/FR/TR dil desteği.

### Docker
- Postgres + Keycloak + Backend + Frontend tek compose.
- **Tek komutla ayağa kalkar.**

---

## 5) Çalıştırma

```bash
cp .env
docker compose up --build
```

- Frontend: http://localhost:4200
- Keycloak Admin: http://localhost:8081 (admin/admin)
- Realm: `phase1-realm` otomatik import
- Kullanıcılar:
  - testuser / test123 (ROLE_USER)
  - admin / admin123 (ROLE_ADMIN)

---

## 6) Öğrenim hedefleri (Architect perspektifi)

- OAuth2 / OIDC akışları (Authorization Code + PKCE)
- Access token & ID token farkı
- Claim/Role mapping ve RBAC
- Resource Server doğrulama mantığı
- SPA security best practices
- Docker ile composable altyapı kurma

---

## 7) Kaynaklar

- Keycloak Resmi Dokümantasyon: https://www.keycloak.org/documentation
- Spring Security Reference: https://docs.spring.io/spring-security/reference/
- OAuth2 in Action (kitap): https://www.manning.com/books/oauth-2-in-action
- Keycloak JS adapter: https://www.keycloak.org/docs/latest/securing_apps/#_javascript_adapter
- OWASP Authentication CheatSheet: https://cheatsheetseries.owasp.org/cheatsheets/Authentication_Cheat_Sheet.html
- Auth0 Token Basics (görsel anlatım): https://auth0.com/docs/secure/tokens
