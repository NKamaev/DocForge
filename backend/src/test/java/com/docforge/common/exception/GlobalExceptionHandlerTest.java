package com.docforge.common.exception;

import com.docforge.template.TemplateController;
import com.docforge.template.TemplateOwnerResolver;
import com.docforge.template.TemplateService;
import com.docforge.template.dto.TemplateResponse;
import com.docforge.template.dto.UpdateTemplateRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.UUID;

import static org.hamcrest.Matchers.aMapWithSize;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Web-layer contract tests for the {@link GlobalExceptionHandler} mapping of unreadable
 * request bodies and bean-validation failures (BUG-067).
 *
 * <p><strong>BUG-067:</strong> a request body that Jackson cannot read (unknown enum value,
 * malformed JSON, wrong field type) threw {@code HttpMessageNotReadableException}, which fell
 * into the generic {@code Exception} handler of {@link GlobalExceptionHandler} and produced
 * <strong>HTTP 500</strong>. The fix maps it to <strong>HTTP 400</strong> with a
 * {@code ProblemDetail} whose {@code detail} starts with «Некорректное тело запроса» and never
 * leaks internal Java/Jackson class names (package-qualified names are replaced with {@code …}).</p>
 *
 * <p>Standalone MockMvc slice (same style as {@code DocumentControllerDiffTest} /
 * {@code DocumentControllerApplyAnswersTest}): the real {@code TemplateController} — including
 * real {@code @RequestBody} JSON deserialization of {@code UpdateTemplateRequest} — driven
 * against the real {@link GlobalExceptionHandler}, without spinning up the database stack.</p>
 *
 * <p>Note on the «wrong field type» cases: Jackson silently coerces scalars (e.g.
 * {@code "name": 123} is accepted as the string {@code "123"}), so a genuine type mismatch has
 * to use a non-scalar value where a scalar (or vice versa) is expected.</p>
 */
@ExtendWith(MockitoExtension.class)
class GlobalExceptionHandlerTest {

    private static final UUID TEMPLATE_ID = UUID.fromString("00000000-0000-0000-0000-000000000667");

    @Mock private TemplateService templateService;
    @Mock private TemplateOwnerResolver ownerResolver;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        TemplateController controller = new TemplateController(templateService, ownerResolver);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();
    }

    // ── BUG-067: unreadable body → 400, not 500 ───────────────────────────────

    /**
     * The original bug report: {@code parameters[].type = "boolean"} (lowercase) instead of
     * the enum constant {@code BOOLEAN}. Jackson raises {@code InvalidFormatException};
     * the handler must answer 400 and scrub the fully-qualified enum/DTO class names out of
     * the {@code detail}.
     */
    @Test
    @DisplayName("BUG-067: unknown enum value in body returns 400 with scrubbed detail")
    void update_withUnknownEnumValue_returns400WithScrubbedDetail() throws Exception {
        // given — lowercase enum constant, exactly as in the bug report
        String body = "{\"name\":\"BUG-067 Template\",\"parameters\":[{\"code\":\"penalty\",\"type\":\"boolean\"}]}";

        // when / then — 400 (client error), not 500
        mockMvc.perform(put("/api/templates/{id}", TEMPLATE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.status").value(400))
                // detail starts with the friendly prefix …
                .andExpect(jsonPath("$.detail", startsWith("Некорректное тело запроса")))
                // … carries the real deserialization reason (the offending value) …
                .andExpect(jsonPath("$.detail", containsString("\"boolean\"")))
                // … and leaks no internal class names
                .andExpect(jsonPath("$.detail", not(containsString("com.docforge"))))
                .andExpect(jsonPath("$.detail", not(containsString(".java"))));

        // the request never reached the service layer
        verifyNoInteractions(templateService, ownerResolver);
    }

    /** Truncated JSON body (the request ended mid-object) — a client error, not a 500. */
    @Test
    @DisplayName("BUG-067: malformed JSON body returns 400, not 500")
    void update_withMalformedJson_returns400() throws Exception {
        // given — JSON truncated after the field name
        String body = "{\"name\": ";

        // when / then
        mockMvc.perform(put("/api/templates/{id}", TEMPLATE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.detail", startsWith("Некорректное тело запроса")))
                .andExpect(jsonPath("$.detail", not(containsString("com.docforge"))))
                .andExpect(jsonPath("$.detail", not(containsString(".java"))));

        verifyNoInteractions(templateService, ownerResolver);
    }

    /**
     * Wrong field type: an object where the DTO expects a string ({@code name}).
     * (A bare {@code "name": 123} is deliberately not used here — Jackson coerces numeric
     * scalars to strings, so it would deserialize successfully.)
     */
    @Test
    @DisplayName("BUG-067: wrong field type (object for string) returns 400, not 500")
    void update_withWrongFieldType_returns400() throws Exception {
        // given — object value in the string field `name`
        String body = "{\"name\":{\"first\":\"Иван\"}}";

        // when / then
        mockMvc.perform(put("/api/templates/{id}", TEMPLATE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.detail", startsWith("Некорректное тело запроса")))
                .andExpect(jsonPath("$.detail", not(containsString("com.docforge"))))
                .andExpect(jsonPath("$.detail", not(containsString(".java"))));

        verifyNoInteractions(templateService, ownerResolver);
    }

    /** Wrong container type: a scalar where the DTO expects a list ({@code parameters}). */
    @Test
    @DisplayName("BUG-067: wrong container type (scalar for list) returns 400, not 500")
    void update_withWrongContainerType_returns400() throws Exception {
        // given — string value in the array field `parameters`
        String body = "{\"name\":\"BUG-067 Template\",\"parameters\":\"oops\"}";

        // when / then
        mockMvc.perform(put("/api/templates/{id}", TEMPLATE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.detail", startsWith("Некорректное тело запроса")))
                .andExpect(jsonPath("$.detail", not(containsString("com.docforge"))))
                .andExpect(jsonPath("$.detail", not(containsString(".java"))));

        verifyNoInteractions(templateService, ownerResolver);
    }

    // ── BUG-067 review WARNING: duplicate field errors on one key ────────────

    /**
     * Regression for the review WARNING on the BUG-067 fix: {@code handleValidation} built its
     * {@code errors} map with {@code Collectors.toMap(...)} without a merge function. Two
     * violated constraints on the <em>same</em> field produce two {@link FieldError}s with one
     * key, so the collector threw {@code IllegalStateException: Duplicate key …}, which fell
     * into the catch-all handler and answered <strong>HTTP 500</strong> instead of 400. The
     * merge function {@code (first, second) -> first} keeps the first message.
     *
     * <p>The body sets {@code name} to 501 spaces: {@code @NotBlank} fails (blank) and
     * {@code @Size(max = 500)} fails (length 501) at the same time on the single field
     * {@code name} of {@link UpdateTemplateRequest}.</p>
     */
    @Test
    @DisplayName("BUG-067: two constraint violations on one field return 400, not 500 (duplicate-key regression)")
    void update_withTwoViolationsOnSameField_returns400WithErrorsMap() throws Exception {
        // given — 501 spaces: violates @NotBlank AND @Size(max = 500) on `name` simultaneously
        String body = "{\"name\":\"" + " ".repeat(501) + "\"}";

        // when / then — 400 «Ошибка валидации», not 500 from the duplicate-key crash;
        // the two FieldErrors merge into a single `errors.name` entry
        mockMvc.perform(put("/api/templates/{id}", TEMPLATE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.detail").value("Ошибка валидации"))
                .andExpect(jsonPath("$.errors", aMapWithSize(1)))
                .andExpect(jsonPath("$.errors.name").isNotEmpty());

        // the request never reached the service layer
        verifyNoInteractions(templateService, ownerResolver);
    }

    // ── regressions ───────────────────────────────────────────────────────────

    /** A well-formed body must still reach the service and answer 200. */
    @Test
    @DisplayName("PUT /api/templates/{id} with a valid body still returns 200 (regression)")
    void update_withValidBody_returns200() throws Exception {
        // given — readable body with the enum spelled the right way round
        String body = "{\"name\":\"BUG-067 Valid\",\"parameters\":[{\"code\":\"penalty\",\"type\":\"BOOLEAN\"}]}";
        when(templateService.update(eq(TEMPLATE_ID), any(UpdateTemplateRequest.class)))
                .thenReturn(templateResponse(TEMPLATE_ID, "BUG-067 Valid"));

        // when / then
        mockMvc.perform(put("/api/templates/{id}", TEMPLATE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(TEMPLATE_ID.toString()))
                .andExpect(jsonPath("$.name").value("BUG-067 Valid"));
    }

    /**
     * A bad query parameter ({@code createdAfter} that is not an ISO date-time) must still be
     * answered by the pre-existing {@code TypeMismatchException} handler with 400 — not by the
     * {@code HttpMessageNotReadableException} handler and not by the catch-all.
     */
    @Test
    @DisplayName("GET /api/templates with a malformed query param still returns 400 (regression)")
    void list_withMalformedQueryParam_returns400() throws Exception {
        // when / then
        mockMvc.perform(get("/api/templates")
                        .param("createdAfter", "not-a-date"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.detail").value("Некорректное значение параметра запроса"))
                .andExpect(jsonPath("$.parameter").value("createdAfter"));

        verifyNoInteractions(templateService, ownerResolver);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    /** Minimal {@link TemplateResponse} for the happy-path stub (unused fields are null). */
    private static TemplateResponse templateResponse(UUID id, String name) {
        return new TemplateResponse(
                id,                 // id
                "bug067",           // code
                name,               // name
                null,               // description
                null,               // category
                1,                  // version
                1,                  // majorVersion
                0,                  // minorVersion
                false,              // published
                null,               // ownerId
                null,               // createdAt
                null,               // updatedAt
                null,               // defaultFolderId
                null,               // parameters
                null,               // questionnaire
                null,               // body
                null,               // formatting
                null,               // scripts
                null);              // phrasings
    }
}
