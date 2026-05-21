package com.proaula.aula.dto;

import lombok.Data;

@Data
public class ContactoMensajeDto {
    private String nombre;
    private String apellido;
    private String telefono;
    private String email;
    private String mensaje;
}
