package com.example.demo;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.web.bind.annotation.*;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.security.MessageDigest;
import java.sql.*;
import java.util.Base64;

@RestController
@RequestMapping("/owasp")
public class OwaspDemoController {

    // A01: Broken Access Control - ejemplo 1 y 2

    // Ejemplo 1: endpoint administrativo sin verificación de rol
    @DeleteMapping("/admin/users/{id}")
    public String deleteUserWithoutRoleCheck(@PathVariable("id") Long id) {
        return "Usuario con id " + id + " eliminado (sin verificación de rol)";
    }

    // Ejemplo 2: IDOR - se accede a datos de cuenta solo por ID
    @GetMapping("/accounts/{id}")
    public String insecureAccountAccess(@PathVariable("id") Long id) {
        return "Detalles de la cuenta " + id + " mostrados sin verificar el propietario";
    }

    // A02: Security Misconfiguration - se utilizan properties y configuración insegura

    @Autowired
    private Environment environment;

    // Ejemplo 1: exponer información de configuración sensible
    @GetMapping("/config-info")
    public String insecureConfigInfo() {
        String dbUrl = environment.getProperty("spring.datasource.url", "jdbc:h2:mem:testdb");
        return "Configuración (insegura) visible públicamente: " + dbUrl;
    }

    // Ejemplo 2: endpoint que indica que el H2 console está “expuesto”
    @GetMapping("/h2-console-hint")
    public String h2ConsoleHint() {
        return "La consola H2 puede estar expuesta en /h2-console (simulación de misconfiguración).";
    }

    // A03: Software Supply Chain Failures
    // La vulnerabilidad principal se simulará en pom.xml con dependencias inseguras.
    // Aquí solo añadimos un endpoint “marcador”.

    @GetMapping("/supply-chain/info")
    public String supplyChainInfo() {
        return "Este proyecto usa dependencias potencialmente vulnerables para pruebas de cadena de suministro.";
    }

    @GetMapping("/supply-chain/dynamic-load")
    public String insecureDynamicLoad(@RequestParam(name = "url", required = false) String url) {
        return "Descarga dinámica simulada desde: " + url + " (sin validación de integridad).";
    }

    // A04: Cryptographic Failures

    // Ejemplo 1: password almacenado en texto plano (simulado)
    @PostMapping("/crypto/plain-password")
    public String storePlainPassword(@RequestParam("password") String password) {
        // Simulamos guardar password en texto plano
        return "Password recibido y (supuestamente) guardado en texto plano: " + password;
    }

    // Ejemplo 2: uso de algoritmo débil / clave hardcodeada
    @GetMapping("/crypto/weak-hash")
    public String weakHash(@RequestParam("input") String input) throws Exception {
        MessageDigest md5 = MessageDigest.getInstance("MD5");
        byte[] digest = md5.digest(input.getBytes());
        return "Hash MD5 inseguro: " + Base64.getEncoder().encodeToString(digest);
    }

    // Extra: cifrado con modo ECB y clave hardcodeada
    @GetMapping("/crypto/weak-ecb")
    public String weakEcb(@RequestParam("text") String text) throws Exception {
        String hardcodedKey = "1234567890123456"; // clave hardcodeada
        SecretKeySpec keySpec = new SecretKeySpec(hardcodedKey.getBytes(), "AES");
        Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
        cipher.init(Cipher.ENCRYPT_MODE, keySpec);
        byte[] encrypted = cipher.doFinal(text.getBytes());
        return Base64.getEncoder().encodeToString(encrypted);
    }

    // A05: Injection

    // Ejemplo 1: SQL Injection usando concatenación de parámetros
    @GetMapping("/injection/sql")
    public String sqlInjection(@RequestParam("username") String username) {
        StringBuilder result = new StringBuilder();
        Connection conn = null;
        Statement stmt = null;
        ResultSet rs = null;
        try {
            conn = DriverManager.getConnection("jdbc:h2:mem:testdb", "sa", "");
            stmt = conn.createStatement();
            // Consulta vulnerable a SQLi
            String query = "SELECT * FROM USERS WHERE USERNAME = '" + username + "'";
            rs = stmt.executeQuery(query);
            while (rs.next()) {
                result.append("Usuario: ").append(rs.getString("USERNAME")).append("\n");
            }
        } catch (Exception e) {
            return "Error ejecutando consulta vulnerable: " + e.getMessage();
        } finally {
            try {
                if (rs != null) rs.close();
                if (stmt != null) stmt.close();
                if (conn != null) conn.close();
            } catch (Exception ignored) {
            }
        }
        return result.toString();
    }

    // Ejemplo 2: Command Injection
    @GetMapping("/injection/command")
    public String commandInjection(@RequestParam("cmd") String cmd) {
        StringBuilder output = new StringBuilder();
        try {
            Process process = Runtime.getRuntime().exec(cmd);
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
        } catch (Exception e) {
            return "Error ejecutando comando: " + e.getMessage();
        }
        return "Salida del comando:\n" + output;
    }

    // A06: Insecure Design

    // Ejemplo 1: reset de contraseña solo con email (sin token)
    @PostMapping("/design/reset-password")
    public String insecureResetPassword(@RequestParam("email") String email,
                                        @RequestParam("newPassword") String newPassword) {
        return "Password de " + email + " cambiado directamente sin token ni verificación adicional.";
    }

    // Ejemplo 2: aprobación crítica solo con un flag en la petición
    @PostMapping("/design/approve-transaction")
    public String insecureApprove(@RequestParam("transactionId") String transactionId,
                                  @RequestParam("approved") boolean approved) {
        if (approved) {
            return "Transacción " + transactionId + " aprobada solo por un flag en la petición.";
        } else {
            return "Transacción " + transactionId + " no aprobada.";
        }
    }

    // A07: Authentication Failures

    // Ejemplo 1: login sin límite de intentos
    @PostMapping("/auth/login")
    public String insecureLogin(@RequestParam("user") String user,
                                @RequestParam("password") String password) {
        if ("admin".equals(user) && "admin".equals(password)) {
            return "Login exitoso (sin límite de intentos ni medidas adicionales).";
        }
        return "Credenciales incorrectas, pero puedes intentar infinitas veces.";
    }

    // Ejemplo 2: token sin expiración adecuada (simulado)
    @GetMapping("/auth/token")
    public String insecureToken(@RequestParam("user") String user) {
        String fakeToken = "FAKE-TOKEN-FIJO-PARA-" + user;
        return "Token inseguro sin expiración: " + fakeToken;
    }

    // A08: Software or Data Integrity Failures

    // Ejemplo 1: deserialización insegura (simulada)
    @PostMapping("/integrity/deserialize")
    public String insecureDeserialize(@RequestBody byte[] data) {
        return "Datos binarios deserializados sin validación (simulado). Tamaño: " + data.length;
    }

    // Ejemplo 2: uso de configuración externa sin verificar integridad
    @GetMapping("/integrity/config-from-url")
    public String configFromUrl(@RequestParam("url") String url) {
        return "Configuración cargada desde URL externa sin firma ni checksum: " + url;
    }

    // A09: Security Logging and Alerting Failures

    // Ejemplo 1: falta de logging en evento crítico (cambio de contraseña)
    @PostMapping("/logging/change-password")
    public String noLoggingChangePassword(@RequestParam("user") String user,
                                          @RequestParam("newPassword") String newPassword) {
        // No se registra ningún log de este evento crítico
        return "Password cambiado para usuario " + user + " (sin logging de seguridad).";
    }

    // Ejemplo 2: logging de información sensible
    @PostMapping("/logging/login-verbose")
    public String verboseLogging(@RequestParam("user") String user,
                                 @RequestParam("password") String password) {
        System.out.println("Intento de login con user=" + user + " y password=" + password);
        return "Login procesado (las credenciales se registran en logs, lo cual es inseguro).";
    }

    // A10: Mishandling of Exceptional Conditions

    // Ejemplo 1: devolver stack trace directamente al cliente
    @GetMapping("/errors/stacktrace")
    public String stackTraceExposure() {
        try {
            int x = 1 / 0;
            return "Resultado: " + x;
        } catch (Exception e) {
            e.printStackTrace();
            return "Error interno: " + e.toString();
        }
    }

    // Ejemplo 2: mensajes de error reveladores
    @GetMapping("/errors/login-detailed")
    public String detailedLoginError(@RequestParam("user") String user,
                                     @RequestParam("password") String password) {
        if (!"admin".equals(user)) {
            return "Error: el usuario no existe en la base de datos.";
        }
        if (!"admin".equals(password)) {
            return "Error: password incorrecto para el usuario admin.";
        }
        return "Login correcto.";
    }
}

