package com.localys.marketplace.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

@Getter
@Setter
@Entity
@Table(name = "messages")
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class Message extends AuditableEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "conversation_id", nullable = false)
    private Conversation conversation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sender_id", nullable = false)
    private UserEntity sender;

    /** Empty when the message is only a photo. */
    @Column(nullable = false, length = 4000)
    private String body;

    @Column(name = "image_url", length = 500)
    private String imageUrl;

    /** A {@link com.localys.marketplace.service.MessageSafetyService.Warning} name, or null. */
    @Column(name = "safety_warning", length = 30)
    private String safetyWarning;

    @Column(name = "read_at")
    private OffsetDateTime readAt;
}
