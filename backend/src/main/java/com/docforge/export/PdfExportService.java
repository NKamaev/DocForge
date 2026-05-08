package com.docforge.export;

import com.docforge.template.model.DocumentFormatting;

import java.io.IOException;
import java.util.Map;

public interface PdfExportService {

    /**
     * Конвертирует TipTap JSON-тело шаблона в PDF-байты.
     *
     * @param body       TipTap JSON (корневой узел "doc")
     * @param formatting настройки страницы
     * @return байты PDF-файла
     */
    byte[] export(Map<String, Object> body, DocumentFormatting formatting) throws IOException;
}
