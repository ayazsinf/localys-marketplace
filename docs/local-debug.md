# Local Debug Flow

This flow is for day-to-day local development without changing the VPS/deploy setup.

## Goal

- Frontend runs locally from IntelliJ or terminal
- Backend runs locally from IntelliJ in debug mode
- Keycloak runs in Docker
- Database stays on Neon

## Quick Start

```powershell
.\local.cmd up
```

This command:

- loads the root `.env` file
- starts only Keycloak in Docker
- starts the backend locally with the `local` profile
- starts the frontend locally
- writes backend/frontend logs under `.local-dev`

Useful commands:

```powershell
.\local.cmd status
.\local.cmd logs backend
.\local.cmd logs frontend
.\local.cmd logs keycloak
.\local.cmd restart
.\local.cmd down
```

URLs:

- Frontend: `http://localhost:4200`
- Backend: `http://localhost:8080`
- Keycloak: `http://localhost:8081`
- Realm issuer: `http://localhost:8081/realms/localys-realm`

Admin console:

- `http://localhost:8081/admin`
- user: `admin`
- password: `admin`

The application and Keycloak database connections come from the root `.env` file. No local PostgreSQL container is started.

## IntelliJ Alternative

Use the Spring Boot main class from IntelliJ with profile `local`.

Recommended environment variables for the IntelliJ run configuration:

```text
SPRING_PROFILES_ACTIVE=local
SPRING_DATASOURCE_URL=jdbc:postgresql://ep-jolly-king-ahecyc6a-pooler.c-3.us-east-1.aws.neon.tech/neondb?sslmode=require
SPRING_DATASOURCE_USERNAME=neondb_owner
SPRING_DATASOURCE_PASSWORD=your_neon_password
OIDC_ISSUER_URI=http://localhost:8081/realms/localys-realm

APP_MAIL_ENABLED=true
MAIL_FROM=ayazsinf@gmail.com
SPRING_MAIL_HOST=smtp.gmail.com
SPRING_MAIL_PORT=587
SPRING_MAIL_USERNAME=your_mail_user
SPRING_MAIL_PASSWORD=your_mail_app_password
SPRING_MAIL_PROPERTIES_MAIL_SMTP_AUTH=true
SPRING_MAIL_PROPERTIES_MAIL_SMTP_STARTTLS_ENABLE=true
SPRING_MAIL_PROPERTIES_MAIL_SMTP_STARTTLS_REQUIRED=true
SPRING_MAIL_PROPERTIES_MAIL_SMTP_SSL_TRUST=smtp.gmail.com
SPRING_MAIL_PROPERTIES_MAIL_SMTP_SSL_PROTOCOLS=TLSv1.2
```

Backend URL:

- `http://localhost:8080`

Run the frontend from IntelliJ terminal or a frontend run configuration:

From IntelliJ terminal or a frontend run configuration:

```powershell
cd frontend
npm run start
```

Frontend URL:

- `http://localhost:4200`

The frontend already points to local Keycloak in `src/environments/environment.ts`.
The Angular proxy already points `/api` to `http://localhost:8080`.

## Notes

- This does not replace `docker-compose.yml` or `docker-compose.prod.yml`.
- VPS pull/deploy flow stays unchanged.
- `application-local.yml` still has local Postgres defaults, but IntelliJ env vars override them.
