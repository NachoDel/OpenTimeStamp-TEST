package trusthub.ots.opentimestamp_poc.service;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.eternitywall.ots.DetachedTimestampFile;
import com.eternitywall.ots.OpenTimestamps;
import com.eternitywall.ots.op.OpSHA256;

import trusthub.ots.opentimestamp_poc.dto.UpgradeResult;
import trusthub.ots.opentimestamp_poc.dto.VerifyResponse;

/**
 * Servicio para operaciones de OpenTimestamps.
 * <p>
 * Proporciona funcionalidad para:
 * <ul>
 *   <li>Stamping: generar timestamps (.ots) de archivos</li>
 *   <li>Upgrade: actualizar timestamps con attestations de Bitcoin</li>
 *   <li>Verify: verificar timestamps contra archivos originales</li>
 *   <li>Info: extraer metadatos de archivos .ots</li>
 * </ul>
 * </p>
 *
 * @author Ignacio Delamer. TrustHub Team
 * @version 1.0
 * @since 2025-08-18
 */
@Service
public class OpenTimestampsService {

    private static final Logger logger = LoggerFactory.getLogger(OpenTimestampsService.class);

    // -------------------- STAMP --------------------
        /**
     * Genera un timestamp detached (.ots) para un archivo PDF.
     * <p>
     * El timestamp se envía a calendarios públicos de OpenTimestamps para
     * su posterior inclusión en la blockchain de Bitcoin.
     * </p>
     *
     * @param pdf archivo PDF a timestampear (no puede ser null o vacío)
     * @return bytes del archivo .ots generado
     * @throws IllegalArgumentException si el PDF es null o vacío
     * @throws Exception si ocurre un error durante el stamping o comunicación con calendarios
     * @see DetachedTimestampFile
     */
    public byte[] stamp(MultipartFile pdf) throws Exception {
        if (pdf == null || pdf.isEmpty()) {
            throw new IllegalArgumentException("El archivo PDF no puede ser nulo/vacío");
        }

        File tmpPdf = toTempFile(pdf, ".pdf");
        try {
            // crear detached y stamp
            DetachedTimestampFile detached = DetachedTimestampFile.from(new OpSHA256(), tmpPdf);
            OpenTimestamps.stamp(detached); // envía a calendarios remotos (inicia la atestación)
            byte[] otsBytes = detached.serialize();
            logger.info("Stamp generado: {} bytes", otsBytes != null ? otsBytes.length : 0);
            return otsBytes;
        } finally {
            try { tmpPdf.delete(); } catch (Exception ignored) {}
        }
    }

    // -------------------- UPGRADE --------------------
    /**
     * Intenta actualizar un archivo .ots con attestations de Bitcoin.
     * <p>
     * Consulta calendarios públicos para obtener pruebas criptográficas
     * de inclusión en bloques de Bitcoin. Si el timestamp aún no fue
     * incluido en un bloque, devuelve {@code upgraded=false}.
     * </p>
     *
     * @param otsFile archivo .ots a actualizar (no puede ser null o vacío)
     * @return {@link UpgradeResult} con estado y bytes actualizados (si aplica)
     * @throws IllegalArgumentException si otsFile es null o vacío
     * @throws Exception si ocurre error al deserializar o comunicarse con calendarios
     */
    public UpgradeResult upgrade(MultipartFile otsFile) throws Exception {
        if (otsFile == null || otsFile.isEmpty()) {
            throw new IllegalArgumentException("El archivo .ots no puede ser nulo/vacío");
        }

        byte[] otsBytes = otsFile.getBytes();
        DetachedTimestampFile detached = DetachedTimestampFile.deserialize(otsBytes);

        boolean changed = OpenTimestamps.upgrade(detached); // intenta descargar attestations

        if (changed) {
            byte[] newBytes = detached.serialize();
            logger.info("Upgrade ejecutado. Cambios: true. Tamaño .ots ahora: {}", newBytes != null ? newBytes.length : 0);
            return new UpgradeResult(true, newBytes);
        } else {
            logger.info("Upgrade ejecutado. No se encontraron nuevas attestations (changed=false).");
            return new UpgradeResult(false, null);
        }
    }

    // -------------------- INFO --------------------
    /**
     * Extrae información legible de un archivo .ots.
     *
     * @param otsFile archivo .ots (no puede ser null o vacío)
     * @return String con información del timestamp (operaciones, attestations, etc.)
     * @throws IllegalArgumentException si otsFile es null o vacío
     * @throws Exception si ocurre error al deserializar
     */
    public String info(MultipartFile otsFile) throws Exception {
        if (otsFile == null || otsFile.isEmpty()) {
            throw new IllegalArgumentException("El archivo .ots no puede ser nulo/vacío");
        }
        byte[] otsBytes = otsFile.getBytes();
        DetachedTimestampFile detached = DetachedTimestampFile.deserialize(otsBytes);
        String info = OpenTimestamps.info(detached);
        logger.info("Info extraída del .ots (longitud {}):\n{}", info != null ? info.length() : 0, info);
        return info;
    }

    // -------------------- VERIFY (boolean simple, no utilizado en la aplicacion final, se puede eliminar) --------------------
    /** Verifica un .ots contra el PDF original. Devuelve true si se verificó (hay atestaciones verificables). */
    public boolean verify(MultipartFile otsFile, MultipartFile originalPdf) throws Exception {
        if (otsFile == null || otsFile.isEmpty()) {
            throw new IllegalArgumentException("El archivo .ots no puede ser nulo/vacío");
        }
        if (originalPdf == null || originalPdf.isEmpty()) {
            throw new IllegalArgumentException("El archivo original no puede ser nulo/vacío");
        }

        DetachedTimestampFile detachedOts = DetachedTimestampFile.deserialize(otsFile.getBytes());
        File tmpPdf = toTempFile(originalPdf, ".pdf");
        try {
            DetachedTimestampFile detachedOrig = DetachedTimestampFile.from(new OpSHA256(), tmpPdf);
            Map<?, ?> verifyResults = (Map<?, ?>) OpenTimestamps.verify(detachedOts, detachedOrig);

            boolean success = (verifyResults != null && !verifyResults.isEmpty());
            logger.info("Verify simple: success = {}, verifyResults-size = {}", success, verifyResults != null ? verifyResults.size() : 0);
            return success;
        } finally {
            try { tmpPdf.delete(); } catch (Exception ignored) {}
        }
    }

    // -------------------- verifyAndGetMetadata (enriquecido) --------------------
        /**
     * Verifica un timestamp .ots contra el archivo PDF original y extrae metadatos.
     * <p>
     * Además de verificar la validez del timestamp, intenta extraer:
     * <ul>
     *   <li>Transaction ID (txid) de Bitcoin</li>
     *   <li>Block hash</li>
     *   <li>Block height</li>
     *   <li>Block time (timestamp Unix convertido a ISO 8601)</li>
     * </ul>
     * </p>
     *
     * @param otsFile archivo .ots (no puede ser null o vacío)
     * @param originalPdf archivo PDF original (no puede ser null o vacío)
     * @return {@link VerifyResponse} con estado y metadatos del bloque
     * @throws IllegalArgumentException si algún archivo es null o vacío
     * @throws Exception si ocurre error durante verificación
     */
    public VerifyResponse verifyAndGetMetadata(MultipartFile otsFile, MultipartFile originalPdf) throws Exception {
        // 1. reconstruct detached objects
        DetachedTimestampFile detachedOts = DetachedTimestampFile.deserialize(otsFile.getBytes());
        File tmpPdf = toTempFile(originalPdf, ".pdf");
        String info = null;
        Map<?, ?> verifyResults = null;
        try {
            DetachedTimestampFile detachedOrig = DetachedTimestampFile.from(new OpSHA256(), tmpPdf);
            Map<?, ?> vr = (Map<?, ?>) OpenTimestamps.verify(detachedOts, detachedOrig);
            verifyResults = vr;

            try {
                info = OpenTimestamps.info(detachedOts);
            } catch (Exception e) {
                // info may not be available, keep null
                info = null;
            }
        } finally {
            try { tmpPdf.delete(); } catch (Exception ignored) {}
        }

        boolean success = (verifyResults != null && !verifyResults.isEmpty());

        // 2. Try to extract txid/block_hash/height from info or raw verifyResults
        String txid = null;
        String blockHash = null;
        Long blockHeight = null;

        if (info != null) {
            Optional<String> maybe = extractTxOrBlockFromText(info);
            if (maybe.isPresent()) {
                String found = maybe.get();
                if (found.matches("^[a-fA-F0-9]{64}$")) {
                    txid = found;
                } else if (found.matches("^\\d+$")) {
                    blockHeight = Long.valueOf(found);
                }
            }
            Optional<Long> maybeHeight = extractBlockHeightFromText(info);
            if (maybeHeight.isPresent()) blockHeight = maybeHeight.get();
            if (blockHeight == null) {
                Pattern attestation = Pattern.compile("BitcoinBlockHeaderAttestation\\((\\d+)\\)");
                Matcher ma = attestation.matcher(info);
                if (ma.find()) {
                    blockHeight = Long.valueOf(ma.group(1));
                }
            }
        }

        if ((txid == null && blockHash == null && blockHeight == null) && verifyResults != null) {
            for (Object val : verifyResults.values()) {
                if (val == null) continue;
                String s = val.toString();
                Optional<String> maybe = extractTxOrBlockFromText(s);
                if (maybe.isPresent()) {
                    String found = maybe.get();
                    if (found.matches("^[a-fA-F0-9]{64}$")) {
                        txid = found;
                    } else if (found.matches("^\\d+$")) {
                        blockHeight = Long.valueOf(found);
                    }
                }
                if (blockHeight == null) {
                    Optional<Long> mh = extractBlockHeightFromText(s);
                    if (mh.isPresent()) blockHeight = mh.get();
                }
                if (txid != null || blockHeight != null) break;
            }
        }

        // 3. If we have a txid or blockHeight/blockHash, query block explorer to obtain block_time
        String blockTimeIso = null;
        if (txid != null) {
            Optional<String> bt = getBlockTimeFromTxid(txid);
            if (bt.isPresent()) blockTimeIso = bt.get();
            else {
                Optional<String> maybeBlockHash = getBlockHashFromTxid(txid);
                if (maybeBlockHash.isPresent()) blockHash = maybeBlockHash.get();
            }
        }
        if (blockHash == null && blockHeight != null) {
            Optional<String> bh = getBlockHashFromHeight(blockHeight);
            if (bh.isPresent()) blockHash = bh.get();
        }
        if (blockHash != null && blockTimeIso == null) {
            Optional<String> bt2 = getBlockTimeFromBlockHash(blockHash);
            if (bt2.isPresent()) blockTimeIso = bt2.get();
        }
        if (blockTimeIso == null && blockHeight != null) {
            Optional<String> bt3 = getBlockTimeFromHeight(blockHeight);
            if (bt3.isPresent()) blockTimeIso = bt3.get();
        }

        VerifyResponse resp = new VerifyResponse();
        resp.setStatus(success ? "OK" : "FAIL");
        resp.setInfo(info);
        resp.setTxid(txid);
        resp.setBlock_hash(blockHash);
        resp.setBlock_height(blockHeight);
        resp.setBlock_time(blockTimeIso);
        resp.setRawVerifyResults(verifyResults);

        return resp;
    }

    // -------------------- File-based wrappers. Métodos helper file-based (para controlador reactivo) --------------------
    // HELPER: stampFromFile(File) -> byte[]
    public byte[] stampFromFile(File pdfFile) throws Exception {
        if (pdfFile == null || !pdfFile.exists()) throw new IllegalArgumentException("pdf file is null or does not exist");
        DetachedTimestampFile detached = DetachedTimestampFile.from(new OpSHA256(), pdfFile);
        OpenTimestamps.stamp(detached);
        return detached.serialize();
    }

    // HELPER: upgradeFromFile(File) -> UpgradeResult
    public UpgradeResult upgradeFromFile(File otsFile) throws Exception {
        if (otsFile == null || !otsFile.exists()) throw new IllegalArgumentException("ots file is null or does not exist");
        byte[] bytes = Files.readAllBytes(otsFile.toPath());
        DetachedTimestampFile detached = DetachedTimestampFile.deserialize(bytes);
        boolean changed = OpenTimestamps.upgrade(detached);
        if (changed) {
            byte[] newBytes = detached.serialize();
            return new UpgradeResult(true, newBytes);
        } else {
            return new UpgradeResult(false, null);
        }
    }

    // HELPER: infoFromFile(File) -> String
    public String infoFromFile(File otsFile) throws Exception {
        if (otsFile == null || !otsFile.exists()) throw new IllegalArgumentException("ots file is null or does not exist");
        byte[] bytes = Files.readAllBytes(otsFile.toPath());
        DetachedTimestampFile detached = DetachedTimestampFile.deserialize(bytes);
        return OpenTimestamps.info(detached);
    }

    // HELPER: verifyAndGetMetadataFromFiles(File otsFile, File originalPdf) -> VerifyResponse
    public VerifyResponse verifyAndGetMetadataFromFiles(File otsFile, File originalPdf) throws Exception {
        if (otsFile == null || !otsFile.exists()) throw new IllegalArgumentException("ots file is null or does not exist");
        if (originalPdf == null || !originalPdf.exists()) throw new IllegalArgumentException("original pdf is null or does not exist");
        byte[] otsBytes = Files.readAllBytes(otsFile.toPath());
        DetachedTimestampFile detachedOts = DetachedTimestampFile.deserialize(otsBytes);
        // reuse logic from verifyAndGetMetadata but operating on File for original
        File tmpPdf = originalPdf;
        String info = null;
        Map<?, ?> verifyResults = null;
        try {
            DetachedTimestampFile detachedOrig = DetachedTimestampFile.from(new OpSHA256(), tmpPdf);
            Map<?, ?> vr = (Map<?, ?>) OpenTimestamps.verify(detachedOts, detachedOrig);
            verifyResults = vr;
            try {
                info = OpenTimestamps.info(detachedOts);
            } catch (Exception e) {
                info = null;
            }
        } finally {
            // do not delete provided files; caller manages temp files
        }

        boolean success = (verifyResults != null && !verifyResults.isEmpty());

        String txid = null;
        String blockHash = null;
        Long blockHeight = null;

        if (info != null) {
            Optional<String> maybe = extractTxOrBlockFromText(info);
            if (maybe.isPresent()) {
                String found = maybe.get();
                if (found.matches("^[a-fA-F0-9]{64}$")) {
                    txid = found;
                } else if (found.matches("^\\d+$")) {
                    blockHeight = Long.valueOf(found);
                }
            }
            Optional<Long> maybeHeight = extractBlockHeightFromText(info);
            if (maybeHeight.isPresent()) blockHeight = maybeHeight.get();
            if (blockHeight == null) {
                Pattern attestation = Pattern.compile("BitcoinBlockHeaderAttestation\\((\\d+)\\)");
                Matcher ma = attestation.matcher(info);
                if (ma.find()) {
                    blockHeight = Long.valueOf(ma.group(1));
                }
            }
        }

        if ((txid == null && blockHash == null && blockHeight == null) && verifyResults != null) {
            for (Object val : verifyResults.values()) {
                if (val == null) continue;
                String s = val.toString();
                Optional<String> maybe = extractTxOrBlockFromText(s);
                if (maybe.isPresent()) {
                    String found = maybe.get();
                    if (found.matches("^[a-fA-F0-9]{64}$")) {
                        txid = found;
                    } else if (found.matches("^\\d+$")) {
                        blockHeight = Long.valueOf(found);
                    }
                }
                if (blockHeight == null) {
                    Optional<Long> mh = extractBlockHeightFromText(s);
                    if (mh.isPresent()) blockHeight = mh.get();
                }
                if (txid != null || blockHeight != null) break;
            }
        }

        String blockTimeIso = null;
        if (txid != null) {
            Optional<String> bt = getBlockTimeFromTxid(txid);
            if (bt.isPresent()) blockTimeIso = bt.get();
            else {
                Optional<String> maybeBlockHash = getBlockHashFromTxid(txid);
                if (maybeBlockHash.isPresent()) blockHash = maybeBlockHash.get();
            }
        }
        if (blockHash == null && blockHeight != null) {
            Optional<String> bh = getBlockHashFromHeight(blockHeight);
            if (bh.isPresent()) blockHash = bh.get();
        }
        if (blockHash != null && blockTimeIso == null) {
            Optional<String> bt2 = getBlockTimeFromBlockHash(blockHash);
            if (bt2.isPresent()) blockTimeIso = bt2.get();
        }
        if (blockTimeIso == null && blockHeight != null) {
            Optional<String> bt3 = getBlockTimeFromHeight(blockHeight);
            if (bt3.isPresent()) blockTimeIso = bt3.get();
        }

        VerifyResponse resp = new VerifyResponse();
        resp.setStatus(success ? "OK" : "FAIL");
        resp.setInfo(info);
        resp.setTxid(txid);
        resp.setBlock_hash(blockHash);
        resp.setBlock_height(blockHeight);
        resp.setBlock_time(blockTimeIso);
        resp.setRawVerifyResults(verifyResults);

        return resp;
    }

    // ----------------- Helpers: extraction from text -----------------

    /** Tries to find a 64-hex string or a numeric block height inside free text. */
    private Optional<String> extractTxOrBlockFromText(String text) {
        if (text == null) return Optional.empty();
        Pattern pHex = Pattern.compile("\\b([a-fA-F0-9]{64})\\b");
        Matcher mHex = pHex.matcher(text);
        if (mHex.find()) return Optional.of(mHex.group(1));
        Pattern pHeight = Pattern.compile("\\b(?:block(?:\\s*#?)?|height[:\\s])\\s*(\\d{2,10})\\b", Pattern.CASE_INSENSITIVE);
        Matcher mh = pHeight.matcher(text);
        if (mh.find()) return Optional.of(mh.group(1));
        return Optional.empty();
    }

    private Optional<Long> extractBlockHeightFromText(String text) {
        if (text == null) return Optional.empty();
        Pattern pHeight = Pattern.compile("\\b(?:block(?:\\s*#?)?|height[:\\s])\\s*(\\d{2,10})\\b", Pattern.CASE_INSENSITIVE);
        Matcher mh = pHeight.matcher(text);
        if (mh.find()) {
            try {
                return Optional.of(Long.valueOf(mh.group(1)));
            } catch (NumberFormatException ignored) {}
        }
        return Optional.empty();
    }

    // ----------------- Blockstream / Esplora queries -----------------
    // Note: public Blockstream API endpoints. Rate-limits may apply.

    private Optional<String> getBlockTimeFromTxid(String txid) {
        try {
            String body = httpGet("https://blockstream.info/api/tx/" + txid + "/status");
            Pattern p = Pattern.compile("\"block_time\"\\s*:\\s*(\\d+)");
            Matcher m = p.matcher(body);
            if (m.find()) {
                long epoch = Long.parseLong(m.group(1));
                String iso = Instant.ofEpochSecond(epoch).toString();
                return Optional.of(iso);
            }
            Pattern pb = Pattern.compile("\"block_hash\"\\s*:\\s*\"([a-fA-F0-9]{64})\"");
            Matcher mb = pb.matcher(body);
            if (mb.find()) {
                String bh = mb.group(1);
                Optional<String> bt = getBlockTimeFromBlockHash(bh);
                if (bt.isPresent()) return bt;
            }
        } catch (Exception e) {
            // ignore and return empty
        }
        return Optional.empty();
    }

    private Optional<String> getBlockHashFromTxid(String txid) {
        try {
            String body = httpGet("https://blockstream.info/api/tx/" + txid + "/status");
            Pattern pb = Pattern.compile("\"block_hash\"\\s*:\\s*\"([a-fA-F0-9]{64})\"");
            Matcher mb = pb.matcher(body);
            if (mb.find()) return Optional.of(mb.group(1));
        } catch (Exception e) {}
        return Optional.empty();
    }

    private Optional<String> getBlockTimeFromBlockHash(String blockHash) {
        try {
            String body = httpGet("https://blockstream.info/api/block/" + blockHash);
            Pattern p = Pattern.compile("\"(timestamp|time)\"\\s*:\\s*(\\d+)");
            Matcher m = p.matcher(body);
            if (m.find()) {
                long epoch = Long.parseLong(m.group(2));
                return Optional.of(Instant.ofEpochSecond(epoch).toString());
            }
        } catch (Exception e) {}
        return Optional.empty();
    }

    private Optional<String> getBlockTimeFromHeight(long height) {
        try {
            String blockHash = httpGet("https://blockstream.info/api/block-height/" + height).trim();
            if (blockHash.length() > 0) {
                return getBlockTimeFromBlockHash(blockHash);
            }
        } catch (Exception e) {}
        return Optional.empty();
    }

    private Optional<String> getBlockHashFromHeight(long height) {
        try {
            String blockHash = httpGet("https://blockstream.info/api/block-height/" + height).trim();
            if (blockHash.length() > 0) return Optional.of(blockHash);
        } catch (Exception e) {}
        return Optional.empty();
    }

    // Basic HTTP GET helper
    private String httpGet(String urlStr) throws Exception {
        URL url = new java.net.URI(urlStr).toURL();
        HttpURLConnection con = (HttpURLConnection) url.openConnection();
        con.setRequestMethod("GET");
        con.setConnectTimeout(8000);
        con.setReadTimeout(8000);
        int status = con.getResponseCode();
        try (BufferedReader in = new BufferedReader(new InputStreamReader(
                (status >= 200 && status < 300) ? con.getInputStream() : con.getErrorStream()
        ))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = in.readLine()) != null) sb.append(line);
            con.disconnect();
            return sb.toString();
        }
    }

    // -------------- rest of service (toTempFile, stamp, upgrade, info...) assumed present --------------

    private File toTempFile(MultipartFile multipart, String suffix) throws IOException {
        // Crea archivo temporal, copia el contenido del MultipartFile y devuelve File
        Path tmp = Files.createTempFile("otssvc-", suffix);
        try (InputStream in = multipart.getInputStream()) {
            Files.copy(in, tmp, StandardCopyOption.REPLACE_EXISTING);
        }
        File f = tmp.toFile();
        f.deleteOnExit(); // intenta borrar al salir de la JVM
        return f;
    }

}
