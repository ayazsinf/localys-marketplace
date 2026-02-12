pipeline {
  agent any

  options {
    timestamps()
    disableConcurrentBuilds()
    buildDiscarder(logRotator(numToKeepStr: '30', daysToKeepStr: '14', artifactNumToKeepStr: '10'))
  }

  environment {
    COMPOSE_FILE = 'docker-compose.prod.yml'

    DEV_DEPLOY_DIR = '/opt/localys/localys-marketplace-dev'
    UAT_DEPLOY_DIR = '/opt/localys/localys-marketplace-uat'
    PROD_DEPLOY_DIR = '/opt/localys/localys-marketplace'

    DEV_ENV_FILE = '.env.dev'
    UAT_ENV_FILE = '.env.uat'
    PROD_ENV_FILE = '.env.prod'
  }

  stages {
    stage('Checkout') {
      steps {
        checkout scm
      }
    }

    stage('Deploy DEV (develop)') {
      when {
        branch 'develop'
      }
      steps {
        sh '''
          bash -lc 'set -euo pipefail
          DEPLOY_DIR="$DEV_DEPLOY_DIR"
          ENV_FILE="$DEV_ENV_FILE"
          HTTP_PORT_VALUE="18080"
          HTTPS_PORT_VALUE="18443"

          git config --global --add safe.directory "$DEPLOY_DIR"
          cd "$DEPLOY_DIR"

          git fetch --all
          git clean -fd -e "$DEV_ENV_FILE" -e "$UAT_ENV_FILE" -e "$PROD_ENV_FILE" -e certbot -e nginx -e uploads
          git checkout -B develop origin/develop
          git pull --ff-only origin develop

          APP_ENV_FILE="$ENV_FILE" HTTP_PORT="$HTTP_PORT_VALUE" HTTPS_PORT="$HTTPS_PORT_VALUE" \
            docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" up -d --build'
        '''
      }
    }

    stage('Deploy UAT (release/*)') {
      when {
        expression {
          return env.BRANCH_NAME?.startsWith('release/')
        }
      }
      steps {
        sh '''
          bash -lc 'set -euo pipefail
          DEPLOY_DIR="$UAT_DEPLOY_DIR"
          ENV_FILE="$UAT_ENV_FILE"
          TARGET_BRANCH="$BRANCH_NAME"
          HTTP_PORT_VALUE="28080"
          HTTPS_PORT_VALUE="28443"

          git config --global --add safe.directory "$DEPLOY_DIR"
          cd "$DEPLOY_DIR"

          git fetch --all
          git clean -fd -e "$DEV_ENV_FILE" -e "$UAT_ENV_FILE" -e "$PROD_ENV_FILE" -e certbot -e nginx -e uploads
          git checkout -B "$TARGET_BRANCH" "origin/$TARGET_BRANCH"
          git pull --ff-only origin "$TARGET_BRANCH"

          APP_ENV_FILE="$ENV_FILE" HTTP_PORT="$HTTP_PORT_VALUE" HTTPS_PORT="$HTTPS_PORT_VALUE" \
            docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" up -d --build'
        '''
      }
    }

    stage('Approval Gate (master)') {
      when {
        branch 'master'
      }
      steps {
        timeout(time: 30, unit: 'MINUTES') {
          input message: 'Deploy to PRODUCTION?', ok: 'Deploy'
        }
      }
    }

    stage('Deploy PROD (master)') {
      when {
        branch 'master'
      }
      steps {
        sh '''
          bash -lc 'set -euo pipefail
          DEPLOY_DIR="$PROD_DEPLOY_DIR"
          ENV_FILE="$PROD_ENV_FILE"
          HTTP_PORT_VALUE="80"
          HTTPS_PORT_VALUE="443"

          git config --global --add safe.directory "$DEPLOY_DIR"
          cd "$DEPLOY_DIR"

          git fetch --all
          git clean -fd -e "$DEV_ENV_FILE" -e "$UAT_ENV_FILE" -e "$PROD_ENV_FILE" -e certbot -e nginx -e uploads
          git checkout -B master origin/master
          git pull --ff-only origin master

          APP_ENV_FILE="$ENV_FILE" HTTP_PORT="$HTTP_PORT_VALUE" HTTPS_PORT="$HTTPS_PORT_VALUE" \
            docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" up -d --build'
        '''
      }
    }

    stage('Unsupported Branch') {
      when {
        not {
          anyOf {
            branch 'develop'
            branch 'master'
            expression {
              return env.BRANCH_NAME?.startsWith('release/')
            }
          }
        }
      }
      steps {
        echo "No deployment action for branch: ${env.BRANCH_NAME}"
      }
    }
  }

  post {
    success {
      echo "Pipeline completed successfully for ${env.BRANCH_NAME}."
    }
    failure {
      echo "Pipeline failed for ${env.BRANCH_NAME}."
    }
    always {
      echo 'Execution finished.'
    }
  }
}
