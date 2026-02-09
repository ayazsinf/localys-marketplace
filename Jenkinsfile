pipeline {
  agent any
  options {
    timestamps()
  }
  environment {
    DEPLOY_DIR = '/opt/localys/localys-marketplace'
    COMPOSE_FILE = 'docker-compose.prod.yml'
  }
  stages {
    stage('Checkout') {
      steps {
        checkout scm
      }
    }
    stage('Build (develop)') {
      when {
        branch 'develop'
      }
      steps {
        sh '''
          bash -lc 'set -euo pipefail
          git config --global --add safe.directory "$DEPLOY_DIR"
          cd "$DEPLOY_DIR"
          git fetch --all
          git clean -fd -e .env.prod -e certbot -e nginx -e uploads
          git checkout -B develop origin/develop
          git pull --ff-only origin develop
          docker compose -f "$COMPOSE_FILE" build'
        '''
      }
    }
    stage('Deploy (master)') {
      when {
        branch 'master'
      }
      steps {
        sh '''
          bash -lc 'set -euo pipefail
          git config --global --add safe.directory "$DEPLOY_DIR"
          cd "$DEPLOY_DIR"
          git fetch --all
          git clean -fd -e .env.prod -e certbot -e nginx -e uploads
          git checkout -B master origin/master
          git pull --ff-only origin master
          docker compose -f "$COMPOSE_FILE" up -d --build'
        '''
      }
    }
  }
}
