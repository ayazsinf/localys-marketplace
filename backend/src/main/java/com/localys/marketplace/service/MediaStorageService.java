package com.localys.marketplace.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class MediaStorageService {

    private static final String STORAGE_R2 = "r2";
    private static final long MAX_IMAGE_BYTES = 10L * 1024 * 1024;

    private final String storage;
    private final Path uploadRoot;
    private final String r2Bucket;
    private final String r2PublicBaseUrl;
    private final S3Client s3Client;

    public MediaStorageService(
            @Value("${app.media.storage:local}") String storage,
            @Value("${app.media.upload-dir:uploads}") String uploadDir,
            @Value("${app.media.r2.bucket:}") String r2Bucket,
            @Value("${app.media.r2.endpoint:}") String r2Endpoint,
            @Value("${app.media.r2.access-key-id:}") String r2AccessKeyId,
            @Value("${app.media.r2.secret-access-key:}") String r2SecretAccessKey,
            @Value("${app.media.r2.public-base-url:}") String r2PublicBaseUrl
    ) {
        this.storage = storage == null ? "local" : storage.trim().toLowerCase(Locale.ROOT);
        this.uploadRoot = Paths.get(uploadDir).toAbsolutePath().normalize();
        this.r2Bucket = requireR2Value("bucket", r2Bucket);
        this.r2PublicBaseUrl = trimTrailingSlash(requireR2Value("public-base-url", r2PublicBaseUrl));
        this.s3Client = STORAGE_R2.equals(this.storage)
                ? buildR2Client(r2Endpoint, r2AccessKeyId, r2SecretAccessKey)
                : null;
    }

    public List<String> storeListingImages(Long listingId, List<MultipartFile> files) {
        if (files == null || files.isEmpty()) {
            return List.of();
        }
        // Reject the whole batch up front so a bad file does not leave earlier ones orphaned.
        files.stream()
                .filter(file -> file != null && !file.isEmpty())
                .forEach(this::validateImage);
        if (STORAGE_R2.equals(storage)) {
            return storeListingImagesInR2(listingId, files);
        }

        return storeListingImagesLocally(listingId, files);
    }

    private List<String> storeListingImagesLocally(Long listingId, List<MultipartFile> files) {
        Path listingDir = uploadRoot.resolve("listings").resolve(String.valueOf(listingId));
        try {
            Files.createDirectories(listingDir);
        } catch (IOException e) {
            throw new RuntimeException("Could not create upload directory", e);
        }

        List<String> urls = new ArrayList<>();
        for (MultipartFile file : files) {
            if (file == null || file.isEmpty()) {
                continue;
            }
            ImageType imageType = validateImage(file);
            String filename = UUID.randomUUID().toString().replace("-", "") + imageType.extension();
            Path target = listingDir.resolve(filename);
            try (InputStream inputStream = file.getInputStream()) {
                Files.copy(inputStream, target, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                throw new RuntimeException("Failed to store file", e);
            }
            urls.add("/uploads/listings/" + listingId + "/" + filename);
        }
        return urls;
    }

    private List<String> storeListingImagesInR2(Long listingId, List<MultipartFile> files) {
        List<String> urls = new ArrayList<>();
        for (MultipartFile file : files) {
            if (file == null || file.isEmpty()) {
                continue;
            }
            ImageType imageType = validateImage(file);
            String filename = UUID.randomUUID().toString().replace("-", "") + imageType.extension();
            String key = "listings/" + listingId + "/" + filename;
            PutObjectRequest request = PutObjectRequest.builder()
                    .bucket(r2Bucket)
                    .key(key)
                    .contentType(imageType.contentType())
                    .cacheControl("public, max-age=31536000, immutable")
                    .build();

            try (InputStream inputStream = file.getInputStream()) {
                s3Client.putObject(request, RequestBody.fromInputStream(inputStream, file.getSize()));
            } catch (IOException e) {
                throw new RuntimeException("Failed to read uploaded file", e);
            } catch (RuntimeException e) {
                throw new RuntimeException("Failed to store file in Cloudflare R2", e);
            }
            urls.add(r2PublicBaseUrl + "/" + key);
        }
        return urls;
    }

    /**
     * Identifies the image from its leading bytes. The client-supplied content type and file
     * name are ignored: trusting them would let e.g. an HTML file be stored and later served
     * as a page (stored XSS).
     */
    private ImageType validateImage(MultipartFile file) {
        if (file.getSize() > MAX_IMAGE_BYTES) {
            throw new IllegalArgumentException("Image is too large");
        }
        byte[] header = new byte[12];
        int read;
        try (InputStream inputStream = file.getInputStream()) {
            read = inputStream.readNBytes(header, 0, header.length);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read uploaded file", e);
        }
        if (read >= 3 && (header[0] & 0xFF) == 0xFF && (header[1] & 0xFF) == 0xD8 && (header[2] & 0xFF) == 0xFF) {
            return new ImageType("image/jpeg", ".jpg");
        }
        if (read >= 8 && startsWith(header, new int[]{0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A})) {
            return new ImageType("image/png", ".png");
        }
        if (read >= 6 && startsWith(header, new int[]{'G', 'I', 'F', '8'})) {
            return new ImageType("image/gif", ".gif");
        }
        if (read >= 12 && startsWith(header, new int[]{'R', 'I', 'F', 'F'})
                && header[8] == 'W' && header[9] == 'E' && header[10] == 'B' && header[11] == 'P') {
            return new ImageType("image/webp", ".webp");
        }
        throw new IllegalArgumentException("Only image uploads are allowed");
    }

    private boolean startsWith(byte[] data, int[] prefix) {
        for (int i = 0; i < prefix.length; i++) {
            if ((data[i] & 0xFF) != prefix[i]) {
                return false;
            }
        }
        return true;
    }

    private record ImageType(String contentType, String extension) {
    }

    private S3Client buildR2Client(String endpoint, String accessKeyId, String secretAccessKey) {
        AwsBasicCredentials credentials = AwsBasicCredentials.create(
                requireR2Value("access-key-id", accessKeyId),
                requireR2Value("secret-access-key", secretAccessKey)
        );
        return S3Client.builder()
                .endpointOverride(URI.create(requireR2Value("endpoint", endpoint)))
                .credentialsProvider(StaticCredentialsProvider.create(credentials))
                .region(Region.of("auto"))
                .forcePathStyle(true)
                .build();
    }

    private String requireR2Value(String name, String value) {
        if (!STORAGE_R2.equals(storage)) {
            return value;
        }
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing Cloudflare R2 media setting: app.media.r2." + name);
        }
        return value.trim();
    }

    private String trimTrailingSlash(String value) {
        if (value == null) {
            return null;
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
