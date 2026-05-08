package com.docforge.upload;

import com.docforge.common.exception.ResourceNotFoundException;
import com.docforge.common.exception.ValidationException;
import com.docforge.upload.dto.ImageUploadResponse;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
public class LocalImageStorageService implements ImageStorageService {

    private static final Set<String> ALLOWED_TYPES = Set.of(
            "image/jpeg", "image/png", "image/gif", "image/webp"
    );
    private static final Map<String, String> MIME_TO_EXT = Map.of(
            "image/jpeg", "jpg",
            "image/png",  "png",
            "image/gif",  "gif",
            "image/webp", "webp"
    );

    @Value("${app.upload.dir:./uploads/images}")
    private String uploadDir;

    private Path uploadPath;

    @PostConstruct
    public void init() throws IOException {
        uploadPath = Paths.get(uploadDir).toAbsolutePath().normalize();
        Files.createDirectories(uploadPath);
        log.info("Image upload directory: {}", uploadPath);
    }

    @Override
    public ImageUploadResponse store(MultipartFile file) {
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_TYPES.contains(contentType)) {
            throw new ValidationException(
                    "Недопустимый тип файла",
                    Map.of("file", "Допустимые форматы: JPEG, PNG, GIF, WebP")
            );
        }

        String ext = MIME_TO_EXT.get(contentType);
        String id = UUID.randomUUID().toString();
        String filename = id + "." + ext;
        Path target = uploadPath.resolve(filename);

        try {
            Files.copy(file.getInputStream(), target);
        } catch (IOException e) {
            throw new RuntimeException("Не удалось сохранить файл: " + filename, e);
        }

        String url = "/api/uploads/images/" + filename;
        log.debug("Stored image: {}", target);
        return new ImageUploadResponse(id, url);
    }

    @Override
    public Resource load(String filename) {
        // Security: prevent path traversal
        if (filename.contains("..") || filename.contains("/")) {
            throw new ResourceNotFoundException("Файл не найден: " + filename);
        }
        Path file = uploadPath.resolve(filename).normalize();
        if (!file.startsWith(uploadPath)) {
            throw new ResourceNotFoundException("Файл не найден: " + filename);
        }

        try {
            Resource resource = new UrlResource(file.toUri());
            if (!resource.exists() || !resource.isReadable()) {
                throw new ResourceNotFoundException("Файл не найден: " + filename);
            }
            return resource;
        } catch (MalformedURLException e) {
            throw new ResourceNotFoundException("Файл не найден: " + filename);
        }
    }
}
