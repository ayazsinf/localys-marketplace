# CI/CD Release Flow (DEV -> UAT -> PROD)

## 1) Branch Strategy

- `feature/US-123-short-name`: work branch for each user story
- `develop`: integration branch, deploy target = DEV
- `release/YYYY.MM.X`: stabilization branch, deploy target = UAT
- `master`: production branch, deploy target = PROD
- `hotfix/*`: urgent production fix

## 2) Environment Mapping

- DEV: `develop` branch
- UAT: `release/*` branches
- PROD: `master` branch

Jenkinsfile expects these server paths and env files:

- DEV: `/opt/localys/localys-marketplace-dev` + `.env.dev`
- UAT: `/opt/localys/localys-marketplace-uat` + `.env.uat`
- PROD: `/opt/localys/localys-marketplace` + `.env.prod`

## 3) End-to-End Flow

1. Create branch from `develop`: `feature/US-xxx-*`
2. Push commits, CI checks pass
3. Open PR: `feature/* -> develop`
4. After review, merge to `develop`
5. Jenkins deploys `develop` to DEV
6. At release cut, create `release/YYYY.MM.X` from `develop`
7. Jenkins deploys `release/*` to UAT
8. QA/UAT sign-off on UAT environment
9. Merge `release/* -> master`
10. Jenkins `master` pipeline waits for manual approval
11. On approval, Jenkins deploys PROD
12. Merge `master -> develop` to avoid drift

## 4) Git Provider Protection Rules

Set branch protections:

- `develop`
  - Require pull request before merge
  - Require status checks to pass
  - Block direct push
- `master`
  - Require pull request before merge
  - Require status checks to pass
  - Block direct push + force push
  - Optional: require 2 approvals

## 5) Jenkins Job Recommendations

- Use Multibranch Pipeline job
- Enable GitHub webhook trigger
- Keep build retention enabled (already in Jenkinsfile)
- Keep `input` approval stage for `master`

## 5.1) Optional Professional Promotion Job

For controlled manual promotions, add a second Jenkins Pipeline job that uses `Jenkinsfile.promote`.

- Job type: `Pipeline`
- Definition: `Pipeline script from SCM`
- Script path: `Jenkinsfile.promote`

Runtime parameters:

- `TARGET_ENV`: `uat` or `prod`
- `SOURCE_BRANCH`: branch to deploy

Guardrails in `Jenkinsfile.promote`:

- UAT accepts only `release/*`
- PROD accepts only `master`
- PROD has manual approval gate before deploy

## 6) Release Checklist

Before creating `release/*`:

- All target US merged to `develop`
- DEV smoke tests green
- DB migration review complete

Before `release/* -> master`:

- UAT functional tests complete
- UAT sign-off from product owner
- Rollback note prepared

After PROD deploy:

- Smoke tests passed
- Error logs checked
- Monitoring dashboards healthy
