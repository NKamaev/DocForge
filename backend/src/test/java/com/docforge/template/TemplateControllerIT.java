package com.docforge.template;

import com.docforge.AbstractIT;
import com.docforge.template.dto.CreateTemplateRequest;
import com.docforge.template.dto.TemplateResponse;
import com.docforge.template.dto.UpdateTemplateRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class TemplateControllerIT extends AbstractIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TemplateService templateService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @WithMockUser(username = "testuser")
    @DisplayName("PUT /api/templates/{id} returns 409 for published template")
    void update_publishedTemplate_returns409() throws Exception {
        TemplateResponse created = templateService.create(
                null,
                new CreateTemplateRequest("HTTP Published Template", null, null, null)
        );
        templateService.publish(created.id());

        UpdateTemplateRequest body = new UpdateTemplateRequest("Attempt to Update", null, null, null, null, null, null, null, null, null);
        mockMvc.perform(put("/api/templates/" + created.id())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isConflict());
    }

    @Test
    @WithMockUser(username = "testuser")
    @DisplayName("PUT /api/templates/{id} returns 200 for draft template (regression)")
    void update_draftTemplate_returns200() throws Exception {
        TemplateResponse created = templateService.create(
                null,
                new CreateTemplateRequest("HTTP Draft Template", null, null, null)
        );

        UpdateTemplateRequest body = new UpdateTemplateRequest("Updated Draft", null, null, null, null, null, null, null, null, null);
        mockMvc.perform(put("/api/templates/" + created.id())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk());
    }
}
