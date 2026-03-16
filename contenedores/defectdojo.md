# Levantar Jenkins DevSecOps — Windows

## 1. Ir a la carpeta del proyecto

```cmd
cd ruta\a\jenkins-devsecops
```

## 2. Construir y levantar

```cmd
docker compose up -d --build
```

> Primera vez tarda 5-15 min. Las siguientes es inmediato.

## 3. Abrir Jenkins

```
http://localhost:8083
```

## 4. Contraseña inicial

```cmd
docker exec jenkins-devsecops cat /var/jenkins_home/secrets/initialAdminPassword
```

---

## Parar

```cmd
docker compose down
```

## Parar sin perder datos

```cmd
docker compose stop
```

---

> El puerto es **8083** porque en `docker-compose.yml` está configurado `"8083:8080"`.

---

> **Por qué el puerto 8083:**
> En `docker-compose.yml` el bloque de Nginx tiene `published: ${DD_PORT:-8083}`.
> Esto significa que si la variable `DD_PORT` no está definida, el puerto por defecto es `8083`.
> El valor original era `8080` — se cambió a `8083` directamente en ese archivo.
