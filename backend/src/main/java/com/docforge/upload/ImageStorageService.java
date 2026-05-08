package com.docforge.upload;

import com.docforge.upload.dto.ImageUploadResponse;
import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

public interface ImageStorageService {

    /** Сохранить изображение и вернуть id + публичный URL. */
    ImageUploadResponse store(MultipartFile file);

    /** Загрузить файл по имени (uuid.ext). */
    Resource load(String filename);
}
