# Localys Deploy Runbook

This document records the current Git, Jenkins, and VPS deployment flow so we do not have to rediscover it later.

## Environments

VPS base path:

```bash
/opt/localys
```

Environment folders:

```text
/opt/localys/localys-marketplace-dev  -> DEV
/opt/localys/localys-marketplace-uat  -> UAT
/opt/localys/localys-marketplace      -> PROD
```

Branch mapping:

```text
develop       -> DEV
release/*     -> UAT
master        -> PROD
```

Compose/env mapping:

```text
DEV  -> .env.dev  -> HTTP 18080 / HTTPS 18443
UAT  -> .env.uat  -> HTTP 28080 / HTTPS 28443
PROD -> .env.prod -> HTTP 80 / HTTPS 443
```

Jenkins URL:

```text
https://jenkins.localys.shop/
```

## Normal Development Flow

Work locally, not directly inside the VPS deployment folders.

```bash
git checkout develop
git pull origin develop

# make code changes

git status
git add <changed-files>
git commit -m "Short clear message"
git push origin develop
```

Jenkins should deploy `develop` to DEV automatically. If webhook does not trigger, open Jenkins and run:

```text
Localys Marketplace -> Scan Repository Now
```

or manually build the `develop` branch.

## Release To UAT

Create a release branch from `develop`:

```bash
git checkout develop
git pull origin develop
git checkout -b release/YYYY.MM.DD
git push origin release/YYYY.MM.DD
```

If the release branch already exists and `develop` has a new fix:

```bash
git checkout release/YYYY.MM.DD
git merge develop
git push origin release/YYYY.MM.DD
```

Deploy UAT through Jenkins promote job:

```text
pipe-localys-promote
TARGET_ENV = uat
SOURCE_BRANCH = release/YYYY.MM.DD
```

Expected success message:

```text
Manual promotion succeeded: release/YYYY.MM.DD -> uat
```

## Promote To PROD

After UAT is validated, merge the release branch into `master` locally:

```bash
git checkout master
git pull origin master
git merge release/YYYY.MM.DD
git push origin master
```

Then deploy PROD through Jenkins promote job:

```text
pipe-localys-promote
TARGET_ENV = prod
SOURCE_BRANCH = master
```

Jenkins will ask for approval:

```text
Deploy master to PRODUCTION?
```

Approve only after UAT has been checked.

After PROD deploy, merge `master` back into `develop` if needed:

```bash
git checkout develop
git pull origin develop
git merge master
git push origin develop
```

## Jenkins Jobs

Main multibranch job:

```text
Localys Marketplace
```

Uses:

```text
Jenkinsfile
```

Behavior:

```text
develop    -> Deploy DEV
release/*  -> Deploy UAT
master     -> Approval Gate -> Deploy PROD
```

Manual promotion job:

```text
pipe-localys-promote
```

Uses:

```text
Jenkinsfile.promote
```

Parameters:

```text
TARGET_ENV = uat | prod
SOURCE_BRANCH = release/* for UAT, master for PROD
```

Important guardrail:

```text
PROD deployments only allow SOURCE_BRANCH = master
```

## GitHub Credential In Jenkins

If Jenkins scan fails with:

```text
Bad credentials
status: 401
```

Create a new GitHub personal access token and update the Jenkins credential:

```text
Credentials -> ayazsinf/****** -> Update
```

or select it from:

```text
Localys Marketplace -> Configure -> Branch Sources -> GitHub -> Credentials
```

Then run:

```text
Localys Marketplace -> Scan Repository Now
```

## UAT Checks

SSH to the VPS and check containers:

```bash
cd /opt/localys/localys-marketplace-uat
sudo docker compose --env-file .env.uat -f docker-compose.prod.yml ps
```

Expected important services:

```text
localys-marketplace-uat-backend-1
localys-marketplace-uat-frontend-1
localys-marketplace-uat-keycloak-1
localys-marketplace-uat-nginx-1
```

UAT HTTP URL:

```text
http://51.255.200.173:28080
```

Local server test:

```bash
curl -I http://localhost:28080
```

Firewall ports:

```bash
sudo ufw allow 28080/tcp
sudo ufw allow 28443/tcp
sudo ufw reload
sudo ufw status
```

## Known UAT Issues

### 1. `.env.uat` Permission

If this fails:

```bash
docker compose --env-file .env.uat -f docker-compose.prod.yml ps
```

with:

```text
permission denied
```

use `sudo` for inspection:

```bash
sudo docker compose --env-file .env.uat -f docker-compose.prod.yml ps
```

Do not make `.env.uat` world-readable.

### 2. Liquibase Checksum Error

If backend logs show:

```text
Validation Failed: changesets check sum
```

Do not edit old migration files that have already run in a deployed DB.

Correct pattern:

```text
Keep old V*.sql files unchanged.
Add a new migration file, for example V18__normalize_user_roles.sql.
Register it in db.changelog-master.yaml.
```

We fixed role normalization this way:

```text
V18__normalize_user_roles.sql
```

### 3. Keycloak Restart Loop

If Keycloak status keeps showing only a few seconds:

```text
localys-marketplace-uat-keycloak-1 Up 2 seconds
```

check logs:

```bash
docker logs --tail=100 localys-marketplace-uat-keycloak-1
```

If the log says:

```text
database "keycloak_db" does not exist
```

create the DB once:

```bash
sudo docker exec -it localys-marketplace-uat-db-1 sh -lc 'createdb -U "$POSTGRES_USER" keycloak_db'
sudo docker restart localys-marketplace-uat-keycloak-1
```

### 4. UAT Nginx Config Missing

If:

```bash
curl -I http://localhost:28080
```

returns:

```text
Recv failure: Connection reset by peer
```

check:

```bash
ls -la /opt/localys/localys-marketplace-uat/nginx/conf.d
```

If empty, create a simple UAT HTTP config:

```bash
sudo tee /opt/localys/localys-marketplace-uat/nginx/conf.d/uat.conf > /dev/null <<'EOF'
server {
  listen 80 default_server;
  server_name _;

  client_max_body_size 20m;

  location /api/ {
    proxy_pass http://backend:8080;
    proxy_set_header Host $host;
    proxy_set_header X-Real-IP $remote_addr;
  }

  location /uploads/ {
    alias /var/www/uploads/;
    access_log off;
    expires 30d;
  }

  location / {
    proxy_pass http://frontend:80/;
    proxy_set_header Host $host;
    proxy_set_header X-Real-IP $remote_addr;
  }
}
EOF
```

Restart UAT nginx:

```bash
cd /opt/localys/localys-marketplace-uat
sudo docker compose --env-file .env.uat -f docker-compose.prod.yml restart nginx
curl -I http://localhost:28080
```

## Useful Logs

```bash
docker logs --tail=100 localys-marketplace-uat-nginx-1
docker logs --tail=100 localys-marketplace-uat-backend-1
docker logs --tail=100 localys-marketplace-uat-keycloak-1
```

## Current Product Role Decision

Current roles:

```text
ROLE_USER
ROLE_ADMIN
```

Do not add these yet:

```text
ROLE_VENDOR
ROLE_CUSTOMER
ROLE_APPROVER
```

Normal users can publish listings and buy/contact. Admin approves or rejects listings.
