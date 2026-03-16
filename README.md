Laboratorio 3: Spring Boot + Jenkins + DevSecOps
=================================================

Este repositorio contiene una aplicación de ejemplo en **Spring Boot** con un pipeline de **CI/CD en Jenkins** y **errores de seguridad intencionales** para practicar:

- **SAST (Static Application Security Testing)** – análisis estático de código.
- **SCA (Software Composition Analysis)** – análisis de dependencias.
- **Image Checker** – escaneo de imágenes de contenedores (ej. Trivy).
- **DAST (Dynamic Application Security Testing)** – pruebas dinámicas con la app levantada.
- **Policy as Code (PaC)** – validación de configuraciones y políticas como código.

Todos los errores están documentados con más detalle en `SECURITY-DEMO.md`.

---

## Requisitos previos (Windows)

- **JDK 17** (o compatible con el `pom.xml`).
- **Maven** instalado y agregado a la variable de entorno `PATH`.
- **Docker Desktop** (para la parte de contenedores/Jenkins).
- Git.

Verifica que todo está instalado ejecutando en **CMD (no PowerShell)**:

```cmd
java -version
mvn -version
git --version
docker --version
```

---

## 1. Cómo levantar el proyecto (Windows CMD)

Desde CMD, en la carpeta del proyecto:

```cmd
cd "C:\Users\jsahonero\Desktop\DIPLOMADO\MODULO 4\SpringBoot_CI_CD_Lab\springboot-app"
mvn clean package
mvn spring-boot:run
```

La aplicación quedará levantada (por defecto) en:

```cmd
http://localhost:8080
```

Para parar la app, vuelve a la ventana donde corre y presiona `Ctrl + C`.

---

## 2. Jenkins + herramientas de seguridad en contenedores

En la carpeta `contenedores/jenkins-devsecops` tienes un `Dockerfile` que instala:

- **Semgrep** → SAST.
- **Trivy** → SCA + Image Checker.
- **OWASP Dependency-Check** → SCA / PaC de dependencias.
- **OWASP ZAP** → DAST.

Comandos típicos desde CMD, situándote en la carpeta del proyecto:

```cmd
cd "C:\Users\jsahonero\Desktop\DIPLOMADO\MODULO 4\SpringBoot_CI_CD_Lab\springboot-app\contenedores\jenkins-devsecops"
docker build -t jenkins-devsecops .
docker run -d -p 8081:8080 -p 50000:50000 --name jenkins-devsecops jenkins-devsecops
```

Luego puedes acceder a Jenkins en:

```cmd
http://localhost:8081
```

> Nota: el `Dockerfile` incluye intencionalmente algunos **problemas de buena práctica de imagen** (por ejemplo, instalación de paquetes innecesarios) para que herramientas como **Trivy** los marquen como hallazgos.

---

## 3. Errores de seguridad intencionales por categoría

Debes **mantener estos errores**, no corregirlos, para que las herramientas los detecten durante el laboratorio.

### 3.1. SAST (código fuente – Semgrep)

Archivo: `OwaspDemoController.java`

- **SAST 1 – API key hardcodeada**
  - **Qué buscar en el IDE:** `API_KEY`
  - **Tipo:** A02:2021 Cryptographic Failures.
  - **Por qué es un error:** una clave secreta no debe ir en el código; debe venir de variables de entorno o un gestor de secretos.
  - **Cómo se detecta:** reglas de **Semgrep** para *hardcoded secrets*.

- **SAST 2 – Reflected XSS en endpoint**
  - **Qué buscar en el IDE:** `/owasp/sast/reflect`
  - **Tipo:** A03:2021 Injection (XSS).
  - **Por qué es un error:** el parámetro de entrada se devuelve directamente en la respuesta sin escapado/validación.
  - **Cómo se detecta:** reglas de **Semgrep** para **XSS** / salida no sanitizada.

Comando ejemplo en el contenedor Jenkins o en tu máquina:

```cmd
semgrep scan --config "p/java" --config "p/owasp-top-ten" src/
```

### 3.2. SCA (dependencias – Trivy / Dependency-Check)

Archivo: `pom.xml`

- **SCA 1 – log4j vulnerable**
  - **Qué buscar en el IDE:** `log4j-core`
  - **Por qué es un error:** la versión usada tiene CVEs críticos (ej. **Log4Shell**).
  - **Herramientas:** **Trivy**, **OWASP Dependency-Check**.

- **SCA 2 – commons-collections vulnerable**
  - **Qué buscar en el IDE:** `commons-collections`
  - **Por qué es un error:** versión antigua con vulnerabilidades conocidas.
  - **Herramientas:** **Trivy**, **OWASP Dependency-Check**.

Ejemplos de comandos (desde la raíz del repo, con las herramientas instaladas):

```cmd
trivy fs .
dependency-check.sh --project demo -s .
```

### 3.3. Image Checker (imágenes Docker – Trivy)

Archivo: `contenedores/jenkins-devsecops/Dockerfile`

Ejemplos de “errores” intencionales:

- Instalación de muchas herramientas en la misma imagen (`maven`, `docker.io`, ZAP, etc.).
- Uso de paquetes del sistema que pueden quedar desactualizados con vulnerabilidades.

**Qué buscar en el IDE:**

- `FROM jenkins/jenkins:lts`
- `docker.io`
- `trivy`
- `zap.sh`

**Por qué serán detectados:**

- **Trivy** analiza la imagen resultante y reporta:
  - Paquetes del sistema con CVEs.
  - Versiones vulnerables de binarios/librerías dentro de la imagen.

Ejemplo (desde la carpeta del `Dockerfile`, después de hacer `docker build`):

```cmd
docker images
trivy image jenkins-devsecops
```

### 3.4. DAST (aplicación en ejecución – OWASP ZAP)

Endpoints vulnerables (app levantada en `http://localhost:8080`):

- **DAST 1 – XSS reflejado**
  - **URL:** `/owasp/dast/xss?search=`
  - **Qué buscar en el IDE:** `/owasp/dast/xss`
  - **Por qué es un error:** el valor de `search` se devuelve en HTML sin escapar.

- **DAST 2 – SQL Injection**
  - **URL:** `/owasp/injection/sql?username=`
  - **Qué buscar en el IDE:** `/owasp/injection/sql`
  - **Por qué es un error:** el parámetro se concatena directamente en la consulta SQL.

Pasos simplificados con ZAP:

```text
1. Levantar la app: mvn spring-boot:run
2. En ZAP, lanzar un "Automated Scan" contra http://localhost:8080
3. Revisar alertas de "Cross Site Scripting (Reflected)" y "SQL Injection"
```

### 3.5. Policy as Code (PaC)

En este laboratorio, **PaC** se representa principalmente con:

- Reglas de seguridad que se ejecutan en el pipeline (por ejemplo, fallar el build si Trivy/Dependency-Check reportan vulnerabilidades críticas).
- Configuración del Jenkinsfile y scripts de despliegue que tratan las políticas (por ejemplo, no desplegar si hay hallazgos de cierto nivel).

**Qué buscar en el IDE para ubicar esta lógica:**

- Archivo: `Jenkinsfile`  
  - Buscar: `semgrep`, `trivy`, `dependency-check`, `zap`.
- Archivo: `delploy.sh` / `delploy.sh.modulo4Final`  
  - Buscar: `trivy`, `kubectl`, `policy`.

La idea es que el **pipeline se comporte como “código de políticas”**: si se detectan problemas de seguridad definidos por la organización, el pipeline falla y no se continúa con el despliegue.

---

## 4. Cómo encontrar rápido los errores en el IDE

Dentro de tu IDE (por ejemplo, Cursor/VS Code) puedes usar el **buscador global** y escribir alguno de estos textos para ir directo al código vulnerable:

- `API_KEY`
- `/owasp/sast/reflect`
- `log4j-core`
- `commons-collections`
- `/owasp/dast/xss`
- `/owasp/injection/sql`
- `semgrep`
- `trivy`
- `dependency-check`
- `zap.sh`

Con eso podrás localizar el código y entender por qué cada herramienta marca el hallazgo, sin tener que recorrer todo el proyecto manualmente.
