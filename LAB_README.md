Laboratorio 4 - Spring Boot CI/CD with Jenkins and Docker

Instrucciones rápidas:
1. Requisitos: Jenkins with Docker available on agent, Docker daemon, Maven installed on agent.
2. Checkout the repository.
3. Run the Jenkins pipeline (Jenkinsfile) which:
   - builds the project (mvn clean package)
   - runs unit tests
   - builds a docker image
   - runs the container mapping port 8080
4. Validate by opening http://<jenkins-agent-host>:8080/ after container starts.

Entregables:
- Captura del pipeline con stages exitosos.
- URL de servicio (if accessible) or output of 'docker ps' showing container running.
- Jenkinsfile used.
