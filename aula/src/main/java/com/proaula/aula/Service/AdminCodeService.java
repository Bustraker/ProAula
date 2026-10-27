package com.proaula.aula.Service;

import java.util.List;
import java.util.Locale;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.proaula.aula.Entity.AdminCode;
import com.proaula.aula.Entity.AdminInviteCode;
import com.proaula.aula.Repository.AdminCodeRepository;
import com.proaula.aula.Repository.AdminInviteCodeRepository;

@Service
public class AdminCodeService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final char[] INVITE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();
    private static final int INVITE_CODE_LENGTH = 24;
    private static final int MAX_ADMIN_CODE_LENGTH = 256;
    private static final List<String> ACCESS_CODE_WORDS = List.of(
        "barco", "brisa", "campo", "canto", "carga", "cielo", "claro", "cobre",
        "coral", "costa", "delta", "duna", "farol", "faro", "fiesta", "flor",
        "fuego", "girasol", "globo", "hoja", "isla", "jardin", "lago", "lima",
        "lince", "llave", "loma", "luna", "mapa", "mar", "mate", "mesa",
        "monte", "naranja", "nube", "ola", "oliva", "palma", "pampa", "pasto",
        "piedra", "pino", "plaza", "prado", "puente", "rayo", "rio", "roble",
        "roca", "rueda", "salto", "selva", "senda", "sol", "suave", "tango",
        "tela", "tierra", "tigre", "trigo", "valle", "vela", "viento", "zorro");
    private static final char[] ACCESS_CODE_SYMBOLS = "!@#$%&?*+=".toCharArray();

    private final AdminCodeRepository adminCodeRepository;
    private final AdminInviteCodeRepository inviteCodeRepository;

    public AdminCodeService(AdminCodeRepository adminCodeRepository,
                            AdminInviteCodeRepository inviteCodeRepository) {
        this.adminCodeRepository = adminCodeRepository;
        this.inviteCodeRepository = inviteCodeRepository;
    }

    public boolean isValidAdminCode(String codigo) {
        if (codigo == null || codigo.trim().isEmpty() || codigo.length() > MAX_ADMIN_CODE_LENGTH) {
            return false;
        }
        return adminCodeRepository.existsByCodeHash(hashAdminCode(codigo));
    }

    public AdminCode findByCodigo(String codigo) {
        if (codigo == null || codigo.trim().isEmpty() || codigo.length() > MAX_ADMIN_CODE_LENGTH) {
            return null;
        }
        return adminCodeRepository.findByCodeHash(hashAdminCode(codigo)).orElse(null);
    }

    public Optional<AdminCode> findById(long id) {
        return adminCodeRepository.findById(id);
    }

    @Transactional
    public String issueAdminAccessCode(String email, long registrationRequestId) {
        if (email == null || email.isBlank() || registrationRequestId <= 0) {
            throw new IllegalArgumentException("El correo y la solicitud de registro son obligatorios.");
        }

        String code = generateAdminAccessCode();
        AdminCode adminCode = new AdminCode(hashAdminCode(code), "Código de acceso ADMIN");
        adminCode.setEmail(email.trim().toLowerCase(Locale.ROOT));
        adminCode.setRegistrationRequestId(registrationRequestId);
        adminCodeRepository.save(adminCode);
        return code;
    }

    @Transactional
    public String replaceAdminAccessCode(String email, long registrationRequestId) {
        if (email == null || email.isBlank() || registrationRequestId <= 0) {
            throw new IllegalArgumentException("El correo y la solicitud de registro son obligatorios.");
        }

        AdminCode adminCode = adminCodeRepository.findByRegistrationRequestId(registrationRequestId)
            .orElse(null);
        String code = generateAdminAccessCode();
        if (adminCode == null) {
            adminCode = new AdminCode(hashAdminCode(code), "Código de acceso ADMIN");
            adminCode.setEmail(email.trim().toLowerCase(Locale.ROOT));
            adminCode.setRegistrationRequestId(registrationRequestId);
            adminCodeRepository.save(adminCode);
            return code;
        }
        adminCode.setCodeHash(hashAdminCode(code));
        adminCode.setEmail(email.trim().toLowerCase(Locale.ROOT));
        adminCodeRepository.save(adminCode);
        return code;
    }

    @Transactional
    public void ensureDefaultAdminCodes(List<String> defaultCodes) {
        if (defaultCodes == null || defaultCodes.isEmpty()) {
            return;
        }

        for (String codigo : defaultCodes) {
            if (codigo == null || codigo.trim().isEmpty()) {
                continue;
            }
            String trimmedCode = codigo.trim();
            String codeHash = hashAdminCode(trimmedCode);
            if (!adminCodeRepository.existsByCodeHash(codeHash)) {
                AdminCode adminCode = new AdminCode(codeHash);
                adminCodeRepository.save(adminCode);
            }
        }
    }

    @Transactional
    public void migrateLegacyAdminCodes() {
        List<AdminCode> legacyCodes = adminCodeRepository.findAll().stream()
            .filter(adminCode -> !isHashedAdminCode(adminCode.getCodeHash()))
            .toList();

        for (AdminCode adminCode : legacyCodes) {
            adminCode.setCodeHash(hashAdminCode(adminCode.getCodeHash()));
        }
        if (!legacyCodes.isEmpty()) {
            adminCodeRepository.saveAll(legacyCodes);
        }
    }

    @Transactional
    public String issueRegistrationInvite(String email) {
        String code = generateInviteCode();
        AdminInviteCode invite = new AdminInviteCode();
        invite.setCodeHash(hashInviteCode(code));
        invite.setEmail(email.trim().toLowerCase(java.util.Locale.ROOT));
        invite.setExpiresAt(LocalDateTime.now().plusHours(24));
        inviteCodeRepository.save(invite);
        return code;
    }

    @Transactional
    public boolean consumeRegistrationInvite(String code, String email) {
        if (code == null || code.isBlank() || email == null || email.isBlank()) {
            return false;
        }
        AdminInviteCode invite = inviteCodeRepository
            .findByCodeHashAndEmailIgnoreCaseAndUsedAtIsNullAndExpiresAtAfter(
                hashInviteCode(code), email.trim(), LocalDateTime.now())
            .orElse(null);
        if (invite == null) {
            return false;
        }
        invite.setUsedAt(LocalDateTime.now());
        inviteCodeRepository.saveAndFlush(invite);
        return true;
    }

    private String generateInviteCode() {
        char[] code = new char[INVITE_CODE_LENGTH];
        for (int index = 0; index < code.length; index++) {
            code[index] = INVITE_ALPHABET[SECURE_RANDOM.nextInt(INVITE_ALPHABET.length)];
        }
        return new String(code);
    }

    private String generateAdminAccessCode() {
        StringBuilder code = new StringBuilder();
        for (int index = 0; index < 5; index++) {
            if (index > 0) {
                code.append('-');
            }
            String word = ACCESS_CODE_WORDS.get(SECURE_RANDOM.nextInt(ACCESS_CODE_WORDS.size()));
            code.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        code.append('-');
        code.append(String.format(Locale.ROOT, "%04d", SECURE_RANDOM.nextInt(10_000)));
        code.append(ACCESS_CODE_SYMBOLS[SECURE_RANDOM.nextInt(ACCESS_CODE_SYMBOLS.length)]);
        code.append(ACCESS_CODE_SYMBOLS[SECURE_RANDOM.nextInt(ACCESS_CODE_SYMBOLS.length)]);
        return code.toString();
    }

    private String hashCode(String code) {
        try {
            return HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(code.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    private String hashAdminCode(String code) {
        return "sha256$" + hashCode(code.trim().toUpperCase(Locale.ROOT));
    }

    private String hashInviteCode(String code) {
        return hashCode(code.trim().toUpperCase(Locale.ROOT));
    }

    private boolean isHashedAdminCode(String code) {
        return code != null && code.matches("sha256\\$[0-9a-f]{64}");
    }
}
