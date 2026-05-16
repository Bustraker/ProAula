package com.proaula.aula.Controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ModelAttribute;

import com.proaula.aula.Entity.ContactoMensaje;
import com.proaula.aula.Service.ContactoMensajeService;

@Controller
@RequestMapping("/contacto")
public class ContactoController {
    @Autowired
    private ContactoMensajeService contactoMensajeService;

    @PostMapping
    public String guardarMensaje(@ModelAttribute ContactoMensaje contactoMensaje) {
        contactoMensajeService.guardarMensaje(contactoMensaje);
        return "redirect:/index_3";
    }

    @GetMapping("/mensajes")
    public String verMensajes(Model model, @org.springframework.web.bind.annotation.RequestParam(value = "q", required = false) String q) {
        if (q != null && !q.trim().isEmpty()) {
            model.addAttribute("mensajes", contactoMensajeService.searchMensajes(q));
            model.addAttribute("busqueda", q);
        } else {
            model.addAttribute("mensajes", contactoMensajeService.getAllMensajes());
            model.addAttribute("busqueda", "");
        }
        return "Admin/lista_textos";
    }
}
