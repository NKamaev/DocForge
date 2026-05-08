package com.docforge.directory;

import com.docforge.common.exception.ResourceNotFoundException;
import com.docforge.directory.dto.*;
import com.docforge.directory.model.DirectoryDocument;
import com.docforge.directory.model.DirectoryRegistry;
import com.docforge.directory.model.DirectorySchemaField;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
class DirectoryServiceImpl implements DirectoryService {

    private final DirectoryRegistryRepository registryRepo;
    private final DirectorySchemaFieldRepository fieldRepo;
    private final DirectoryDocumentRepository docRepo;

    DirectoryServiceImpl(DirectoryRegistryRepository registryRepo,
                         DirectorySchemaFieldRepository fieldRepo,
                         DirectoryDocumentRepository docRepo) {
        this.registryRepo = registryRepo;
        this.fieldRepo    = fieldRepo;
        this.docRepo      = docRepo;
    }

    // ── list ──────────────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public List<DirectoryListItemResponse> list() {
        return registryRepo.findAllByOrderByCreatedAtDesc().stream()
                .map(reg -> {
                    List<DirectoryFieldDto> fields = toFieldDtos(reg.getId());
                    int count = docRepo.findById(reg.getId().toString())
                            .map(d -> d.getEntries().size()).orElse(0);
                    return new DirectoryListItemResponse(
                            reg.getId(), reg.getName(), fields, count,
                            reg.getCreatedAt(), reg.getUpdatedAt());
                })
                .collect(Collectors.toList());
    }

    // ── getById ───────────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public DirectoryResponse getById(UUID id) {
        DirectoryRegistry reg = findRegistryOrThrow(id);
        List<DirectoryFieldDto> fields = toFieldDtos(id);
        List<DirectoryEntryDto> entries = docRepo.findById(id.toString())
                .map(d -> d.getEntries().stream()
                        .map(e -> new DirectoryEntryDto(e.getId(), e.getValues()))
                        .collect(Collectors.toList()))
                .orElse(List.of());
        return new DirectoryResponse(reg.getId(), reg.getName(), fields, entries,
                reg.getCreatedAt(), reg.getUpdatedAt());
    }

    // ── create ────────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public DirectoryResponse create(UUID ownerId, CreateDirectoryRequest req) {
        DirectoryRegistry reg = new DirectoryRegistry();
        reg.setName(req.name());
        reg.setOwnerId(ownerId);
        registryRepo.save(reg);

        saveFields(reg.getId(), req.fields());

        // Initialise empty MongoDB document
        DirectoryDocument doc = new DirectoryDocument();
        doc.setId(reg.getId().toString());
        doc.setEntries(new ArrayList<>());
        docRepo.save(doc);

        return getById(reg.getId());
    }

    // ── update ────────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public DirectoryResponse update(UUID id, UpdateDirectoryRequest req) {
        DirectoryRegistry reg = findRegistryOrThrow(id);
        reg.setName(req.name());
        registryRepo.save(reg);

        // Replace schema fields
        fieldRepo.deleteByDirectoryId(id);
        saveFields(id, req.fields());

        return getById(id);
    }

    // ── delete ────────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public void delete(UUID id) {
        findRegistryOrThrow(id);
        fieldRepo.deleteByDirectoryId(id);
        registryRepo.deleteById(id);
        docRepo.deleteById(id.toString());
    }

    // ── addEntry ──────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public DirectoryResponse addEntry(UUID directoryId, DirectoryEntryRequest req) {
        findRegistryOrThrow(directoryId);
        DirectoryDocument doc = findDocOrInit(directoryId);

        DirectoryDocument.DirectoryEntry entry = new DirectoryDocument.DirectoryEntry();
        entry.setId(UUID.randomUUID().toString());
        entry.setValues(new LinkedHashMap<>(req.values()));
        doc.getEntries().add(entry);
        docRepo.save(doc);

        return getById(directoryId);
    }

    // ── updateEntry ───────────────────────────────────────────────────────────

    @Override
    @Transactional
    public DirectoryResponse updateEntry(UUID directoryId, String entryId, DirectoryEntryRequest req) {
        findRegistryOrThrow(directoryId);
        DirectoryDocument doc = findDocOrThrow(directoryId);

        doc.getEntries().stream()
                .filter(e -> entryId.equals(e.getId()))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Запись не найдена: " + entryId))
                .setValues(new LinkedHashMap<>(req.values()));

        docRepo.save(doc);
        return getById(directoryId);
    }

    // ── deleteEntry ───────────────────────────────────────────────────────────

    @Override
    @Transactional
    public DirectoryResponse deleteEntry(UUID directoryId, String entryId) {
        findRegistryOrThrow(directoryId);
        DirectoryDocument doc = findDocOrThrow(directoryId);
        doc.getEntries().removeIf(e -> entryId.equals(e.getId()));
        docRepo.save(doc);
        return getById(directoryId);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private DirectoryRegistry findRegistryOrThrow(UUID id) {
        return registryRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Справочник не найден: " + id));
    }

    private DirectoryDocument findDocOrThrow(UUID id) {
        return docRepo.findById(id.toString())
                .orElseThrow(() -> new ResourceNotFoundException("Данные справочника не найдены: " + id));
    }

    private DirectoryDocument findDocOrInit(UUID id) {
        return docRepo.findById(id.toString()).orElseGet(() -> {
            DirectoryDocument doc = new DirectoryDocument();
            doc.setId(id.toString());
            doc.setEntries(new ArrayList<>());
            return doc;
        });
    }

    private List<DirectoryFieldDto> toFieldDtos(UUID directoryId) {
        return fieldRepo.findByDirectoryIdOrderByDisplayOrderAsc(directoryId).stream()
                .map(f -> new DirectoryFieldDto(
                        f.getFieldKey(), f.getLabel(),
                        f.getFieldType() != null ? f.getFieldType() : "TEXT",
                        f.getFieldOptions(),
                        f.getDisplayOrder()))
                .collect(Collectors.toList());
    }

    private void saveFields(UUID directoryId, List<DirectoryFieldDto> dtos) {
        if (dtos == null) return;
        for (int i = 0; i < dtos.size(); i++) {
            DirectoryFieldDto dto = dtos.get(i);
            DirectorySchemaField field = new DirectorySchemaField();
            field.setDirectoryId(directoryId);
            field.setFieldKey(dto.key());
            field.setLabel(dto.label());
            field.setFieldType(dto.type() != null ? dto.type() : "TEXT");
            field.setFieldOptions(dto.options());
            field.setDisplayOrder(i);
            fieldRepo.save(field);
        }
    }
}
