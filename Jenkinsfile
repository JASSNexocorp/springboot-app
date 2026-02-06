pipeline {
  agent any
  
  environment {
    IMAGE_NAME = "demo-ci-cd:latest"
    VM_HOST = "192.168.1.160"
    VM_USER = "crayolito"
    DEPLOY_PATH = "/opt/spring-boot-app"
  }
  
  stages {
    stage('Checkout') {
      steps {
        git branch: 'main',
            url: 'https://github.com/JASSNexocorp/springboot-app.git',
            credentialsId: 'github-token'
        sh 'echo "✅ Checkout completado"'
      }
    }
    
    stage('Build & Test') {
      steps {
        sh 'mvn -B clean package'
        sh 'mvn jacoco:report'  // ← JACOCO: Genera reporte de cobertura
        sh 'echo "✅ Build, tests y reporte de cobertura completados"'
      }
    }
    
    stage('Deploy to VM - Rolling Update') {
      steps {
        script {
          sh """
            echo "🚀 Copiando JAR a VM..."
            scp target/*.jar ${VM_USER}@${VM_HOST}:/tmp/app-new.jar
            
            echo "🔄 [1/2] Desplegando en puerto 8081..."
            ssh ${VM_USER}@${VM_HOST} "sudo ${DEPLOY_PATH}/deploy.sh /tmp/app-new.jar 8081"
            
            echo "✅ Puerto 8081 actualizado"
            
            echo "🔄 [2/2] Desplegando en puerto 8082..."
            ssh ${VM_USER}@${VM_HOST} "sudo ${DEPLOY_PATH}/deploy.sh /tmp/app-new.jar 8082"
            
            echo "✅ Rolling deployment completado"
          """
        }
      }
    }
    
    stage('Verify Deployment') {
      steps {
        script {
          sh """
            echo "🔍 Verificando ambas instancias..."
            ssh ${VM_USER}@${VM_HOST} "curl -f http://localhost:8081/health"
            ssh ${VM_USER}@${VM_HOST} "curl -f http://localhost:8082/health"
            echo "✅ Ambas instancias verificadas"
          """
        }
      }
    }
  }
  
  post {
    always {
      // Reportes de tests
      junit '**/target/surefire-reports/*.xml'
      
      // Archivar JAR
      archiveArtifacts artifacts: 'target/*.jar', fingerprint: true
      
      // JACOCO: Archivar reporte de cobertura
      archiveArtifacts artifacts: 'target/site/jacoco/**/*', fingerprint: true, allowEmptyArchive: true
      
      // JACOCO: Publicar reporte HTML
      publishHTML([
        allowMissing: false,
        alwaysLinkToLastBuild: true,
        keepAll: true,
        reportDir: 'target/site/jacoco',
        reportFiles: 'index.html',
        reportName: 'Jacoco Coverage Report'
      ])
    }
    success {
      echo '✅ Pipeline completado exitosamente'
    }
    failure {
      echo '❌ Pipeline falló - revisar logs'
    }
  }
}