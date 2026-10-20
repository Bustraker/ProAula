package com.proaula.aula.Entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "admin_code_requests", indexes = {
    @Index(name = "idx_admin_code_request_status_created", columnList = "request_status, created_at"),
    @Index(name = "idx_admin_code_request_email", columnList = "email")
})
public class AdminCodeRequest {

    public enum Status {
        PENDING,
        APPROVED,
        REJECTED,
        REGISTERED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 254)
    private String email;

    @Column(name = "nombres", length = 100)
    private String nombres;

    @Column(name = "apellidos", length = 100)
    private String apellidos;

    @Column(name = "motivo", length = 500)
    private String motivo;

    @Column(name = "created_by", length = 20)
    private String createdBy;

    @Column(name = "created_by_name", length = 201)
    private String createdByName;

    @Column(name = "request_status", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private Status status = Status.PENDING;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "decided_at")
    private LocalDateTime decidedAt;

    @Column(name = "decided_by", length = 20)
    private String decidedBy;

    @Column(name = "code_email_sent_at")
    private LocalDateTime codeEmailSentAt;

    @Column(name = "code_email_sent_by", length = 20)
    private String codeEmailSentBy;

    public Long getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getNombres() {
        return nombres;
    }

    public void setNombres(String nombres) {
        this.nombres = nombres;
    }

    public String getApellidos() {
        return apellidos;
    }

    public void setApellidos(String apellidos) {
        this.apellidos = apellidos;
    }

    public String getMotivo() {
        return motivo;
    }

    public void setMotivo(String motivo) {
        this.motivo = motivo;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public String getCreatedByName() {
        return createdByName;
    }

    public void setCreatedByName(String createdByName) {
        this.createdByName = createdByName;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getDecidedAt() {
        return decidedAt;
    }

    public void setDecidedAt(LocalDateTime decidedAt) {
        this.decidedAt = decidedAt;
    }

    public String getDecidedBy() {
        return decidedBy;
    }

    public void setDecidedBy(String decidedBy) {
        this.decidedBy = decidedBy;
    }

    public LocalDateTime getCodeEmailSentAt() {
        return codeEmailSentAt;
    }

    public void setCodeEmailSentAt(LocalDateTime codeEmailSentAt) {
        this.codeEmailSentAt = codeEmailSentAt;
    }

    public String getCodeEmailSentBy() {
        return codeEmailSentBy;
    }

    public void setCodeEmailSentBy(String codeEmailSentBy) {
        this.codeEmailSentBy = codeEmailSentBy;
    }
}
