package com.docforge.document;

import com.docforge.AbstractIT;
import com.docforge.document.dto.DocumentListItemResponse;
import com.docforge.document.dto.DocumentMemberDTO;
import com.docforge.document.model.DocumentPermission;
import com.docforge.document.model.DocumentRole;
import com.docforge.template.TemplateService;
import com.docforge.template.dto.CreateTemplateRequest;
import com.docforge.template.dto.TemplateResponse;
import com.docforge.user.User;
import com.docforge.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.security.test.context.support.WithMockUser;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class DocumentMembersIT extends AbstractIT {

    @Autowired private DocumentService documentService;
    @Autowired private DocumentPermissionService documentPermissionService;
    @Autowired private TemplateService templateService;
    @Autowired private UserRepository userRepository;

    private UUID ownerId;
    private UUID editorId;
    private UUID templateId;

    @BeforeEach
    @WithMockUser(username = "test")
    void setUp() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);

        User owner = new User();
        owner.setLogin("doc-owner-" + suffix);
        owner.setPasswordHash("hash");
        owner.setDisplayName("DocOwner");
        ownerId = userRepository.save(owner).getId();

        User editor = new User();
        editor.setLogin("doc-editor-" + suffix);
        editor.setPasswordHash("hash");
        editor.setDisplayName("DocEditor");
        editorId = userRepository.save(editor).getId();

        TemplateResponse template = templateService.create(
                null, new CreateTemplateRequest("MembersIT Template " + suffix, null, null, null));
        templateId = template.id();
    }

    @Test
    @WithMockUser(username = "test")
    @DisplayName("owner имеет все права на созданный документ")
    void owner_hasAllPermissions() {
        var doc = documentService.create(templateId, "Owner Doc", ownerId, null);

        assertThat(documentPermissionService.hasPermission(doc.id(), ownerId, DocumentPermission.READ)).isTrue();
        assertThat(documentPermissionService.hasPermission(doc.id(), ownerId, DocumentPermission.WRITE)).isTrue();
        assertThat(documentPermissionService.hasPermission(doc.id(), ownerId, DocumentPermission.DELETE)).isTrue();
        assertThat(documentPermissionService.hasPermission(doc.id(), ownerId, DocumentPermission.SHARE)).isTrue();
    }

    @Test
    @WithMockUser(username = "test")
    @DisplayName("не-member не имеет прав на документ")
    void nonMember_denied() {
        var doc = documentService.create(templateId, "Private Doc", ownerId, null);

        assertThat(documentPermissionService.hasPermission(doc.id(), editorId, DocumentPermission.READ)).isFalse();
        assertThat(documentPermissionService.getEffectiveRole(doc.id(), editorId)).isNull();
    }

    @Test
    @WithMockUser(username = "test")
    @DisplayName("grantRole() выдаёт права, revokeRole() их забирает")
    void grantAndRevoke() {
        var doc = documentService.create(templateId, "Shared Doc", ownerId, null);

        documentPermissionService.grantRole(doc.id(), editorId, DocumentRole.EDITOR, ownerId);

        assertThat(documentPermissionService.hasPermission(doc.id(), editorId, DocumentPermission.WRITE)).isTrue();
        assertThat(documentPermissionService.hasPermission(doc.id(), editorId, DocumentPermission.DELETE)).isFalse();

        List<DocumentMemberDTO> members = documentPermissionService.listMembers(doc.id(), ownerId);
        assertThat(members).hasSize(1);
        assertThat(members.get(0).userId()).isEqualTo(editorId);
        assertThat(members.get(0).role()).isEqualTo("EDITOR");

        documentPermissionService.revokeRole(doc.id(), editorId, ownerId);
        assertThat(documentPermissionService.hasPermission(doc.id(), editorId, DocumentPermission.READ)).isFalse();
    }

    @Test
    @WithMockUser(username = "test")
    @DisplayName("findAccessibleByUser — список включает документы где user direct_member")
    void list_includesSharedDocuments() {
        var ownDoc = documentService.create(templateId, "Owner's Doc", ownerId, null);
        var sharedDoc = documentService.create(templateId, "Shared with Editor", ownerId, null);

        documentPermissionService.grantRole(sharedDoc.id(), editorId, DocumentRole.EDITOR, ownerId);

        Page<DocumentListItemResponse> editorDocs = documentService.list(editorId, 0, 20);
        assertThat(editorDocs.getContent().stream().map(DocumentListItemResponse::id))
                .contains(sharedDoc.id())
                .doesNotContain(ownDoc.id());

        Page<DocumentListItemResponse> ownerDocs = documentService.list(ownerId, 0, 20);
        assertThat(ownerDocs.getContent().stream().map(DocumentListItemResponse::id))
                .contains(ownDoc.id(), sharedDoc.id());
    }

    @Test
    @WithMockUser(username = "test")
    @DisplayName("getEffectiveRole() — OWNER у создателя, VIEWER у выданного")
    void getEffectiveRole_correct() {
        var doc = documentService.create(templateId, "Role Doc", ownerId, null);

        assertThat(documentPermissionService.getEffectiveRole(doc.id(), ownerId)).isEqualTo(DocumentRole.OWNER);
        assertThat(documentPermissionService.getEffectiveRole(doc.id(), editorId)).isNull();

        documentPermissionService.grantRole(doc.id(), editorId, DocumentRole.VIEWER, ownerId);
        assertThat(documentPermissionService.getEffectiveRole(doc.id(), editorId)).isEqualTo(DocumentRole.VIEWER);
    }
}
