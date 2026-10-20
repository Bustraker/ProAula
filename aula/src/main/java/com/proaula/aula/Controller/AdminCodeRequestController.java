package com.proaula.aula.Controller;

import com.proaula.aula.Entity.AdminCodeRequest;
import com.proaula.aula.Service.AdminCodeRequestService;
import com.proaula.aula.Service.AdminPushNotificationService;
import com.proaula.aula.Service.UsuarioService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.security.Principal;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Controller
public class AdminCodeRequestController {

    private static final Logger log = LoggerFactory.getLogger(AdminCodeRequestController.class);

    private final AdminCodeRequestService requestService;
    private final AdminPushNotificationService pushNotificationService;
    private final UsuarioService usuarioService;
    private final String approverEmail;

    public AdminCodeRequestController(
            AdminCodeRequestService requestService,
            AdminPushNotificationService pushNotificationService,
            UsuarioService usuarioService,
            @Value("${app.admin.approver-email:}") String approverEmail) {
        this.requestService = requestService;
        this.pushNotificationService = pushNotificationService;
        this.usuarioService = usuarioService;
        this.approverEmail = approverEmail;
    }

    @PostMapping("/admin/solicitudes-codigo")
    public String submitRequest(@RequestParam(required = false) String nombres,
                                @RequestParam(required = false) String apellidos,
                                @RequestParam(required = false) String email,
                                @RequestParam(required = false) String motivo,
                                Principal principal,
                                RedirectAttributes redirectAttributes) {
        try {
            boolean pushSent = requestService.createRequest(
                nombres, apellidos, email, motivo, principal == null ? null : principal.getName());
            redirectAttributes.addFlashAttribute("success",
                "Solicitud enviada al responsable de aprobación.");
            if (!pushSent) {
                redirectAttributes.addFlashAttribute("warning",
                    "No se pudo entregar la notificación push. El responsable recibió un correo y puede revisar "
                        + "la solicitud en el panel.");
            }
        } catch (IllegalArgumentException | IllegalStateException exception) {
            if (exception instanceof IllegalArgumentException) {
                redirectAttributes.addFlashAttribute("error", exception.getMessage());
            } else {
                log.error("Could not create an ADMIN registration request.", exception);
                redirectAttributes.addFlashAttribute("error",
                    "No se pudo enviar la solicitud. Verifica la configuración del correo e inténtalo de nuevo.");
            }
        }
        return "redirect:/gestionar-usuarios";
    }

    @GetMapping("/admin/solicitudes-codigo")
    public String adminRequests(Model model, Principal principal) {
        requireApprover(principal);
        List<AdminCodeRequest> requests = requestService.findAll();
        model.addAttribute("requests", requests);
        model.addAttribute("pendingCount", requests.stream()
            .filter(request -> request.getStatus() == AdminCodeRequest.Status.PENDING).count());
        model.addAttribute("pushConfigured", pushNotificationService.isPushConfigured());
        return "Admin/solicitudes_codigo";
    }

    @PostMapping("/admin/solicitudes-codigo/{id}/aprobar")
    public String approve(@PathVariable long id, Principal principal, RedirectAttributes redirectAttributes) {
        requireApprover(principal);
        try {
            String code = requestService.approve(id, principal.getName());
            redirectAttributes.addFlashAttribute("issuedCode", code);
            redirectAttributes.addFlashAttribute("issuedCodeRequestId", id);
            redirectAttributes.addFlashAttribute("success",
                "Solicitud aprobada. El código quedó guardado como hash; revísalo y envíalo al candidato si corresponde.");
        } catch (IllegalArgumentException | IllegalStateException exception) {
            if (exception instanceof IllegalArgumentException) {
                redirectAttributes.addFlashAttribute("error", exception.getMessage());
            } else {
                log.error("Could not approve ADMIN access request {}.", id, exception);
                redirectAttributes.addFlashAttribute("error",
                    "No se pudo completar la aprobación. No se confirmó el envío del código.");
            }
        }
        return "redirect:/admin/solicitudes-codigo";
    }

    @PostMapping("/admin/solicitudes-codigo/{id}/nuevo-codigo")
    public String replaceCode(@PathVariable long id, Principal principal, RedirectAttributes redirectAttributes) {
        requireApprover(principal);
        try {
            String code = requestService.replaceApprovedCode(id);
            redirectAttributes.addFlashAttribute("issuedCode", code);
            redirectAttributes.addFlashAttribute("issuedCodeRequestId", id);
            redirectAttributes.addFlashAttribute("success",
                "Se generó un nuevo código y se guardó como hash. El código anterior quedó invalidado.");
        } catch (IllegalArgumentException | IllegalStateException exception) {
            redirectAttributes.addFlashAttribute("error", exception.getMessage());
        }
        return "redirect:/admin/solicitudes-codigo";
    }

    @PostMapping("/admin/solicitudes-codigo/{id}/enviar-codigo")
    public String sendApprovedCode(@PathVariable long id,
                                   @RequestParam String codigoAdmin,
                                   Principal principal,
                                   RedirectAttributes redirectAttributes) {
        requireApprover(principal);
        try {
            requestService.sendApprovedCode(id, codigoAdmin, principal.getName());
            redirectAttributes.addFlashAttribute("success", "El código fue enviado al correo del candidato.");
        } catch (IllegalArgumentException | IllegalStateException exception) {
            log.error("Could not deliver ADMIN access code for request {}.", id, exception);
            redirectAttributes.addFlashAttribute("error",
                "No se pudo enviar el código. El código aprobado sigue disponible para reintentar o generar uno nuevo.");
        }
        redirectAttributes.addFlashAttribute("issuedCode", codigoAdmin);
        redirectAttributes.addFlashAttribute("issuedCodeRequestId", id);
        return "redirect:/admin/solicitudes-codigo";
    }

    @PostMapping("/admin/solicitudes-codigo/{id}/rechazar")
    public String reject(@PathVariable long id, Principal principal, RedirectAttributes redirectAttributes) {
        requireApprover(principal);
        try {
            requestService.reject(id, principal.getName());
            redirectAttributes.addFlashAttribute("success", "Solicitud rechazada y respuesta enviada por correo.");
        } catch (IllegalArgumentException | IllegalStateException exception) {
            if (exception instanceof IllegalArgumentException) {
                redirectAttributes.addFlashAttribute("error", exception.getMessage());
            } else {
                log.error("Could not reject ADMIN access request {}.", id, exception);
                redirectAttributes.addFlashAttribute("error",
                    "No se pudo completar el rechazo. Inténtalo de nuevo.");
            }
        }
        return "redirect:/admin/solicitudes-codigo";
    }

    @PostMapping("/api/admin/push-subscriptions")
    @ResponseBody
    public ResponseEntity<Void> registerPushToken(Principal principal, @Valid @RequestBody PushTokenRequest request) {
        requireApprover(principal);
        pushNotificationService.registerDevice(approverEmail, request.token());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/api/admin/push-subscriptions")
    @ResponseBody
    public ResponseEntity<Void> removePushToken(Principal principal, @Valid @RequestBody PushTokenRequest request) {
        requireApprover(principal);
        pushNotificationService.removeDevice(approverEmail, request.token());
        return ResponseEntity.noContent().build();
    }

    private void requireApprover(Principal principal) {
        if (principal == null || !isApproverAccount(principal.getName())) {
            throw new AccessDeniedException("Only the configured ADMIN approver may manage invitations.");
        }
    }

    private boolean isApproverAccount(String username) {
        var user = usuarioService.findByUsername(username);
        return usuarioService.isAdminUser(username) && user != null
            && user.getEmail() != null && user.getEmail().equalsIgnoreCase(approverEmail);
    }

    public record PushTokenRequest(@NotBlank @Size(max = 4096) String token) {
    }
}
