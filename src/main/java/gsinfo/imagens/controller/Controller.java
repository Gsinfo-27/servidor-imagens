package gsinfo.imagens.controller;

import gsinfo.imagens.service.FileService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

@RestController
@RequestMapping("/file")
public class Controller {

    private static final Logger logger = LoggerFactory.getLogger(Controller.class);

    @Autowired
    private FileService fileService;

    @PostMapping
    public ResponseEntity<?> create(@RequestParam("file") MultipartFile file) {
        try {
            logger.info("📥 Recebendo upload: {}", file.getOriginalFilename());
            String url = fileService.createFile(file);

            return ResponseEntity.ok(Map.of(
                    "url", url,
                    "message", "Arquivo enviado com sucesso para o bucket img",
                    "filename", fileService.extractFileNameFromUrl(url),
                    "size", file.getSize()
            ));
        } catch (IOException e) {
            logger.error("❌ Erro no upload: {}", e.getMessage(), e);
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(
                            "error", e.getMessage(),
                            "timestamp", System.currentTimeMillis()
                    ));
        }
    }

    @GetMapping("/ler")
    public ResponseEntity<?> ler(@RequestParam("file") String fileName) {
        try {
            byte[] data = fileService.readFile(fileName);

            // Tenta determinar o content-type
            String contentType = "application/octet-stream";
            if (fileName.toLowerCase().endsWith(".jpg") || fileName.toLowerCase().endsWith(".jpeg")) {
                contentType = "image/jpeg";
            } else if (fileName.toLowerCase().endsWith(".png")) {
                contentType = "image/png";
            } else if (fileName.toLowerCase().endsWith(".gif")) {
                contentType = "image/gif";
            }

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + fileName + "\"")
                    .contentType(MediaType.parseMediaType(contentType))
                    .body(data);
        } catch (IOException e) {
            logger.error("❌ Erro ao ler arquivo: {}", e.getMessage());
            return ResponseEntity.notFound().build();
        }
    }

    @DeleteMapping
    public ResponseEntity<?> delete(@RequestParam("file") String fileName) {
        String result = fileService.removeFile(fileName);
        if (result.startsWith("Arquivo eliminado")) {
            return ResponseEntity.ok(Map.of(
                    "message", result,
                    "filename", fileName
            ));
        }
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of(
                        "error", result,
                        "filename", fileName
                ));
    }

    @GetMapping("/exists")
    public ResponseEntity<?> exists(@RequestParam("file") String fileName) {
        boolean exists = fileService.fileExists(fileName);
        return ResponseEntity.ok(Map.of(
                "exists", exists,
                "filename", fileName
        ));
    }

    @GetMapping("/ping")
    public ResponseEntity<?>ping(){
        return ResponseEntity.ok("pong");
    }
}