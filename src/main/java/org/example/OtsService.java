package org.example;
// -------------- Imports --------------
import com.eternitywall.ots.*;
import com.eternitywall.ots.op.OpSHA256;
import java.io.File;
import com.eternitywall.ots.DetachedTimestampFile;
import com.eternitywall.ots.OpenTimestamps;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.nio.file.*;
import java.nio.channels.FileChannel;
// -------------- Imports --------------

public class OtsService {

    // -------------- STAMP --------------
    /**
     * Aplica un timestamp (stamp) a un archivo y guarda el .ots resultante
     * nota: Tener en cuenta q es el OTS BASE osea que HAY Q UPGRADEARLO LUEGO
     */
    // Wrapper que acepta String (no rompe callers antiguos)
    public static void stampFile(String filePathStr) throws Exception {
        stampFile(Paths.get(filePathStr));
    }
    // Versión principal que recibe Path
    public static void stampFile(Path filePath) throws Exception {
        // Convertir a File sólo si la librería lo requiere (DetachedTimestampFile.from acepta File en tu ejemplo)
        File file = filePath.toFile();

        if (!file.exists()) {
            System.err.println("❌ El archivo no existe: " + file.getAbsolutePath());
            return;
        }

        // Crear detached y hacer stamp
        DetachedTimestampFile detached = DetachedTimestampFile.from(new OpSHA256(), file);
        OpenTimestamps.stamp(detached);

        // Info legible
        String infoResult = OpenTimestamps.info(detached);
        System.out.println("📌 Resultado de OTS (info):");
        System.out.println(infoResult);

        // --------------------------------------------------
        // Construir ruta de salida: mismo directorio, nombre + .ots
        // --------------------------------------------------
        Path inputPath = filePath.toAbsolutePath();
        Path parent = inputPath.getParent();
        String baseName = inputPath.getFileName().toString();
        Path outputPath = (parent != null)
                ? parent.resolve(baseName + ".ots")
                : Paths.get(baseName + ".ots");

        System.out.println("Voy a intentar guardar el .ots en: " + outputPath);

        // Serializar UNA vez y reutilizar
        byte[] otsBytes = detached.serialize();
        if (otsBytes == null) {
            System.err.println("ERROR: detached.serialize() devolvió null. No se puede guardar.");
            return;
        }
        System.out.println("Tamaño del .ots serializado: " + otsBytes.length + " bytes");

        // Asegurar directorio padre y escribir
        try {
            if (outputPath.getParent() != null && !Files.exists(outputPath.getParent())) {
                Files.createDirectories(outputPath.getParent());
                System.out.println("Se creó el directorio padre: " + outputPath.getParent());
            }

            Files.write(outputPath, otsBytes,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE);

            // Forzar flush a disco (fsync)
            try (FileChannel fc = FileChannel.open(outputPath, StandardOpenOption.WRITE)) {
                fc.force(true);
            } catch (IOException e) {
                System.err.println("Aviso: no se pudo forzar fsync: " + e.getMessage());
            }

            // Comprobación final
            if (Files.exists(outputPath)) {
                System.out.println("Se guardó el archivo .ots en: " + outputPath);
                System.out.println("Tamaño en disco: " + Files.size(outputPath) + " bytes");
                byte[] readBack = Files.readAllBytes(outputPath);
                System.out.println("Lectura de comprobación: bytes leídos = " + readBack.length);
            } else {
                System.err.println("Después de Files.write el archivo NO existe (muy extraño).");
            }

        } catch (IOException ioe) {
            System.err.println("IOException al guardar .ots: " + ioe.getMessage());
            ioe.printStackTrace();
        }
    }
    // CHEQUEAR SI FUNCIONA CON PATHFILE!!!!!!!!!!!!1

    // -------------- UPGRADE. Return TRUE si upgradeo y reemplazo el archivo --------------
    /**
     *    Upgradear in-place un archivo .ots (ruta por referencia).
     *    Devuelve true si OpenTimestamps.upgrade(...) modificó el objeto (y se sobrescribió el .ots).
     */
    public static boolean upgradeOts(Path otsPath) throws Exception {
        System.out.println("-> upgradeOts: " + otsPath.toAbsolutePath());
        if (!Files.exists(otsPath)) {
            throw new IllegalArgumentException("El archivo .ots no existe: " + otsPath.toAbsolutePath());
        }

        // Leer y deserializear
        byte[] otsBytes = Files.readAllBytes(otsPath);
        DetachedTimestampFile detachedOts = DetachedTimestampFile.deserialize(otsBytes);

        // Llamar upgrade (intenta completar attestations)
        boolean changed = OpenTimestamps.upgrade(detachedOts);
        System.out.println("OpenTimestamps.upgrade() devolvió: " + changed);

        if (changed) {
            // Serializar y sobrescribir el .ots en disco
            byte[] newOts = detachedOts.serialize();
            if (newOts == null) {
                throw new IOException("serialize() devolvió null luego de upgrade.");
            }
            Files.write(otsPath, newOts, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
            // Forzar fsync
            try (FileChannel fc = FileChannel.open(otsPath, StandardOpenOption.WRITE)) {
                fc.force(true);
            } catch (IOException e) {
                System.err.println("Aviso: no se pudo forzar fsync al guardar .ots actualizado: " + e.getMessage());
            }
            System.out.println("Se sobrescribió el .ots con la versión actualizada: " + otsPath.toAbsolutePath());
        } else {
            System.out.println("No hubo cambios tras upgrade().");
        }

        return changed;
    }
    // ---- FUNCIONA ---- FUNCIONA ----- FUNCIONA ---- FUNCIONA ---- FUNCIONA ----


    // -------- VERIFY. Retorna TRUE si verifico el archivo --------
    /**
     *  Leer y devolver DetachedTimestampFile a partir de un .ots (ruta por referencia).
     */
    public static DetachedTimestampFile readDetachedFromOts(Path otsPath) throws Exception {
        if (!Files.exists(otsPath)) {
            throw new IllegalArgumentException("El archivo .ots no existe: " + otsPath.toAbsolutePath());
        }
        byte[] otsBytes = Files.readAllBytes(otsPath);
        DetachedTimestampFile detached = DetachedTimestampFile.deserialize(otsBytes);
        return detached;
    }
    /**
     * Verifica un .ots contra el archivo original.
     * @param otsPath Path al .ots (debe existir)
     * @param originalPath Path al archivo original (debe existir)
     * @return true si la verificación fue satisfactoria (hay al menos una attestation verificable)
     * @throws Exception en errores IO o de la librería
     */
    public static boolean verifyOts(Path otsPath, Path originalPath) throws Exception {
        System.out.println("-> verifyOts | OTS: " + otsPath.toAbsolutePath() + " | Original: " + originalPath.toAbsolutePath());

        if (!Files.exists(otsPath)) {
            throw new IllegalArgumentException("El archivo .ots no existe: " + otsPath.toAbsolutePath());
        }
        if (!Files.exists(originalPath)) {
            throw new IllegalArgumentException("El archivo original no existe: " + originalPath.toAbsolutePath());
        }

        // Reconstruir detached desde .ots y desde el archivo original
        DetachedTimestampFile detachedOts = readDetachedFromOts(otsPath);
        DetachedTimestampFile detachedOrig = DetachedTimestampFile.from(new OpSHA256(), new File(originalPath.toString()));

        // Llamada a verify
        System.out.println("Llamando OpenTimestamps.verify(...)");
        @SuppressWarnings("unchecked")
        Map<?, ?> verifyResults = (Map<?, ?>) OpenTimestamps.verify(detachedOts, detachedOrig);

        // Mostrar info legible (opcional)
        try {
            String info = OpenTimestamps.info(detachedOts);
            System.out.println("OpenTimestamps.info() (verificación):");
            System.out.println(info);
        } catch (Exception e) {
            System.err.println("No se pudo obtener info legible: " + e.getMessage());
        }

        boolean success = (verifyResults != null && !verifyResults.isEmpty());
        if (success) {
            System.out.println("✅ Verificación EXITOSA. Resultados:");
            verifyResults.forEach((k, v) -> System.out.println(" - " + k + " -> " + v));
        } else {
            System.out.println("Verificación PENDIENTE o sin attestations verificables (map vacío o null).");
        }

        return success;
    }
    // ---- FUNCIONA ---- FUNCIONA ----- FUNCIONA ---- FUNCIONA ---- FUNCIONA ----

    // -------------- UPGRADE + VERIFY. Retorna TRUE en caso de poder upgradear y verificar el archivo--------------
    /**
     *    Ahora upgradeAndVerify delega en upgradeOts(...) y verifyOts(...).
     *    Comportamiento: intenta upgradear; si no hay cambio -> no verifica y retorna false.
     *    Si hubo cambio -> llama verifyOts(...) y retorna su resultado.
     *    NOTA: Si se tiene un OTS que fue UPGRADEADO PREVIAMENTE, este metodo FALLA
     */
    public static boolean upgradeAndVerify(Path otsPath, Path originalPath) throws Exception {
        System.out.println("-> upgradeAndVerify | OTS: " + otsPath.toAbsolutePath() + " | Original: " + originalPath.toAbsolutePath());

        // 1) Intentar upgrade (in-place)
        boolean changed = upgradeOts(otsPath);

        if (!changed) {
            System.out.println("upgradeAndVerify: no se realizó upgrade en este intento o ya fue upgradeado anteriormente. No se procede a verificar.");
            return false;
        }

        // 2) Si hubo upgrade, verificar usando el método dedicado
        boolean verified = verifyOts(otsPath, originalPath);
        if (verified) {
            System.out.println("upgradeAndVerify: verificación exitosa.");
        } else {
            System.out.println("upgradeAndVerify: upgrade realizado pero verificación pendiente.");
        }
        return verified;
    }
    // ---- FUNCIONA ---- FUNCIONA ----- FUNCIONA ---- FUNCIONA ---- FUNCIONA ----


    // -------------- UPGRADE + VERIFY CON REINTENTOS CADA 10 MIN HASTA 1 HORA MAXIMO (6 INTENTOS) --------------
    /**
     * Ejecuta upgrade cada 10 minutos hasta un máximo de 6 intentos (bloqueante).
     * Reutiliza upgradeOts(...) y verifyOts(...).
     *
     * Retorna:
     *  - true  -> si en algún intento se upgradearon las attestations y la verificación fue exitosa.
     *  - false -> si tras maxAttempts no se logró verificar.
     *
     * Lanza InterruptedException si el hilo fue interrumpido durante la espera.
     */
    public static boolean upgradeWithRetries(Path otsPath, Path originalPath) throws InterruptedException {
        final int maxAttempts = 6;
        final long waitMillis = TimeUnit.MINUTES.toMillis(10);

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            System.out.println("\n=== Intento " + attempt + " / " + maxAttempts + " ===");

            boolean upgraded = false;
            try {
                // Reutiliza el método que implementa el upgrade in-place
                upgraded = upgradeOts(otsPath);
            } catch (Exception e) {
                // Logueamos y seguimos intentando (no abortamos)
                System.err.println("Excepción al intentar upgrade en intento " + attempt + ": " + e.getMessage());
                e.printStackTrace();
                upgraded = false;
            }

            if (upgraded) {
                System.out.println("Se detectó upgrade en intento " + attempt + ". Procediendo a verificar...");
                boolean verified = false;
                try {
                    // Reutiliza el método dedicado a la verificación
                    verified = verifyOts(otsPath, originalPath);
                } catch (Exception e) {
                    System.err.println("Excepción durante verify en intento " + attempt + ": " + e.getMessage());
                    e.printStackTrace();
                    verified = false;
                }

                if (verified) {
                    System.out.println("🎉 Verificación satisfactoria después de upgrade en intento " + attempt + ". Finalizando.");
                    return true;
                } else {
                    System.out.println("Upgrade realizado pero verificación todavía pendiente. Continuando con los siguientes intentos...");
                }
            } else {
                System.out.println("No hubo upgrade en el intento " + attempt + ". Se intentará nuevamente si quedan intentos.");
            }

            // esperar antes del siguiente intento si no fue el último
            if (attempt < maxAttempts) {
                System.out.println("Esperando 10 minutos antes del siguiente intento...");
                try {
                    Thread.sleep(waitMillis);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    System.err.println("Interrumpido durante la espera entre intentos.");
                    throw ie; // relanzamos para que el llamador pueda reaccionar
                }
            }
        }

        // Si llegamos acá, todos los intentos fallaron en verificar => devolvemos false (no lanzar excepción)
        System.err.println("⚠️ Tras " + maxAttempts + " intentos no se logró verificar el timestamp.");
        return false;
    }
    // ----
}


/**
 * METODO STAMPFILE ANTIGUO (recibe string como parametro)
 */
/*
    public static void stampFile(String filePath) throws Exception {
        File file = new File(filePath);

        if (!file.exists()) {
            System.err.println("❌ El archivo no existe: " + file.getAbsolutePath());
            return;
        }

        // Crear detached y hacer stamp
        DetachedTimestampFile detached = DetachedTimestampFile.from(new OpSHA256(), file);
        OpenTimestamps.stamp(detached);

        // Info legible
        String infoResult = OpenTimestamps.info(detached);
        System.out.println("📌 Resultado de OTS (info):");
        System.out.println(infoResult);

        // --------------------------------------------------
        // Construir ruta de salida: mismo directorio, nombre + .ots
        // --------------------------------------------------
        Path inputPath = Paths.get(filePath).toAbsolutePath();
        Path parent = inputPath.getParent();
        String baseName = inputPath.getFileName().toString();
        Path outputPath = (parent != null)
                ? parent.resolve(baseName + ".ots")
                : Paths.get(baseName + ".ots"); // caso sin parent (p. ej. ruta relativa simple)

        System.out.println("Voy a intentar guardar el .ots en: " + outputPath);

        // Serializar UNA vez y reutilizar
        byte[] otsBytes = detached.serialize();
        if (otsBytes == null) {
            System.err.println("ERROR: detached.serialize() devolvió null. No se puede guardar.");
            return;
        }
        System.out.println("Tamaño del .ots serializado: " + otsBytes.length + " bytes");

        // Asegurar directorio padre
        try {
            if (outputPath.getParent() != null && !Files.exists(outputPath.getParent())) {
                Files.createDirectories(outputPath.getParent());
                System.out.println("Se creó el directorio padre: " + outputPath.getParent());
            }

            // Escribir el archivo (create/overwrite)
            Files.write(outputPath, otsBytes,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE);

            // Forzar flush a disco (fsync)
            try (FileChannel fc = FileChannel.open(outputPath, StandardOpenOption.WRITE)) {
                fc.force(true);
            } catch (IOException e) {
                System.err.println("Aviso: no se pudo forzar fsync: " + e.getMessage());
            }

            // Comprobación final: existe y leer tamaño
            if (Files.exists(outputPath)) {
                System.out.println("Se guardó el archivo .ots en: " + outputPath);
                System.out.println("Tamaño en disco: " + Files.size(outputPath) + " bytes");
                byte[] readBack = Files.readAllBytes(outputPath);
                System.out.println("Lectura de comprobación: bytes leídos = " + readBack.length);
            } else {
                System.err.println("Después de Files.write el archivo NO existe (muy extraño).");
            }

        } catch (IOException ioe) {
            System.err.println("IOException al guardar .ots: " + ioe.getMessage());
            ioe.printStackTrace();
        }
    }


 */