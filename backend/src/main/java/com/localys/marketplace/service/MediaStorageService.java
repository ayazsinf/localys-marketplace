package com.localys.marketplace.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
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

    private final Path uploadRoot;

    public MediaStorageService(@Value("${app.media.upload-dir:uploads}") String uploadDir) {
        this.uploadRoot = Paths.get(uploadDir).toAbsolutePath().normalize();
    }

    public List<String> storeListingImages(Long listingId, List<MultipartFile> files) {
        if (files == null || files.isEmpty()) {
            return List.of();
        }

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
            String contentType = file.getContentType();
            if (contentType == null || !contentType.toLowerCase(Locale.ROOT).startsWith("image/")) {
                throw new IllegalArgumentException("Only image uploads are allowed");
            }

            String extension = resolveExtension(file.getOriginalFilename());
            String filename = UUID.randomUUID().toString().replace("-", "") + extension;
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

    private String resolveExtension(String filename) {
        if (filename == null) {
            return "";
        }
        int lastDot = filename.lastIndexOf('.');
        if (lastDot < 0 || lastDot == filename.length() - 1) {
            return "";
        }
        return filename.substring(lastDot).toLowerCase(Locale.ROOT);
    }
}
