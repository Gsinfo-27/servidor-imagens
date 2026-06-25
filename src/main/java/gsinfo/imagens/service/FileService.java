package gsinfo.imagens.service;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import okhttp3.MediaType;
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

    @Value("${supabase.storage.bucket:images}")
    private String bucketName;

    @Value("${supabase.storage.folder:produtos}")
    private String defaultFolder;

    private OkHttpClient client;

    @PostConstruct
    public void init() {
        this.client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build();

        logger.info("✅ FileService inicializado com Supabase Storage!");
        logger.info("📡 URL: {}", supabaseUrl);
        logger.info("📦 Bucket: {}", bucketName);
    }

    // ============================================
    // MÉTODOS PARA O CONTROLLER
    // ============================================

    /**
     * Upload de arquivo (usado pelo Controller)
     */
    public String uploadFile(MultipartFile file, String folder) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Arquivo vazio ou nulo");
        }

        String originalFilename = file.getOriginalFilename();
        String extension = "";
        if (originalFilename != null && originalFilename.contains(".")) {
            extension = originalFilename.substring(originalFilename.lastIndexOf("."));
        }
        String uniqueName = UUID.randomUUID().toString() + extension;

        String folderPath = (folder != null && !folder.isEmpty()) ? folder : defaultFolder;
        String filePath = folderPath + "/" + uniqueName;

        String url = supabaseUrl + "/storage/v1/object/" + bucketName + "/" + filePath;

        logger.info("📤 Upload para Supabase: {}", url);

        RequestBody requestBody = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", file.getOriginalFilename(),
                        RequestBody.create(file.getBytes(), MediaType.parse(file.getContentType())))
                .build();

        Request request = new Request.Builder()
                .url(url)
                .post(requestBody)
                .addHeader("Authorization", "Bearer " + serviceRoleKey)
                .addHeader("apikey", serviceRoleKey)
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String errorBody = response.body() != null ? response.body().string() : "Unknown error";
                throw new IOException("Upload falhou: " + response.code() + " - " + errorBody);
            }

            String publicUrl = getPublicUrl(filePath);
            logger.info("✅ Upload concluído: {}", publicUrl);
            return publicUrl;
        }
    }

    /**
     * Download de arquivo (usado pelo Controller)
     */
    public byte[] downloadFile(String filePath) throws IOException {
        String url = supabaseUrl + "/storage/v1/object/" + bucketName + "/" + filePath;

        logger.info("📥 Download do Supabase: {}", url);

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

            if (response.body() == null) {
                throw new IOException("Resposta vazia");
            }

            return response.body().bytes();
        }
    }

    /**
     * Remove arquivo (usado pelo Controller)
     */
    public boolean deleteFile(String filePath) {
        String url = supabaseUrl + "/storage/v1/object/" + bucketName + "/" + filePath;

        logger.info("🗑️ Removendo do Supabase: {}", url);

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
     * Verifica se o arquivo existe (usado pelo Controller)
     */
    public boolean fileExists(String filePath) {
        try {
            byte[] data = downloadFile(filePath);
            return data != null && data.length > 0;
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Obtém URL pública (usado pelo Controller)
     */
    public String getPublicUrl(String filePath) {
        return supabaseUrl + "/storage/v1/object/public/" + bucketName + "/" + filePath;
    }

    /**
     * Extrai o caminho do bucket da URL (usado pelo Controller)
     */
    public String extractPathFromUrl(String url) {
        if (url == null || url.isEmpty()) {
            return "";
        }

        String prefix = supabaseUrl + "/storage/v1/object/public/" + bucketName + "/";
        if (url.startsWith(prefix)) {
            return url.substring(prefix.length());
        }

        if (url.contains("/storage/v1/object/public/")) {
            int idx = url.indexOf("/storage/v1/object/public/") + 28;
            return url.substring(idx);
        }

        return url;
    }

    /**
     * Extrai o nome do arquivo (usado pelo Controller)
     */
    public String extractFileName(String filePath) {
        if (filePath == null || filePath.isEmpty()) {
            return "";
        }

        if (filePath.contains("/storage/v1/object/public/")) {
            int idx = filePath.indexOf("/storage/v1/object/public/") + 28;
            filePath = filePath.substring(idx);
        }

        if (filePath.contains("/")) {
            return filePath.substring(filePath.lastIndexOf("/") + 1);
        }

        return filePath;
    }

    // ============================================
    // MÉTODOS LEGADO (para compatibilidade)
    // ============================================

    public String createFile(MultipartFile file) throws IOException {
        return uploadFile(file, defaultFolder);
    }

    public byte[] readFile(String nome) throws IOException {
        String filePath = extractPathFromUrl(nome);
        return downloadFile(filePath);
    }

    public String removeFile(String nome) {
        try {
            String filePath = extractPathFromUrl(nome);
            boolean deleted = deleteFile(filePath);
            if (deleted) {
                return "Arquivo eliminado com sucesso: " + nome;
            } else {
                return "Erro ao eliminar o arquivo: " + nome;
            }
        } catch (Exception e) {
            logger.error("❌ Erro ao remover: {}", e.getMessage());
            return "Erro ao eliminar o arquivo: " + e.getMessage();
        }
    }
}


