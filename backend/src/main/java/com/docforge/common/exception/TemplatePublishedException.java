package com.docforge.common.exception;

import java.util.UUID;

public class TemplatePublishedException extends BusinessException {

    public TemplatePublishedException(UUID templateId) {
        super("Шаблон опубликован и не может быть изменён: " + templateId);
    }
}
