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
          set -euo pipefail
          cd "$DEPLOY_DIR"
          git fetch --all
          git checkout develop
          git pull --ff-only
          docker compose -f "$COMPOSE_FILE" build
        '''
      }
    }
    stage('Deploy (master)') {
      when {
        branch 'master'
      }
      steps {
        sh '''
          set -euo pipefail
          cd "$DEPLOY_DIR"
          git fetch --all
          git checkout master
          git pull --ff-only
          docker compose -f "$COMPOSE_FILE" up -d --build
        '''
      }
    }
  }
}
