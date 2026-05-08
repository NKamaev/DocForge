package com.docforge.directory;

import com.docforge.directory.dto.*;
import com.docforge.template.TemplateOwnerResolver;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/directories")
public class DirectoryController {

    private final DirectoryService directoryService;
    private final TemplateOwnerResolver ownerResolver;

    public DirectoryController(DirectoryService directoryService,
                               TemplateOwnerResolver ownerResolver) {
        this.directoryService = directoryService;
        this.ownerResolver    = ownerResolver;
    }

    @GetMapping
    public List<DirectoryListItemResponse> list() {
        return directoryService.list();
    }

    @GetMapping("/{id}")
    public DirectoryResponse getById(@PathVariable UUID id) {
        return directoryService.getById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public DirectoryResponse create(@RequestBody CreateDirectoryRequest req,
                                    @AuthenticationPrincipal UserDetails userDetails) {
        UUID ownerId = ownerResolver.resolve(userDetails.getUsername());
        return directoryService.create(ownerId, req);
    }

    @PutMapping("/{id}")
    public DirectoryResponse update(@PathVariable UUID id,
                                    @RequestBody UpdateDirectoryRequest req) {
        return directoryService.update(id, req);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        directoryService.delete(id);
    }

    // ── Entries ───────────────────────────────────────────────────────────────

    @PostMapping("/{id}/entries")
    @ResponseStatus(HttpStatus.CREATED)
    public DirectoryResponse addEntry(@PathVariable UUID id,
                                      @RequestBody DirectoryEntryRequest req) {
        return directoryService.addEntry(id, req);
    }

    @PutMapping("/{id}/entries/{entryId}")
    public DirectoryResponse updateEntry(@PathVariable UUID id,
                                         @PathVariable String entryId,
                                         @RequestBody DirectoryEntryRequest req) {
        return directoryService.updateEntry(id, entryId, req);
    }

    @DeleteMapping("/{id}/entries/{entryId}")
    public DirectoryResponse deleteEntry(@PathVariable UUID id,
                                         @PathVariable String entryId) {
        return directoryService.deleteEntry(id, entryId);
    }
}
