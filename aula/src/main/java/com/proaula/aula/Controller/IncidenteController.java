package com.proaula.aula.Controller;

import com.proaula.aula.Entity.Incidente;
import com.proaula.aula.Service.IncidenteService;
import com.proaula.aula.dto.IncidenteReportRequest;
import com.proaula.aula.exception.AulaException;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/incidentes")
public class IncidenteController {

    private static final Logger logger = LoggerFactory.getLogger(IncidenteController.class);
    private static final String FORM_VIEW = "Usuario/incidente-form";

    private final IncidenteService incidenteService;

    public IncidenteController(IncidenteService incidenteService) {
        this.incidenteService = incidenteService;
    }

    @GetMapping("/panel")
    public String mostrarPanel() {
        return "incidentes";
    }

    @GetMapping("/nuevo")
    public String mostrarFormulario(Model model) {
        if (!model.containsAttribute("incidenteForm")) {
            model.addAttribute("incidenteForm", new IncidenteReportRequest());
        }
        cargarOpciones(model);
        return FORM_VIEW;
    }

    @PostMapping
    public String reportar(@Valid @ModelAttribute("incidenteForm") IncidenteReportRequest solicitud,
                           BindingResult bindingResult,
                           Authentication authentication,
                           Model model,
                           RedirectAttributes redirectAttributes,
                           jakarta.servlet.http.HttpServletResponse response) {
        if (bindingResult.hasErrors()) {
            cargarOpciones(model);
            return FORM_VIEW;
        }

        try {
            Incidente incidente = incidenteService.reportar(solicitud, authentication.getName());
            redirectAttributes.addFlashAttribute(
                    "success", "Incidente #" + incidente.getId() + " registrado correctamente.");
            return "redirect:/incidentes/nuevo";
        } catch (AulaException ex) {
            bindingResult.rejectValue("rutaId", "ruta.invalida", ex.getMessage());
            response.setStatus(HttpStatus.BAD_REQUEST.value());
            cargarOpciones(model);
            return FORM_VIEW;
        } catch (DataAccessException ex) {
            logger.error("No se pudo guardar el reporte de incidente.", ex);
            model.addAttribute("error",
                    "No se pudo registrar el incidente. Revisa la conexión e inténtalo de nuevo.");
            response.setStatus(HttpStatus.INTERNAL_SERVER_ERROR.value());
            cargarOpciones(model);
            return FORM_VIEW;
        }
    }

    private void cargarOpciones(Model model) {
        model.addAttribute("rutas", incidenteService.getRutas());
        model.addAttribute("tiposIncidente", IncidenteReportRequest.TipoIncidente.values());
    }
}
