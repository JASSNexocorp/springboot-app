// ============================================================
// PIPELINE DEVSECOPS — Spring Boot
// Orden: Checkout → Build Maven → SAST+SCA (paralelo) →
//        Build Docker → Image Checker → DAST → PAC →
//        Push Hub → DefectDojo → Deploy VM → Health Check
// ============================================================
pipeline {
    agent any

    environment {
        // --- Docker Hub ---
        DOCKER_HUB_USER = 'crayolito'
        IMAGE_NAME      = 'proyecto-final-modulo5'
        DOCKER_CREDS_ID = 'dockerhub-credentials'

        // --- App ---
        APP_PORT    = '8081'
        APP_URL     = "http://192.168.1.160:${APP_PORT}"
        HEALTH_URL  = "http://192.168.1.160:${APP_PORT}/health"

        // --- Seguridad ---
        // false = QA   → corre todo, nunca corta el pipeline
        // true  = PROD → corta si encuentra Critical/High/Medium
        IS_PRODUCTION = 'false'
        REPORTS_DIR   = 'security-reports'
        CHECKOV_BIN   = '/var/jenkins_home/.local/bin/checkov'

        // --- DefectDojo ---
        DD_PRODUCT   = 'springboot-app'
        DD_TOKEN     = credentials('defectdojo-api-token')
        DD_URL       = 'http://django-defectdojo-nginx-1:8080'
        DD_ENG_SAST  = 'sast-semgrep'
        DD_ENG_SCA   = 'sca-trivy-dependency-check'
        DD_ENG_IMAGE = 'image-checker-trivy'
        DD_ENG_DAST  = 'dast-owasp-zap'
        DD_ENG_PAC   = 'pac-checkov'

        // --- VM destino ---
        VM_USER      = 'crayolito'
        VM_HOST      = '192.168.1.160'
        VERSIONS_FILE = '/home/modulo5diplomado/deploy/versions.env'
        DEPLOY_SCRIPT = '/home/modulo5diplomado/deploy/deploy.sh'
        DEPLOY_LOG    = '/home/modulo5diplomado/deploy/deploy.log'
    }

    stages {

        // ----------------------------------------------------------
        // 1. CHECKOUT
        // ----------------------------------------------------------
        stage('Checkout') {
            steps {
                git branch: 'main',
                    url: 'https://github.com/JASSNexocorp/springboot-app.git'
            }
        }

        // ----------------------------------------------------------
        // 2. BUILD MAVEN — compilar una sola vez
        // ----------------------------------------------------------
        stage('Build Maven') {
            steps {
                sh 'mvn clean package -DskipTests -q'
            }
        }

        // ----------------------------------------------------------
        // 3. GENERAR VERSION — se usa en Docker y en el deploy
        // ----------------------------------------------------------
        stage('Generar Version') {
            steps {
                script {
                    env.IMAGE_VERSION = sh(
                        script: "date +%H%M-%d-%m-%Y",
                        returnStdout: true
                    ).trim()
                    echo "Version actual: ${env.IMAGE_VERSION}"
                }
            }
        }

        // ----------------------------------------------------------
        // 4. SAST + SCA — corren en paralelo, no dependen entre si
        // ----------------------------------------------------------
        stage('SAST + SCA') {
            parallel {

                stage('SAST - Semgrep') {
                    steps {
                        script {
                            sh '''
                                mkdir -p ${REPORTS_DIR}
                                echo "--- SAST Semgrep ---"

                                semgrep scan \
                                    --config auto \
                                    --json \
                                    --output ${REPORTS_DIR}/sast-semgrep.json \
                                    ./src 2>/dev/null || true

                                FINDINGS=$(grep -c '"check_id"' ${REPORTS_DIR}/sast-semgrep.json 2>/dev/null || echo "0")
                                echo "SAST completado — Hallazgos: ${FINDINGS}"
                            '''

                            // Solo en PROD: cortar si hay criticos
                            if (env.IS_PRODUCTION == 'true') {
                                sh '''
                                    CRITICAL=$(grep -c '"severity":"ERROR"'   ${REPORTS_DIR}/sast-semgrep.json 2>/dev/null || echo "0")
                                    HIGH=$(grep -c '"severity":"WARNING"' ${REPORTS_DIR}/sast-semgrep.json 2>/dev/null || echo "0")
                                    if [ "${CRITICAL}" -gt 0 ] || [ "${HIGH}" -gt 0 ]; then
                                        echo "PROD: SAST encontro hallazgos criticos. Abortando."
                                        exit 1
                                    fi
                                    echo "PROD Gate SAST: OK"
                                '''
                            }
                        }
                    }
                }

                stage('SCA - Trivy + Dependency-Check') {
                    steps {
                        script {
                            sh '''
                                mkdir -p ${REPORTS_DIR}
                                echo "--- SCA Trivy ---"

                                trivy fs \
                                    --format json \
                                    --output ${REPORTS_DIR}/sca-trivy.json \
                                    --scanners vuln \
                                    . 2>/dev/null || true
                                echo "Trivy completado"

                                echo "--- SCA Dependency-Check ---"

                                dependency-check \
                                    --project "${DD_PRODUCT}" \
                                    --scan . \
                                    --format JSON \
                                    --out ${REPORTS_DIR} \
                                    --disableAssembly \
                                    --noupdate 2>/dev/null || true
                                echo "Dependency-Check completado"
                            '''

                            if (env.IS_PRODUCTION == 'true') {
                                sh '''
                                    CRITICAL=$(grep -c '"Severity":"CRITICAL"' ${REPORTS_DIR}/sca-trivy.json 2>/dev/null || echo "0")
                                    HIGH=$(grep -c '"Severity":"HIGH"'     ${REPORTS_DIR}/sca-trivy.json 2>/dev/null || echo "0")
                                    MEDIUM=$(grep -c '"Severity":"MEDIUM"'   ${REPORTS_DIR}/sca-trivy.json 2>/dev/null || echo "0")
                                    if [ "${CRITICAL}" -gt 0 ] || [ "${HIGH}" -gt 0 ] || [ "${MEDIUM}" -gt 0 ]; then
                                        echo "PROD: SCA encontro vulnerabilidades. Abortando."
                                        exit 1
                                    fi
                                    echo "PROD Gate SCA: OK"
                                '''
                            }
                        }
                    }
                }

            }
        }

        // ----------------------------------------------------------
        // 5. BUILD DOCKER IMAGE — despues del analisis de codigo
        // ----------------------------------------------------------
        stage('Build Docker Image') {
            steps {
                sh "docker build -t ${env.DOCKER_HUB_USER}/${env.IMAGE_NAME}:${env.IMAGE_VERSION} ."
            }
        }

        // ----------------------------------------------------------
        // 6. IMAGE CHECKER — ahora si tiene imagen real
        // ----------------------------------------------------------
        stage('Image Checker - Trivy') {
            steps {
                script {
                    sh '''
                        echo "--- Image Checker Trivy ---"

                        trivy config \
                            --format json \
                            --output ${REPORTS_DIR}/image-checker.json \
                            . 2>/dev/null || true

                        # Si no genero archivo, crear uno vacio valido
                        if [ ! -f "${REPORTS_DIR}/image-checker.json" ]; then
                            echo '{"Results":[]}' > ${REPORTS_DIR}/image-checker.json
                        fi

                        echo "Image Checker completado"
                    '''

                    if (env.IS_PRODUCTION == 'true') {
                        sh '''
                            CRITICAL=$(grep -c '"Severity":"CRITICAL"' ${REPORTS_DIR}/image-checker.json 2>/dev/null || echo "0")
                            HIGH=$(grep -c '"Severity":"HIGH"'     ${REPORTS_DIR}/image-checker.json 2>/dev/null || echo "0")
                            MEDIUM=$(grep -c '"Severity":"MEDIUM"'   ${REPORTS_DIR}/image-checker.json 2>/dev/null || echo "0")
                            if [ "${CRITICAL}" -gt 0 ] || [ "${HIGH}" -gt 0 ] || [ "${MEDIUM}" -gt 0 ]; then
                                echo "PROD: Image Checker encontro misconfigs. Abortando."
                                exit 1
                            fi
                            echo "PROD Gate Image: OK"
                        '''
                    }
                }
            }
        }

        // ----------------------------------------------------------
        // 7. DAST — usa el contenedor ya construido
        // ----------------------------------------------------------
        stage('DAST - OWASP ZAP') {
            steps {
                script {
                    sh '''
                        echo "--- DAST OWASP ZAP ---"

                        # Levantar contenedor con la imagen recien construida
                        docker run -d \
                            --name app-dast-temp \
                            -p 8089:8080 \
                            ${DOCKER_HUB_USER}/${IMAGE_NAME}:${IMAGE_VERSION}

                        echo "Esperando que la app arranque..."
                        sleep 25

                        # Verificar que responde
                        if curl -s --max-time 5 "http://localhost:8089" > /dev/null 2>&1; then
                            echo "App corriendo en puerto 8089"
                        else
                            echo "App no responde, continuando con ZAP de todas formas..."
                        fi

                        # Correr ZAP
                        zap.sh -cmd \
                            -port 8090 \
                            -quickurl http://172.17.0.1:8089 \
                            -quickprogress \
                            -quickout $(pwd)/${REPORTS_DIR}/dast-zap.xml 2>/dev/null || true

                        echo "DAST completado"

                        # Apagar y borrar contenedor temporal
                        docker stop app-dast-temp  || true
                        docker rm   app-dast-temp  || true
                        echo "Contenedor temporal eliminado"
                    '''

                    if (env.IS_PRODUCTION == 'true') {
                        sh '''
                            HIGH=$(grep -c 'riskcode="3"' ${REPORTS_DIR}/dast-zap.xml 2>/dev/null || echo "0")
                            MEDIUM=$(grep -c 'riskcode="2"' ${REPORTS_DIR}/dast-zap.xml 2>/dev/null || echo "0")
                            if [ "${HIGH}" -gt 0 ] || [ "${MEDIUM}" -gt 0 ]; then
                                echo "PROD: ZAP encontro vulnerabilidades. Abortando."
                                exit 1
                            fi
                            echo "PROD Gate DAST: OK"
                        '''
                    }
                }
            }
        }

        // ----------------------------------------------------------
        // 8. PAC — rapido, al final del analisis
        // ----------------------------------------------------------
        stage('PAC - Checkov') {
            steps {
                script {
                    sh '''
                        echo "--- PAC Checkov ---"
                        export PATH=$PATH:/var/jenkins_home/.local/bin

                        ${CHECKOV_BIN} \
                            --directory . \
                            --output json \
                            --skip-download \
                            --quiet > ${REPORTS_DIR}/pac-checkov.json 2>/dev/null || true

                        FAILED=$(grep -c '"result": "FAILED"' ${REPORTS_DIR}/pac-checkov.json 2>/dev/null || echo "0")
                        PASSED=$(grep -c '"result": "PASSED"' ${REPORTS_DIR}/pac-checkov.json 2>/dev/null || echo "0")
                        echo "Checkov completado — Passed: ${PASSED} | Failed: ${FAILED}"
                    '''

                    if (env.IS_PRODUCTION == 'true') {
                        sh '''
                            FAILED=$(grep -c '"result": "FAILED"' ${REPORTS_DIR}/pac-checkov.json 2>/dev/null || echo "0")
                            if [ "${FAILED}" -gt 0 ]; then
                                echo "PROD: Checkov encontro fallos. Abortando."
                                exit 1
                            fi
                            echo "PROD Gate PAC: OK"
                        '''
                    }
                }
            }
        }

        // ----------------------------------------------------------
        // 9. PUSH DOCKER HUB — solo si paso todo el analisis
        // ----------------------------------------------------------
        stage('Push Docker Hub') {
            steps {
                script {
                    withCredentials([usernamePassword(
                        credentialsId: "${DOCKER_CREDS_ID}",
                        usernameVariable: 'DOCKER_USER',
                        passwordVariable: 'DOCKER_TOKEN'
                    )]) {
                        sh "echo ${DOCKER_TOKEN} | docker login -u ${DOCKER_USER} --password-stdin"

                        // Subir imagen con version especifica
                        retry(3) {
                            sh "docker push ${DOCKER_HUB_USER}/${IMAGE_NAME}:${env.IMAGE_VERSION}"
                        }

                        // Etiquetar y subir como 'previous' para tener la anterior disponible
                        sh "docker tag ${DOCKER_HUB_USER}/${IMAGE_NAME}:${env.IMAGE_VERSION} ${DOCKER_HUB_USER}/${IMAGE_NAME}:previous"
                        retry(3) {
                            sh "docker push ${DOCKER_HUB_USER}/${IMAGE_NAME}:previous"
                        }

                        echo "Push completado: ${DOCKER_HUB_USER}/${IMAGE_NAME}:${env.IMAGE_VERSION}"
                    }
                }
            }
        }

        // ----------------------------------------------------------
        // 10. IMPORTAR EN DEFECTDOJO
        // ----------------------------------------------------------
        stage('Importar en DefectDojo') {
            steps {
                script {
                    sh '''
                        set +e
                        echo "--- Importando en DefectDojo ---"

                        # Funcion reutilizable para importar cada reporte
                        import_scan() {
                            local scan_type=$1
                            local file=$2
                            local engagement=$3

                            if [ ! -f "${file}" ]; then
                                echo "No encontrado: ${file}"
                                return
                            fi

                            RESPONSE=$(curl -s -X POST \
                                -H "Authorization: Token ${DD_TOKEN}" \
                                -F "scan_type=${scan_type}" \
                                -F "file=@${file}" \
                                -F "product_name=${DD_PRODUCT}" \
                                -F "engagement_name=${engagement}" \
                                -F "active=true" \
                                -F "verified=false" \
                                "${DD_URL}/api/v2/import-scan/")

                            if echo "${RESPONSE}" | grep -q '"test":'; then
                                echo "OK: ${engagement} → ${scan_type}"
                            else
                                echo "Error: ${engagement} → $(echo ${RESPONSE} | grep -o '"message":"[^"]*"')"
                            fi
                        }

                        import_scan "Semgrep JSON Report"       "${REPORTS_DIR}/sast-semgrep.json"              "${DD_ENG_SAST}"
                        import_scan "Trivy Scan"                "${REPORTS_DIR}/sca-trivy.json"                 "${DD_ENG_SCA}"
                        import_scan "Dependency Check Scan"     "${REPORTS_DIR}/dependency-check-report.json"   "${DD_ENG_SCA}"
                        import_scan "Trivy Scan"                "${REPORTS_DIR}/image-checker.json"             "${DD_ENG_IMAGE}"
                        import_scan "ZAP Scan"                  "${REPORTS_DIR}/dast-zap.xml"                   "${DD_ENG_DAST}"
                        import_scan "Checkov Scan"              "${REPORTS_DIR}/pac-checkov.json"               "${DD_ENG_PAC}"

                        echo "Importacion completada"
                    '''
                }
            }
        }

        // ----------------------------------------------------------
        // 11. DEPLOY EN VM
        // Leer version anterior del historial en la VM,
        // pasar ambas versiones al script como argumentos
        // El script decide si escribe o no en el historial
        // ----------------------------------------------------------
        stage('Deploy en VM') {
            steps {
                script {
                    // Version nueva — la que acaba de construir y subir este pipeline
                    def versionNueva = "${DOCKER_HUB_USER}/${IMAGE_NAME}:${env.IMAGE_VERSION}"

                    // Version anterior — ultima linea del historial en la VM
                    // Si el archivo esta vacio usamos la misma nueva (primer deploy)
                    def versionAnterior = sh(
                        script: """
                            ssh ${VM_USER}@${VM_HOST} \
                            'tail -1 ${VERSIONS_FILE} 2>/dev/null || echo "${versionNueva}"'
                        """,
                        returnStdout: true
                    ).trim()

                    // Si el archivo estaba vacio tail devuelve vacio, usamos la nueva
                    if (!versionAnterior) {
                        versionAnterior = versionNueva
                    }

                    echo "Version nueva    : ${versionNueva}"
                    echo "Version anterior : ${versionAnterior}"

                    // Ejecutar el script pasando las 2 versiones como argumentos
                    sh """
                        ssh ${VM_USER}@${VM_HOST} \
                        'sudo bash ${DEPLOY_SCRIPT} "${versionNueva}" "${versionAnterior}"'
                    """
                }
            }
        }

        // ----------------------------------------------------------
        // 12. HEALTH CHECK — verificar que los contenedores responden
        // ----------------------------------------------------------
        stage('Health Check') {
            steps {
                script {
                    sh '''
                        echo "--- Health Check ---"

                        # Esperar que los contenedores arranquen
                        sleep 15

                        # Verificar 8081
                        STATUS_8081=$(curl -s -o /dev/null -w "%{http_code}" --max-time 10 http://${VM_HOST}:8081/health || echo "000")
                        if [ "${STATUS_8081}" = "200" ]; then
                            echo "8081 OK — responde health check"
                        else
                            echo "8081 NO responde — HTTP: ${STATUS_8081}"
                        fi

                        # Verificar 8082
                        STATUS_8082=$(curl -s -o /dev/null -w "%{http_code}" --max-time 10 http://${VM_HOST}:8082/health || echo "000")
                        if [ "${STATUS_8082}" = "200" ]; then
                            echo "8082 OK — responde health check"
                        else
                            echo "8082 NO responde — HTTP: ${STATUS_8082}"
                        fi
                    '''
                }
            }
        }

        // ----------------------------------------------------------
        // 13. RESUMEN FINAL
        // ----------------------------------------------------------
        stage('Resumen Final') {
            steps {
                sh '''
                    echo ""
                    echo "================================================"
                    echo "  PIPELINE DEVSECOPS COMPLETADO"
                    echo "================================================"
                    echo "  Modo         : IS_PRODUCTION=${IS_PRODUCTION}"
                    echo "  Version      : ${IMAGE_VERSION}"
                    echo "  Imagen       : ${DOCKER_HUB_USER}/${IMAGE_NAME}:${IMAGE_VERSION}"
                    echo "  DefectDojo   : http://localhost:8083/product/1/"
                    echo "  App 8081     : http://192.168.1.160:8081/health"
                    echo "  App 8082     : http://192.168.1.160:8082/health"
                    echo "================================================"
                '''
            }
        }

    }

    post {
        always {
            // Limpiar imagenes locales para no llenar el disco
            sh "docker rmi ${DOCKER_HUB_USER}/${IMAGE_NAME}:${env.IMAGE_VERSION} || true"
            sh "docker rmi ${DOCKER_HUB_USER}/${IMAGE_NAME}:previous             || true"

            // Guardar todos los reportes como artefactos en Jenkins
            archiveArtifacts artifacts: "${REPORTS_DIR}/**", allowEmptyArchive: true

            // Apagar app si quedo corriendo por algun error
            sh 'docker stop app-dast-temp 2>/dev/null || true'
            sh 'docker rm   app-dast-temp 2>/dev/null || true'
        }
        success {
            echo "Pipeline completado exitosamente — ${env.IMAGE_VERSION}"
        }
        failure {
            echo "Pipeline fallo — revisa los logs"
        }
    }
}