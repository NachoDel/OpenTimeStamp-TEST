
# TrustHub OpenTimestamp POC
## ¿Qué es OpenTimestamp?
OpenTimestamps (OTS) es un protocolo abierto que permite **demostrar criptográficamente que un contenido existía en o antes de una fecha determinada**, usando Bitcoin como autoridad pública, sin subir el archivo completo a la blockchain.

En resumidas cuentas, su funcionamiento se puede describir de la siguiente manera:
- Calcula el **hash** del archivo (ej. SHA-256).
  
-   Agrupa muchos hashes en un **Merkle tree** y publica la raíz como compromiso en Bitcoin (a través de calendarios/aggregadores).
  
-   El `.ots` es el fichero de prueba que contiene la ruta/operaciones necesarias para reconstruir y verificar la relación entre el hash del archivo y la raíz publicada.
  
-   workflow: `stamp` → (guardar `.ots` base) → `upgrade` (obtener attestations) → `verify` (comprobar on-chain).
## Descripcion del proyecto
A la hora de realizar este proyecto, lo plantie en base a 4 métodos basicos, de los cuales luego realice algunas implementaciones más complejas haciendo uso de estos métodos.
Los método basicos son:
#### stampFile
  
- Como su nombre indica, se encarga de realizar el stamping de un archivo.
  
#### info
  
- Muestra la información contenida en el archivo .ots
  
#### upgrade
  
- Se encarga de “completar” el archivo .ots, cuando el hash de nuestro archivo se encuentra publicado en la chain
  
#### verify
  
- Método por el cual se verifica la publicación de nuestro archivo en la chain

---

# OpenTimestamps — Microservicio (UI mínima)

> Microservicio Spring Boot para generar, upgradear, consultar y verificar timestamps con **OpenTimestamps**. Incluye una UI muy simple (`index.html`) para pruebas manuales (drag & drop + botones).

---

## En resumen:
- Servicio REST + UI para trabajar con OpenTimestamps.  
- Permite: **stamp** (generar `.ots` desde un PDF), **upgrade** (actualizar `.ots`), **info** (ver info legible de un `.ots`) y **verify** (verificar `.ots` contra el PDF original).  
- La UI está en `src/main/resources/static/index.html` y se sirve desde `http://localhost:8080/index.html`.  
- No almacenamos PDFs ni `.ots` en base de datos en esta versión; todo es temporal (termina siendo descargado en la pc del usuario).  
- Se intenta obtener la **hora de minado del bloque** (`block_time`) consultando Blockstream cuando es posible y se la devuelve en la respuesta JSON de `verify` **CHEQUEAR si funciona como esperado** .

---

##  Estructura principal
- `src/main/java/.../controller/OtsController.java` — endpoints REST: `/api/ots/{stamp,upgrade,info,verify}`  
- `src/main/java/.../service/OpenTimestampsService.java` — lógica que llama a la librería `com.eternitywall.ots`  
- `src/main/resources/static/index.html` — UI para pruebas manuales  
- `pom.xml` — dependencias (Spring Boot, OpenTimestamps lib, etc.)

---

## ⚙️Endpoints
Todos los endpoints son `POST` y esperan `multipart/form-data`.

### **POST /api/ots/stamp**
- **Parámetros:**  
  `file` → archivo PDF  
- **Respuesta:** `application/octet-stream` (descarga `.ots`)  
- **Nombre del archivo devuelto:** `<NombreOriginalDelPDF>.ots`  
  (ej. `documento.pdf` → `documento.pdf.ots`)

---

### **POST /api/ots/upgrade**
- **Parámetros:**  
  `ots` → archivo `.ots`  
- **Respuesta:** `application/octet-stream` (descarga `.ots` actualizado)  
- **Nombre del archivo devuelto:** `<NombreOriginal>-Upgraded.ots`  
  (ej. `documento.pdf.ots` → `documento.pdf-Upgraded.ots`)

---

### **POST /api/ots/info**
- **Parámetros:**  
  `ots` → archivo `.ots`  
- **Respuesta:** texto legible con la salida de `OpenTimestamps.info()`  

---

### **POST /api/ots/verify**
- **Parámetros:**  
  `ots` → archivo `.ots`  
  `file` → PDF original  
- **Respuesta:** JSON con metadatos del bloque y estado de verificación  
  ```json
  {
    "status": "OK" | "FAIL",
    "txid": "<txid|null>",
    "block_hash": "<hash|null>",
    "block_height": "<numero|null>",
    "block_time": "2025-09-21T12:34:56Z" | null,
  }