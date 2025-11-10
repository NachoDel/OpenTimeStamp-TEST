package trusthub.ots.opentimestamp_poc.controllers;

import java.nio.file.Path;
import java.util.Map;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import trusthub.ots.opentimestamp_poc.dto.UpgradeResult;
import trusthub.ots.opentimestamp_poc.dto.VerifyResponse;
import trusthub.ots.opentimestamp_poc.service.OpenTimestampsService;

@RestController
@RequestMapping("/api/ots")
public class OtsController {

    private final OpenTimestampsService otsService;

    public OtsController(OpenTimestampsService otsService) {
        this.otsService = otsService;
    }

    /**
     * STAMP: recibe PDF (campo 'file') -> devuelve .ots (binary/attachment) o JSON error.
     */
   @PostMapping("/stamp")
    public ResponseEntity<?> stamp(@RequestParam("file") MultipartFile file) {
        try {
            byte[] otsBytes = otsService.stamp(file);

            // Obtener nombre original y construir nombre de salida: <OriginalFilename>.ots
            String original = file.getOriginalFilename(); // puede ser null
            String safeOriginal = (original != null && !original.isBlank())
                    ? Path.of(original).getFileName().toString()
                    : "result";
            String outputFilename = safeOriginal + ".ots";

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
            headers.set(HttpHeaders.CONTENT_DISPOSITION,
                    ContentDisposition.attachment().filename(outputFilename).build().toString());

            return ResponseEntity.ok()
                    .headers(headers)
                    .body(otsBytes);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("{\"status\":\"FAIL\",\"error\":\"" + e.getMessage() + "\"}");
        }
    }

    /**
     * UPGRADE: recibe .ots (campo 'ots') -> intenta upgrade y devuelve .ots actualizado (binary) o JSON error.
     */
    @PostMapping("/upgrade")
    public ResponseEntity<?> upgrade(@RequestParam("ots") MultipartFile ots) {
        try {
            UpgradeResult result = otsService.upgrade(ots);

            if (result.isUpgraded()) {
                byte[] newOts = result.getOtsBytes();

                // Construir nombre de salida
                String original = ots.getOriginalFilename(); // e.g. "test2.pdf.ots"
                String safeOriginal = (original != null && !original.isBlank())
                        ? Path.of(original).getFileName().toString()
                        : "upgraded";
                String baseName;
                if (safeOriginal.toLowerCase().endsWith(".ots")) {
                    baseName = safeOriginal.substring(0, safeOriginal.length() - 4);
                } else {
                    int dot = safeOriginal.lastIndexOf('.');
                    baseName = (dot > 0) ? safeOriginal.substring(0, dot) : safeOriginal;
                }
                String outputFilename = baseName + "-Upgraded.ots";

                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
                headers.set(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(outputFilename).build().toString());

                return ResponseEntity.ok()
                        .headers(headers)
                        .body(newOts);
            } else {
                // No hubo upgrade -> devolvemos JSON con mensaje amigable en español
                Map<String, String> payload = Map.of(
                    "status", "NO_UPGRADE",
                    "message", "El archivo .ots todavía no recibió ningún upgrade"
                );
                return ResponseEntity.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(payload);
            }

        } catch (Exception e) {
            e.printStackTrace();
            Map<String, String> err = Map.of("status", "FAIL", "error", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(err);
        }
    }

    /**
     * INFO: recibe .ots (campo 'ots') -> devuelve String con OpenTimestamps.info(...) o JSON error.
     */
    @PostMapping("/info")
    public ResponseEntity<?> info(@RequestParam("ots") MultipartFile ots) {
        try {
            String info = otsService.info(ots);
            // devolvemos texto plano para que la UI lo pegue en el pre
            return ResponseEntity.ok().contentType(MediaType.TEXT_PLAIN).body(info);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("{\"status\":\"FAIL\",\"error\":\"" + e.getMessage() + "\"}");
        }
    }

    /**
     * VERIFY: ya deberías tenerlo funcionando. Aquí lo dejamos por consistencia.
     * Espera 'ots' y 'file' como multipart.
     */
    @PostMapping("/verify")
    public ResponseEntity<?> verify(@RequestParam("ots") MultipartFile ots,
                                    @RequestParam("file") MultipartFile file) {
        try {
            // si tu servicio devuelve VerifyResponse DTO:
            VerifyResponse resp = otsService.verifyAndGetMetadata(ots, file);
            return ResponseEntity.ok(resp);
        } catch (NoSuchMethodError nsme) {
            // fallback si tu servicio tiene otro método verify(...) — intenta usarlo
            try {
                boolean ok = otsService.verify(ots, file); // si tienes este método boolean
                if (ok) return ResponseEntity.ok("{\"status\":\"OK\"}");
                else return ResponseEntity.ok("{\"status\":\"FAIL\"}");
            } catch (Exception ex) {
                ex.printStackTrace();
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body("{\"status\":\"FAIL\",\"error\":\"" + ex.getMessage() + "\"}");
            }
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("{\"status\":\"FAIL\",\"error\":\"" + e.getMessage() + "\"}");
        }
    }
}
