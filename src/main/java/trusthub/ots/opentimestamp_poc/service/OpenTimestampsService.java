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

@Service
public class OpenTimestampsService {

    private static final Logger logger = LoggerFactory.getLogger(OpenTimestampsService.class);

    // -------------------- STAMP --------------------
    /** Crea un detached timestamp (.ots) a partir de un PDF y devuelve los bytes del .ots */
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
    /** Toma un archivo .ots (subido) e intenta hacer upgrade.
     *  Devuelve UpgradeResult: upgraded==true y otsBytes==bytes nuevos,
     *  o upgraded==false y otsBytes==null si no hubo cambio.
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
    /** Devuelve la salida legible de OpenTimestamps.info(detached) para el .ots dado. */
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

    // -------------------- VERIFY (boolean simple) --------------------
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
            @SuppressWarnings("unchecked")
            Map<?, ?> verifyResults = (Map<?, ?>) OpenTimestamps.verify(detachedOts, detachedOrig);

            boolean success = (verifyResults != null && !verifyResults.isEmpty());
            logger.info("Verify simple: success = {}, verifyResults-size = {}", success, verifyResults != null ? verifyResults.size() : 0);
            return success;
        } finally {
            try { tmpPdf.delete(); } catch (Exception ignored) {}
        }
    }

    // -------------------- verifyAndGetMetadata (enriquecido) --------------------
    public VerifyResponse verifyAndGetMetadata(MultipartFile otsFile, MultipartFile originalPdf) throws Exception {
        // 1. reconstruct detached objects
        DetachedTimestampFile detachedOts = DetachedTimestampFile.deserialize(otsFile.getBytes());
        File tmpPdf = toTempFile(originalPdf, ".pdf");
        String info = null;
        Map<?, ?> verifyResults = null;
        try {
            DetachedTimestampFile detachedOrig = DetachedTimestampFile.from(new OpSHA256(), tmpPdf);
            @SuppressWarnings("unchecked")
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
                    blockHeight = Long.parseLong(found);
                }
            }
            Optional<Long> maybeHeight = extractBlockHeightFromText(info);
            if (maybeHeight.isPresent()) blockHeight = maybeHeight.get();
            if (blockHeight == null) {
                Pattern attestation = Pattern.compile("BitcoinBlockHeaderAttestation\\((\\d+)\\)");
                Matcher ma = attestation.matcher(info);
                if (ma.find()) {
                    blockHeight = Long.parseLong(ma.group(1));
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
                        blockHeight = Long.parseLong(found);
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
                return Optional.of(Long.parseLong(mh.group(1)));
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
        URL url = new URL(urlStr);
        HttpURLConnection con = (HttpURLConnection) url.openConnection();
        con.setRequestMethod("GET");
        con.setConnectTimeout(8000);
        con.setReadTimeout(8000);
        int status = con.getResponseCode();
        BufferedReader in = new BufferedReader(new InputStreamReader(
                (status >= 200 && status < 300) ? con.getInputStream() : con.getErrorStream()
        ));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = in.readLine()) != null) sb.append(line);
        in.close();
        con.disconnect();
        return sb.toString();
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
