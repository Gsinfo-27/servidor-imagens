package gsinfo.imagens.service;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.RequestBody;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
public class FileService {

    private static final Logger logger = LoggerFactory.getLogger(FileService.class);

    @Value("${supabase.url}")
    private String supabaseUrl;

    @Value("${supabase.service-role-key}")
    private String serviceRoleKey;

    @Value("${supabase.storage.bucket:imagem}")  // 🔑 Default agora é "imagem"
    private String bucketName;

    @Value("${supabase.enabled:true}")
    private boolean supabaseEnabled;

    private OkHttpClient client;

    @PostConstruct
    public void init() {
        this.client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build();

        if (supabaseEnabled) {
            logger.info("✅ FileService inicializado com Supabase Storage!");
            logger.info("📡 URL: {}", supabaseUrl);
            logger.info("📦 Bucket: {}", bucketName);
            logger.info("📁 Upload direto na raiz do bucket: {}", bucketName);
        } else {
            logger.warn("⚠️ Supabase está desabilitado.");
        }
    }

    /**
     * Upload de arquivo usando MultipartBody (form-data)
     */
    public String uploadFile(MultipartFile file) throws IOException {
        if (!supabaseEnabled) {
            logger.warn("⚠️ Supabase desabilitado. Salvando arquivo localmente.");
            return saveLocally(file);
        }

        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Arquivo vazio ou nulo");
        }

        // Gera nome único
        String originalFilename = file.getOriginalFilename();
        String extension = getFileExtension(originalFilename);
        String uniqueName = UUID.randomUUID().toString() + extension;

        // 🔑 URL usando o novo bucket "imagem"
        String url = supabaseUrl + "/storage/v1/object/" + bucketName + "/" + uniqueName;

        logger.info("📤 Upload para Supabase: {}", url);
        logger.info("📁 Arquivo: {}", originalFilename);
        logger.info("📏 Tamanho: {} bytes", file.getSize());
        logger.info("📋 Content-Type: {}", file.getContentType());
        logger.info("📦 Bucket: {}", bucketName);

        // Usa MultipartBody.FORM
        MediaType mediaType = MediaType.parse(file.getContentType());
        if (mediaType == null) {
            mediaType = MediaType.parse("image/jpeg");
        }

        MultipartBody multipartBody = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", originalFilename,
                        RequestBody.create(file.getBytes(), mediaType))
                .build();

        Request request = new Request.Builder()
                .url(url)
                .post(multipartBody)
                .addHeader("Authorization", "Bearer " + serviceRoleKey)
                .addHeader("apikey", serviceRoleKey)
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (response.isSuccessful()) {
                String publicUrl = getPublicUrl(uniqueName);
                logger.info("✅ Upload concluído com sucesso!");
                logger.info("🔗 URL: {}", publicUrl);
                return publicUrl;
            } else {
                String errorBody = response.body() != null ? response.body().string() : "Unknown";
                logger.error("❌ Upload falhou: {} - {}", response.code(), errorBody);
                throw new IOException("Upload falhou: " + response.code() + " - " + errorBody);
            }
        }
    }

    /**
     * Download de arquivo
     */
    public byte[] downloadFile(String fileName) throws IOException {
        if (!supabaseEnabled) {
            throw new IOException("Supabase está desabilitado");
        }

        String url = supabaseUrl + "/storage/v1/object/" + bucketName + "/" + fileName;

        logger.info("📥 Download: {}", url);

        Request request = new Request.Builder()
                .url(url)
                .get()
                .addHeader("Authorization", "Bearer " + serviceRoleKey)
                .addHeader("apikey", serviceRoleKey)
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Download falhou: " + response.code());
            }
            return response.body() != null ? response.body().bytes() : new byte[0];
        }
    }

    /**
     * Remove arquivo
     */
    public boolean deleteFile(String fileName) {
        if (!supabaseEnabled) {
            return false;
        }

        String url = supabaseUrl + "/storage/v1/object/" + bucketName + "/" + fileName;

        logger.info("🗑️ Removendo: {}", url);

        Request request = new Request.Builder()
                .url(url)
                .delete()
                .addHeader("Authorization", "Bearer " + serviceRoleKey)
                .addHeader("apikey", serviceRoleKey)
                .build();

        try (Response response = client.newCall(request).execute()) {
            return response.isSuccessful();
        } catch (IOException e) {
            logger.error("❌ Erro ao remover: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Verifica se o arquivo existe
     */
    public boolean fileExists(String fileName) {
        if (!supabaseEnabled) {
            return false;
        }

        try {
            String url = supabaseUrl + "/storage/v1/object/" + bucketName + "/" + fileName;
            Request request = new Request.Builder()
                    .url(url)
                    .head()
                    .addHeader("Authorization", "Bearer " + serviceRoleKey)
                    .addHeader("apikey", serviceRoleKey)
                    .build();

            try (Response response = client.newCall(request).execute()) {
                return response.isSuccessful();
            }
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Obtém URL pública
     */
    public String getPublicUrl(String fileName) {
        return supabaseUrl + "/storage/v1/object/public/" + bucketName + "/" + fileName;
    }

    /**
     * Extrai nome do arquivo da URL
     */
    public String extractFileNameFromUrl(String url) {
        if (url == null || url.isEmpty()) {
            return "";
        }

        String publicPrefix = supabaseUrl + "/storage/v1/object/public/" + bucketName + "/";
        if (url.startsWith(publicPrefix)) {
            return url.substring(publicPrefix.length());
        }

        String objectPrefix = supabaseUrl + "/storage/v1/object/" + bucketName + "/";
        if (url.startsWith(objectPrefix)) {
            return url.substring(objectPrefix.length());
        }

        return url;
    }

    /**
     * Salva localmente (fallback)
     */
    private String saveLocally(MultipartFile file) throws IOException {
        String originalFilename = file.getOriginalFilename();
        String extension = getFileExtension(originalFilename);
        String fileName = System.currentTimeMillis() + "_" + UUID.randomUUID().toString() + extension;

        java.nio.file.Path path = java.nio.file.Paths.get("uploads", fileName);
        java.nio.file.Files.createDirectories(path.getParent());
        file.transferTo(path.toFile());

        logger.info("✅ Arquivo salvo localmente: {}", path);
        return "/uploads/" + fileName;
    }

    // ============================================
    // MÉTODOS LEGADO
    // ============================================

    public String createFile(MultipartFile file) throws IOException {
        logger.info("📤 Processando upload de imagem:");
        logger.info("   - Nome: {}", file.getOriginalFilename());
        logger.info("   - Tipo: {}", file.getContentType());
        logger.info("   - Tamanho: {} bytes", file.getSize());
        logger.info("   - Bucket: {}", bucketName);

        return uploadFile(file);
    }

    public byte[] readFile(String nome) throws IOException {
        String fileName = extractFileNameFromUrl(nome);
        return downloadFile(fileName);
    }

    public String removeFile(String nome) {
        try {
            String fileName = extractFileNameFromUrl(nome);
            boolean deleted = deleteFile(fileName);
            return deleted ? "Arquivo eliminado com sucesso: " + nome : "Erro ao eliminar: " + nome;
        } catch (Exception e) {
            return "Erro ao eliminar: " + e.getMessage();
        }
    }

    // ============================================
    // MÉTODOS DE UTILIDADE
    // ============================================

    private String getFileExtension(String filename) {
        if (filename == null || !filename.contains(".")) {
            return "";
        }
        return filename.substring(filename.lastIndexOf("."));
    }
}