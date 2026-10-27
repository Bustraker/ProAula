package com.proaula.aula.Entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

@Entity
@Table(name = "admin_codes", indexes = {
    @Index(name = "idx_admin_code_email", columnList = "email"),
    @Index(name = "uk_admin_code_registration_request", columnList = "registration_request_id", unique = true)
})
public class AdminCode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "codigo", nullable = false, unique = true, length = 255)
    private String codeHash;

    @Column(name = "descripcion")
    private String descripcion;

    @Column(name = "email", length = 254)
    private String email;

    @Column(name = "registration_request_id")
    private Long registrationRequestId;

    public AdminCode() {
    }

    public AdminCode(String codeHash) {
        this.codeHash = codeHash;
    }

    public AdminCode(String codeHash, String descripcion) {
        this.codeHash = codeHash;
        this.descripcion = descripcion;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getCodeHash() {
        return codeHash;
    }

    public void setCodeHash(String codeHash) {
        this.codeHash = codeHash;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public void setDescripcion(String descripcion) {
        this.descripcion = descripcion;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public Long getRegistrationRequestId() {
        return registrationRequestId;
    }

    public void setRegistrationRequestId(Long registrationRequestId) {
        this.registrationRequestId = registrationRequestId;
    }
}
