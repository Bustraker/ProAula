package com.proaula.aula.Service;

import com.proaula.aula.Entity.ContactoMensaje;
import com.proaula.aula.Repository.ContactoMensajeRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ContactoMensajeService {
    @Autowired
    private ContactoMensajeRepository contactoMensajeRepository;

    public ContactoMensaje guardarMensaje(ContactoMensaje mensaje) {
        return contactoMensajeRepository.save(mensaje);
    }

    public List<ContactoMensaje> getAllMensajes() {
        return contactoMensajeRepository.findAll();
    }

    public ContactoMensaje getMensajeById(Long id) {
        return contactoMensajeRepository.findById(id).orElse(null);
    }

    public void deleteMensaje(Long id) {
        contactoMensajeRepository.deleteById(id);
    }

    public List<ContactoMensaje> searchMensajes(String q) {
        return contactoMensajeRepository.findByNameContainingIgnoreCaseOrEmailContainingIgnoreCaseOrPhoneContainingIgnoreCaseOrMessageContainingIgnoreCase(q);
    }
    
    public long count() {
        return contactoMensajeRepository.count();
    }
}