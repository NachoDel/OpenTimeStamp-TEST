# TrustHub OpenTimestamp POC

## ¿Qué es OpenTimestamp?

OpenTimestamps (OTS) es un protocolo abierto que permite **demostrar criptográficamente que un contenido existía en o antes de una fecha determinada**, usando Bitcoin como autoridad pública, sin subir el archivo completo a la blockchain.

En resumidas cuentas, su funcionamiento se puede describir de la siguiente manera:
-   Calcula el **hash** del archivo (ej. SHA-256).
    
-   Agrupa muchos hashes en un **Merkle tree** y publica la raíz como compromiso en Bitcoin (a través de calendarios/aggregadores).
    
-   El `.ots` es el fichero de prueba que contiene la ruta/operaciones necesarias para reconstruir y verificar la relación entre el hash del archivo y la raíz publicada.
    
-   workflow: `stamp` → (guardar `.ots` base) → `upgrade` (obtener attestations) → `verify` (comprobar on-chain).

## Descripcion del proyecto

A la hora de realizar este proyecto, lo plantie en base a 4 métodos basicos, de los cuales luego realice algunas implementaciones más complejas haciendo uso de estos métodos.

Los método basicos son:

#### stampFile
    

-   Como su nombre indica, se encarga de realizar el stamping de un archivo.
    

#### info
    

-   Muestra la información contenida en el archivo .ots
    

#### upgrade
    

-   Se encarga de “completar” el archivo .ots, cuando el hash de nuestro archivo se encuentra publicado en la chain
    

#### verify
    

-   Método por el cual se verifica la publicación de nuestro archivo en la chain


# Getting started
## Declaracion de dependencia atravez de maven


Lo primero que debemos hacer para poder utilizar OTS en java, es instanciarlo, utilizando Maven. Se debe agregar las siguientes líneas al `pom.xml`
```xml
<dependencies>
    <dependency>
        <groupId>com.eternitywall</groupId>
        <artifactId>java-opentimestamps</artifactId>
        <version>1.20</version>
    </dependency>
</dependencies>
```

## Utilización

En este apartado voy a explicar de manera genérica como se puede utilizar esta librería. Esto no significa que sea la única forma, la forma aquí explicada es en la cual yo le encontre forma de uso. Probablemente haya mejores maneras de realizar lo que aquí va a ser explicado.

#### _Cosas a tener en cuenta para entender el uso:_

-   Las direcciones de los archivos, deben ser representadas mediantes variables de tipo Path (de todas formas hay algunos `wrapper` implementados que convierten una direccion de tipo `string` a una de tipo `path`)
    
-   Los métodos son explicados en su funcionamiento base, es decir, se debe tener en cuenta en algunos casos. Como por ejemplo problemas que puedan llegar a surgir (manejo de excepciones); se debe tener en cuenta que no se pase un Path inexistente; entre otros.
    
-   Aqui esta explicado como funciona la base del método, lo que significa que no necesariamente representa la implementación final en el proyecto

## Metodos

### stampFile(Path filePath)
- **`filePath` →** Path del archivo a stampear  

Como primer paso para realizar un stamp, debemos recrear el archivo original (el cual queremos stampear), utilizando su ubicación:

```java
File file = filePath.toFile();
```

Luego realizamos el stamp utilizando la librería **OpenTimestamps**:

```java
DetachedTimestampFile detached = DetachedTimestampFile.from(new OpSHA256(), file);
OpenTimestamps.stamp(detached);
```

Aqui observamos dos procesos:
1. Se crea un objeto `DetachedTimestampFile` usando el hash **SHA256** del archivo.  
2. Se aplica el timestamp mediante `OpenTimestamps.stamp(detached)`, que:
   - Calcula el hash del archivo.  
   - Envía el hash a los calendarios de timestamp.  
   - Obtiene las pruebas criptográficas iniciales.  

Este procedimiento **no genera el archivo `.ots` en disco**, por lo que debemos serializar y guardarlo manualmente.

```java
Path inputPath = filePath.toAbsolutePath();
Path parent = inputPath.getParent();
String baseName = inputPath.getFileName().toString();

Path outputPath = (parent != null)
    ? parent.resolve(baseName + ".ots")
    : Paths.get(baseName + ".ots");
```

Aquí en la resolución de `Path  outputPath  = (parent  !=  null) ?  parent.resolve(baseName  +  ".ots") :  Paths.get(baseName  +  ".ots");` es donde se genera el path correspondiente a la ubicación del archivo a stampear y se le asigna a su vez un nombre definido de la siguiente manera: *nombreArchivoStampeado.fileType.ots*

Finalmente, se serializa y guarda:

```java
byte[] otsBytes = detached.serialize();
Files.write(outputPath, otsBytes,
    StandardOpenOption.CREATE,
    StandardOpenOption.TRUNCATE_EXISTING,
    StandardOpenOption.WRITE);
```
---
### info(Path otsPath)
- **`otsPath` →** Path del archivo `.ots`

Primero reconstruimos el archivo (timestamp) pasando su ruta como referencia:

```java
DetachedTimestampFile detached = readDetachedFromOts(otsPath);
```

Luego extraemos la información con:

```java
String info = OpenTimestamps.info(detached);
```

El resultado se guarda en un `String` que puede ser utilizado según necesidad.

---

### upgrade(Path otsPath)
- **`otsPath` →** Path del archivo `.ots`

El primer paso es igual al paso del metodo “info”, es decir, debemos reconstruir el archivo. Esto se puede hacer de la siguiente manera:

```java
byte[] otsBytes = Files.readAllBytes(otsPath);
DetachedTimestampFile detachedOts = DetachedTimestampFile.deserialize(otsBytes);
```

Luego tratamos de **upgradear** ( es decir, completar) el stamp.  
>  Nota: Ejecutar este metodo puede fallar, en el caso de que el stamp aún no fue publicado en la blockchain.

```java
boolean changed = OpenTimestamps.upgrade(detachedOts);
```

El resultado de este método `(OpenTimestamps.upgrade(detachedOts))`, aconsejo guardarlo en una variable booleana (ya que devuelve true o false) con el objetivo de saber si se pudo realizar el upgrade.

Suponiendo que **SI** pudo realizarse el upgrade, debemos serializar el nuevo archivo y guardarlo de la siguiente manera: 

```java
byte[] newOts = detachedOts.serialize();
Files.write(otsPath, newOts,
    StandardOpenOption.CREATE,
    StandardOpenOption.TRUNCATE_EXISTING,
    StandardOpenOption.WRITE);
```

Esta forma de guardar el stamp, es la misma que se utilizo a la hora de crearlo.

---
### verify (Path otsPath, Path originalPath)
- **`otsPath` →** Path del archivo `.ots`  
- **`originalPath` →** Path del archivo original (stampeado)

Para verificar si un archivo fue stampeado con respaldo en blockchain, necesitamos:

- El archivo `.ots` (completo, con upgrade).  
- El archivo original al que se aplicó el método `stampFile`.  

Una vez que tengamos ambos archivos, procedemos a reconstruirlos:

```java
DetachedTimestampFile detachedOts = readDetachedFromOts(otsPath);
DetachedTimestampFile detachedOrig = DetachedTimestampFile.from(new OpSHA256(), new File(originalPath.toString()));
```

Ahora probamos raelizar la verificación:

```java
Map<?, ?> verifyResults = (Map<?, ?>) OpenTimestamps.verify(detachedOts, detachedOrig);
```

Este método `(OpenTimestamps.verify)` retorna un hashmap que contiene la altura del bloque y la marca de tiempo indexadas por la cadena
El hecho de guardar el resultado del método en una variable (en este caso verifyResults) no es necesario, pero nos permite mostrar la información del bloque en el cual esta contenido nuestro hash.

---

