package com.docforge.document.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.*;

import java.io.Serializable;
import java.util.UUID;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class DocumentParticipantId implements Serializable {

    @Column(name = "document_id")
    private UUID documentId;

    @Column(name = "user_id")
    private UUID userId;
}
