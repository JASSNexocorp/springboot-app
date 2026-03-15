# Errores intencionales para laboratorio de seguridad (OWASP Top 10 2021)

Este proyecto incluye **2 errores por categoría** para que los detecten las siguientes herramientas. Todos están alineados con **OWASP Top 10 2021**.

---

## 1. SAST (Static Application Security Testing) – Semgrep

**Qué es:** Análisis estático del código sin ejecutar la aplicación. Semgrep busca patrones inseguros en el fuente.

| # | Ubicación | OWASP Top 10 | Descripción | Cómo detectar |
|---|-----------|--------------|-------------|----------------|
| **SAST 1** | `OwaspDemoController.java` – constante `API_KEY` | A02:2021 Cryptographic Failures | Clave API hardcodeada en el código | `semgrep scan --config "p/java" .` (reglas de secretos / hardcoded-credentials) |
| **SAST 2** | `OwaspDemoController.java` – endpoint `GET /owasp/sast/reflect?q=` | A03:2021 Injection (XSS) | Parámetro de request devuelto sin codificar (reflected XSS en código) | Semgrep reglas de XSS / output no sanitizado |

**Comando ejemplo:**  
`semgrep scan --config "p/java" --config "p/owasp-top-ten" src/`

---

## 2. SCA (Software Composition Analysis) – Trivy + OWASP Dependency-Check

**Qué es:** Análisis de dependencias (librerías) conocidas vulnerables. No mira tu código, sino el árbol de dependencias.

| # | Ubicación | OWASP Top 10 | Dependencia | CVEs conocidos | Cómo detectar |
|---|-----------|--------------|-------------|----------------|----------------|
| **SCA 1** | `pom.xml` | A06:2021 Vulnerable and Outdated Components | `log4j-core` 2.14.0 | CVE-2021-44228 (Log4Shell), etc. | `trivy fs .` o `dependency-check.sh -s .` |
| **SCA 2** | `pom.xml` | A06:2021 Vulnerable and Outdated Components | `commons-collections` 3.2.1 | CVE-2015-6420 | `trivy fs .` o `dependency-check.sh -s .` |

**Comandos ejemplo:**  
- Trivy: `trivy fs .` (desde la raíz del proyecto)  
- OWASP Dependency-Check: `dependency-check.sh --project demo -s .`

---

## 3. DAST (Dynamic Application Security Testing) – OWASP ZAP

**Qué es:** Pruebas con la aplicación **en ejecución**. ZAP envía peticiones y analiza respuestas para encontrar vulnerabilidades en tiempo de ejecución.

| # | Endpoint | OWASP Top 10 | Descripción | Cómo detectar |
|---|----------|--------------|-------------|----------------|
| **DAST 1** | `GET /owasp/dast/xss?search=<script>alert(1)</script>` | A03:2021 Injection (XSS) | XSS reflejado: el valor de `search` se devuelve en HTML sin escapar | ZAP: Spider + Active Scan (XSS). Ejemplo: `http://localhost:8080/owasp/dast/xss?search=test` |
| **DAST 2** | `GET /owasp/injection/sql?username=admin' OR '1'='1` | A03:2021 Injection (SQL) | SQL Injection: concatenación de parámetro en la consulta | ZAP: Active Scan (SQL Injection). Ejemplo: `http://localhost:8080/owasp/injection/sql?username=test` |

**Pasos con ZAP:**  
1. Arrancar la app: `mvn spring-boot:run`  
2. En ZAP: Automated Scan con URL `http://localhost:8080`  
3. Revisar alertas de “Cross Site Scripting (Reflected)” y “SQL Injection”

---

## Resumen por herramienta

| Herramienta | Tipo | Errores en el proyecto |
|-------------|------|------------------------|
| **Semgrep** | SAST | 2 (hardcoded secret + reflejado sin codificar en `/owasp/sast/reflect`) |
| **Trivy** | SCA | 2 (log4j-core 2.14.0, commons-collections 3.2.1) |
| **OWASP Dependency-Check** | SCA | 2 (las mismas 2 dependencias) |
| **OWASP ZAP** | DAST | 2 (XSS en `/owasp/dast/xss`, SQLi en `/owasp/injection/sql`) |

*Nota: OWASP Top 10 2025 no estaba publicado al crear este documento; se usa OWASP Top 10 2021 como referencia.*
