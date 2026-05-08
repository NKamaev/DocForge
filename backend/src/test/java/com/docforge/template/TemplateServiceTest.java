package com.docforge.template;

import com.docforge.audit.AuditService;
import com.docforge.common.exception.TemplatePublishedException;
import com.docforge.document.DocumentService;
import com.docforge.document.generator.PythonConditionEvaluator;
import com.docforge.template.dto.UpdateTemplateRequest;
import com.docforge.template.model.Template;
import com.docforge.template.model.TemplateRegistry;
import com.docforge.user.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TemplateServiceTest {

    @Mock private TemplateRegistryRepository registryRepository;
    @Mock private TemplateRepository templateRepository;
    @Mock private TemplateVersionRepository versionRepository;
    @Mock private TemplateSnapshotRepository snapshotRepository;
    @Mock private AuditService auditService;
    @Mock private TemplateValidator templateValidator;
    @Mock private PythonConditionEvaluator pythonConditionEvaluator;
    @Mock private DocumentService documentService;
    @Mock private UserService userService;

    @InjectMocks
    private TemplateServiceImpl templateService;

    @Test
    @DisplayName("update() throws TemplatePublishedException for a published template")
    void update_publishedTemplate_throwsException() {
        UUID id = UUID.randomUUID();
        TemplateRegistry registry = new TemplateRegistry();
        registry.setId(id);
        registry.setName("Published Template");
        registry.setPublished(true);

        when(registryRepository.findById(id)).thenReturn(Optional.of(registry));

        UpdateTemplateRequest request = new UpdateTemplateRequest(
                "Attempt to Modify", null, null, null, null, null, null, null, null, null
        );

        assertThatThrownBy(() -> templateService.update(id, request))
                .isInstanceOf(TemplatePublishedException.class)
                .hasMessageContaining(id.toString());
    }

    @Test
    @DisplayName("delete() throws TemplatePublishedException for a published template")
    void delete_publishedTemplate_throwsException() {
        UUID id = UUID.randomUUID();
        TemplateRegistry registry = new TemplateRegistry();
        registry.setId(id);
        registry.setName("Published Template");
        registry.setPublished(true);

        when(registryRepository.findById(id)).thenReturn(Optional.of(registry));

        assertThatThrownBy(() -> templateService.delete(id))
                .isInstanceOf(TemplatePublishedException.class)
                .hasMessageContaining(id.toString());
    }

    @Test
    @DisplayName("update() succeeds for a draft template (regression)")
    void update_draftTemplate_succeeds() {
        UUID id = UUID.randomUUID();
        TemplateRegistry registry = new TemplateRegistry();
        registry.setId(id);
        registry.setName("Draft Template");
        registry.setPublished(false);

        Template template = Template.builder().id(id).version(1).build();

        when(registryRepository.findById(id)).thenReturn(Optional.of(registry));
        when(templateRepository.findById(id)).thenReturn(Optional.of(template));
        when(registryRepository.save(any())).thenReturn(registry);
        when(templateRepository.save(any())).thenReturn(template);

        UpdateTemplateRequest request = new UpdateTemplateRequest(
                "Draft Updated", null, null, null, null, null, null, null, null, null
        );

        // should not throw
        templateService.update(id, request);
    }
}
