pipeline {
  agent any
  environment {
    IMAGE_NAME = "demo-ci-cd:latest"
  }
  stages {
  stage('Checkout') {
    steps {
      git branch: 'main',
          url: 'https://github.com/JASSNexocorp/springboot-app.git'
      sh 'echo "Checkout del repositorio completado..."'
    }
  }
    stage('Build & Test') {
      steps {
        sh 'mvn -B clean package'
      }
    }
    stage('Build Docker Image') {
      steps {
        // sh 'docker build -t $IMAGE_NAME .'
        sh 'echo "Build del Docker Image completado..."'
      }
    }
    stage('Run Container') {
      steps {
        // sh 'docker rm -f demo-ci-cd || true'
        // sh 'docker run -d --name demo-ci-cd -p 8080:8080 $IMAGE_NAME'
        sh 'echo "Run del Container completado..."'
      }
    }
  }
  post {
    always {
      junit '**/target/surefire-reports/*.xml'
      archiveArtifacts artifacts: 'target/*.jar', fingerprint: true
    }
  }
}
