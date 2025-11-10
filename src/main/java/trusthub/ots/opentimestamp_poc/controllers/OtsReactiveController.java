package trusthub.ots.opentimestamp_poc.controllers;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Map;

import org.springframework.core.io.FileSystemResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import trusthub.ots.opentimestamp_poc.dto.UpgradeResult;
import trusthub.ots.opentimestamp_poc.dto.VerifyResponse;
import trusthub.ots.opentimestamp_poc.service.OpenTimestampsService;

@RestController
@RequestMapping("/api/ots")
public class OtsReactiveController {

    private final OpenTimestampsService otsService;

    public OtsReactiveController(OpenTimestampsService otsService) {
        this.otsService = otsService;
    }

    /**
     * STAMP: recibe multipart 'file' (PDF) y devuelve attachment .ots
     */
    @PostMapping(value = "/stamp", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Mono<ResponseEntity<FileSystemResource>> stampReactive(@RequestPart("file") Mono<FilePart> filePartMono) {
        return filePartMono.flatMap(filePart -> {
            try {
                Path tmpPdf = Files.createTempFile("ots-stamp-", ".pdf");
                return filePart.transferTo(tmpPdf).then(
                        Mono.fromCallable(() -> {
                            byte[] otsBytes = otsService.stampFromFile(tmpPdf.toFile()); // método bloqueante en el service
                            Path out = Files.createTempFile("ots-result-", ".ots");
                            Files.write(out, otsBytes, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
                            FileSystemResource resource = new FileSystemResource(out.toFile());
                            String original = filePart.filename();
                            String outName = (original != null ? original : "result") + ".ots";
                            HttpHeaders headers = new HttpHeaders();
                            headers.setContentDisposition(ContentDisposition.attachment().filename(outName).build());
                            headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
                            return ResponseEntity.ok().headers(headers).body(resource);
                        }).subscribeOn(Schedulers.boundedElastic())
                );
            } catch (Exception e) {
                return Mono.error(e);
            }
        });
    }

    /**
     * UPGRADE: recibe multipart 'ots'. Si hubo upgrade -> devuelve attachment,
     * si NO -> devuelve JSON {status: "NO_UPGRADE", message: "..."}
     */
    @PostMapping(value = "/upgrade", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Mono<ResponseEntity<?>> upgradeReactive(@RequestPart("ots") Mono<FilePart> otsMono) {
        return otsMono.flatMap(fp -> {
            try {
                Path tmp = Files.createTempFile("ots-in-", ".ots");
                return fp.transferTo(tmp).then(
                        Mono.fromCallable(() -> {
                            UpgradeResult res = otsService.upgradeFromFile(tmp.toFile()); // método bloqueante en el service
                            if (res.isUpgraded()) {
                                Path out = Files.createTempFile("ots-upgraded-", ".ots");
                                Files.write(out, res.getOtsBytes(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
                                FileSystemResource resource = new FileSystemResource(out.toFile());
                                String original = fp.filename();
                                String base = (original != null) ? original.replaceAll("(?i)\\.ots$", "") : "upgraded";
                                String outName = base + "-Upgraded.ots";
                                HttpHeaders headers = new HttpHeaders();
                                headers.setContentDisposition(ContentDisposition.attachment().filename(outName).build());
                                headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
                                return ResponseEntity.ok().headers(headers).body(resource);
                            } else {
                                Map<String, String> body = Map.of("status", "NO_UPGRADE", "message", "El archivo .ots todavía no recibió ningún upgrade");
                                return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(body);
                            }
                        }).subscribeOn(Schedulers.boundedElastic())
                );
            } catch (Exception e) {
                return Mono.error(e);
            }
        });
    }

    /**
     * INFO: recibe multipart 'ots' -> devuelve texto plano (OpenTimestamps.info)
     */
    @PostMapping(value = "/info", consumes = MediaType.MULTIPART_FORM_DATA_VALUE, produces = MediaType.TEXT_PLAIN_VALUE)
    public Mono<ResponseEntity<String>> infoReactive(@RequestPart("ots") Mono<FilePart> otsMono) {
        return otsMono.flatMap(fp -> {
            try {
                Path tmp = Files.createTempFile("ots-info-", ".ots");
                return fp.transferTo(tmp).then(
                        Mono.fromCallable(() -> {
                            String info = otsService.infoFromFile(tmp.toFile()); // método bloqueante en el service
                            return ResponseEntity.ok().contentType(MediaType.TEXT_PLAIN).body(info);
                        }).subscribeOn(Schedulers.boundedElastic())
                );
            } catch (Exception e) {
                return Mono.error(e);
            }
        });
    }

    /**
     * VERIFY: recibe multipart 'ots' y 'file' (original PDF) -> devuelve VerifyResponse JSON
     */
    @PostMapping(value = "/verify", consumes = MediaType.MULTIPART_FORM_DATA_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<VerifyResponse>> verifyReactive(@RequestPart("ots") Mono<FilePart> otsMono,
                                                               @RequestPart("file") Mono<FilePart> fileMono) {
        return Mono.zip(otsMono, fileMono)
                .flatMap(tuple -> {
                    FilePart otsPart = tuple.getT1();
                    FilePart pdfPart = tuple.getT2();
                    try {
                        Path otsTmp = Files.createTempFile("ots-verify-in-", ".ots");
                        Path pdfTmp = Files.createTempFile("orig-verify-in-", ".pdf");
                        return Mono.when(otsPart.transferTo(otsTmp), pdfPart.transferTo(pdfTmp))
                                .then(Mono.fromCallable(() -> {
                                    VerifyResponse vr = otsService.verifyAndGetMetadataFromFiles(otsTmp.toFile(), pdfTmp.toFile()); // método bloqueante en el service
                                    return ResponseEntity.ok().body(vr);
                                }).subscribeOn(Schedulers.boundedElastic()));
                    } catch (Exception e) {
                        return Mono.error(e);
                    }
                });
    }
}
