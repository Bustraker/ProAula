package com.proaula.aula.Entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "admin_access_logs", indexes = {
    @Index(name = "idx_admin_access_log_accessed_at", columnList = "accessed_at,id")
})
public class AdminAccessLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "admin_user_id", nullable = false)
    private Long adminUserId;

    @Column(name = "username", nullable = false, length = 20)
    private String username;

    @Column(name = "nombres", length = 255)
    private String nombres;

    @Column(name = "apellidos", length = 255)
    private String apellidos;

    @Column(name = "email", nullable = false, length = 254)
    private String email;

    @Column(name = "admin_code_id", nullable = false)
    private Long adminCodeId;

    @Column(name = "code_owner_email", length = 254)
    private String codeOwnerEmail;

    @Column(name = "accessed_at", nullable = false)
    private Instant accessedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getAdminUserId() {
        return adminUserId;
    }

    public void setAdminUserId(Long adminUserId) {
        this.adminUserId = adminUserId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
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

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public Long getAdminCodeId() {
        return adminCodeId;
    }

    public void setAdminCodeId(Long adminCodeId) {
        this.adminCodeId = adminCodeId;
    }

    public String getCodeOwnerEmail() {
        return codeOwnerEmail;
    }

    public void setCodeOwnerEmail(String codeOwnerEmail) {
        this.codeOwnerEmail = codeOwnerEmail;
    }

    public Instant getAccessedAt() {
        return accessedAt;
    }

    public void setAccessedAt(Instant accessedAt) {
        this.accessedAt = accessedAt;
    }
}
