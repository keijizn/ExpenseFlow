package com.finanzero.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import java.io.IOException;
import java.time.Duration;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class ReceiptStorageService {
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "application/pdf",
            "image/png",
            "image/jpeg",
            "image/jpg"
    );

    private final long maxBytes;
    private final String bucket;
    private final String prefix;
    private final Duration presignedUrlDuration;
    private final S3Client s3Client;
    private final S3Presigner s3Presigner;

    public ReceiptStorageService(
            @Value("${app.upload.max-receipt-bytes:10485760}") long maxBytes,
            @Value("${app.aws.s3.bucket:${AWS_S3_BUCKET:}}") String bucket,
            @Value("${app.aws.s3.region:${AWS_REGION:sa-east-1}}") String region,
            @Value("${app.aws.s3.receipts-prefix:receipts/}") String prefix,
            @Value("${app.aws.s3.presigned-url-minutes:10080}") long presignedUrlMinutes
    ) {
        this.maxBytes = maxBytes;
        this.bucket = bucket == null ? "" : bucket.trim();
        this.prefix = normalizePrefix(prefix);
        this.presignedUrlDuration = Duration.ofMinutes(Math.max(1, presignedUrlMinutes));

        Region awsRegion = Region.of(region == null || region.isBlank() ? "sa-east-1" : region.trim());
        this.s3Client = S3Client.builder()
                .region(awsRegion)
                .build();
        this.s3Presigner = S3Presigner.builder()
                .region(awsRegion)
                .build();
    }

    public StoredReceipt store(MultipartFile file) {
        if (bucket.isBlank()) {
            throw new IllegalStateException("Bucket S3 não configurado. Defina AWS_S3_BUCKET ou app.aws.s3.bucket.");
        }
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Selecione um arquivo de comprovante.");
        }
        if (file.getSize() > maxBytes) {
            throw new IllegalArgumentException("O comprovante deve ter no máximo " + (maxBytes / 1024 / 1024) + " MB.");
        }

        String contentType = file.getContentType() == null ? "" : file.getContentType().toLowerCase(Locale.ROOT);
        if (!ALLOWED_CONTENT_TYPES.contains(contentType)) {
            throw new IllegalArgumentException("Formato inválido. Envie PDF, PNG, JPG ou JPEG.");
        }

        String originalName = StringUtils.cleanPath(file.getOriginalFilename() == null ? "comprovante" : file.getOriginalFilename());
        String extension = extensionFrom(originalName, contentType);
        String storedName = UUID.randomUUID() + extension;
        String objectKey = objectKey(storedName);

        try {
            PutObjectRequest request = PutObjectRequest.builder()
                    .bucket(bucket)
                    .key(objectKey)
                    .contentType(contentType)
                    .contentLength(file.getSize())
                    .metadata(java.util.Map.of(
                            "original-name", sanitizeMetadataValue(originalName)
                    ))
                    .build();

            s3Client.putObject(request, RequestBody.fromInputStream(file.getInputStream(), file.getSize()));
            return new StoredReceipt(storedName, originalName, contentType);
        } catch (IOException e) {
            throw new IllegalStateException("Erro ao ler comprovante.", e);
        } catch (S3Exception e) {
            throw new IllegalStateException("Erro ao enviar comprovante para o Amazon S3: " + e.awsErrorDetails().errorMessage(), e);
        }
    }

    public String temporaryUrl(String fileName) {
        if (bucket.isBlank()) {
            throw new IllegalStateException("Bucket S3 não configurado. Defina AWS_S3_BUCKET ou app.aws.s3.bucket.");
        }
        if (fileName == null || fileName.isBlank() || fileName.contains("/") || fileName.contains("\\")) {
            throw new IllegalArgumentException("Comprovante inválido.");
        }

        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(bucket)
                .key(objectKey(fileName))
                .build();

        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(presignedUrlDuration)
                .getObjectRequest(getObjectRequest)
                .build();

        return s3Presigner.presignGetObject(presignRequest).url().toString();
    }

    public void deleteQuietly(String fileName) {
        if (bucket.isBlank() || fileName == null || fileName.isBlank()) return;
        try {
            s3Client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(bucket)
                    .key(objectKey(fileName))
                    .build());
        } catch (Exception ignored) {
        }
    }

    private String objectKey(String fileName) {
        return prefix + fileName;
    }

    private String normalizePrefix(String value) {
        if (value == null || value.isBlank()) return "receipts/";
        String result = value.trim();
        while (result.startsWith("/")) result = result.substring(1);
        if (!result.endsWith("/")) result += "/";
        return result;
    }

    private String extensionFrom(String originalName, String contentType) {
        String name = originalName.toLowerCase(Locale.ROOT);
        if (name.endsWith(".pdf")) return ".pdf";
        if (name.endsWith(".png")) return ".png";
        if (name.endsWith(".jpg")) return ".jpg";
        if (name.endsWith(".jpeg")) return ".jpeg";
        if (contentType.equals("application/pdf")) return ".pdf";
        if (contentType.equals("image/png")) return ".png";
        return ".jpg";
    }

    private String sanitizeMetadataValue(String value) {
        if (value == null || value.isBlank()) return "comprovante";
        return value.replaceAll("[^\\p{ASCII}]", "_");
    }

    public record StoredReceipt(String fileName, String originalName, String contentType) {}
}
