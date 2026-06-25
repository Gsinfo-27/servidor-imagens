package gsinfo.imagens.controller;

import gsinfo.imagens.service.FileService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@RequestMapping("/file")
public class Controller {

    @Autowired
    private FileService fileService;  // ← Agora usa Supabase!

    @GetMapping("/ler")
    public ResponseEntity<?> download(@RequestParam String file) throws IOException {
        try {
            byte[] img = fileService.readFile(file);
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType("image/jpg"))
                    .contentType(MediaType.parseMediaType("image/png"))
                    .body(img);
        } catch (Exception ex) {
            return ResponseEntity.badRequest().build();
        }
    }

    @PostMapping
    public ResponseEntity create(@RequestParam("file") MultipartFile file) throws IOException {
        if (!file.isEmpty()) {
            return ResponseEntity.ok().body(fileService.createFile(file));
        }
        return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
    }

    @GetMapping("/eliminar")
    public ResponseEntity delete(@RequestParam String caminho) throws IOException {
        if (caminho != null && !caminho.isEmpty()) {
            return ResponseEntity.ok().body(fileService.removeFile(caminho));
        }
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }


    @GetMapping("/ping")
    public ResponseEntity teste()  {
        return ResponseEntity.ok("pong");
    }
}