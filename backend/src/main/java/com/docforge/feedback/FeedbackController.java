package com.docforge.feedback;

import com.docforge.audit.AuditService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/feedback")
@RequiredArgsConstructor
public class FeedbackController {

    private final AuditService auditService;

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void reportIssue(
            @Valid @RequestBody FeedbackRequest request,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        String login = userDetails != null ? userDetails.getUsername() : "anonymous";

        String elementsJson = request.elements().stream()
                .map(e -> "{\"selector\":\"%s\",\"label\":\"%s\"}".formatted(escape(e.selector()), escape(e.label())))
                .toList()
                .toString();

        log.info("Bug report from {}: elements={}, url={}, desc={}",
                login, elementsJson, request.url(), request.description());

        auditService.log(login, "FEEDBACK", UUID.randomUUID(), "BUG_REPORT",
                "{\"elements\":%s,\"url\":\"%s\",\"description\":\"%s\"}"
                        .formatted(elementsJson, escape(request.url()), escape(request.description())));
    }

    private static String escape(String s) {
        return s != null ? s.replace("\\", "\\\\").replace("\"", "\\\"") : "";
    }

    public record ElementInfo(
            @NotBlank String selector,
            @NotBlank String label
    ) {}

    public record FeedbackRequest(
            @NotEmpty List<ElementInfo> elements,
            @NotBlank String description,
            String url
    ) {}
}
