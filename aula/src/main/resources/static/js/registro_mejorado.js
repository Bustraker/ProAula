document.addEventListener('DOMContentLoaded', function() {
    const passwordInput = document.getElementById('password');
    const passwordConfirmInput = document.getElementById('passwordConfirm');
    const passwordStrength = document.querySelector('.auth-strength');
    const passwordStrengthText = document.getElementById('passwordStrengthText');
    const passwordConfirmFeedback = document.getElementById('passwordConfirmFeedback');
    const registroForm = document.getElementById('registroForm');

    function updateFieldState(field) {
        if (!field || field.type === 'checkbox') return;

        const hasValue = field.value.trim().length > 0;
        const isValid = hasValue && field.validity.valid;
        field.classList.toggle('is-valid', isValid);
        field.classList.toggle('is-invalid', !isValid);
        field.setAttribute('aria-invalid', String(!isValid));

        const label = registroForm?.querySelector(`label[for="${field.id}"]`);
        label?.classList.toggle('is-valid', isValid);
        label?.classList.toggle('is-invalid', !isValid);
    }

    function updatePasswordStrength() {
        if (!passwordInput || !passwordStrength || !passwordStrengthText) return;

        const passwordValue = passwordInput.value;
        const strengthChecks = [
            passwordValue.length >= 6,
            passwordValue.length >= 10,
            /[a-z]/i.test(passwordValue) && /\d/.test(passwordValue),
            /[^a-z\d]/i.test(passwordValue)
        ];
        const strengthLevel = strengthChecks.filter(Boolean).length;
        const strengthLabels = ['Mínimo 6 caracteres', 'Básica', 'Aceptable', 'Segura', 'Muy segura'];

        passwordStrength.dataset.level = passwordValue ? String(strengthLevel) : '0';
        passwordStrengthText.textContent = passwordValue ? strengthLabels[strengthLevel] : strengthLabels[0];
    }

    function updatePasswordConfirmation() {
        if (!passwordInput || !passwordConfirmInput || !passwordConfirmFeedback) return;

        const confirmationValue = passwordConfirmInput.value;
        const passwordsMatch = confirmationValue.length > 0
            && confirmationValue === passwordInput.value
            && passwordInput.validity.valid;
        const hasMismatch = confirmationValue.length > 0 && !passwordsMatch;
        const isMissing = !confirmationValue && passwordConfirmInput.dataset.touched === 'true';

        passwordConfirmInput.classList.toggle('is-invalid', hasMismatch || isMissing);
        passwordConfirmInput.classList.toggle('is-valid', passwordsMatch);
        passwordConfirmInput.setAttribute('aria-invalid', String(hasMismatch || isMissing));
        const confirmLabel = registroForm?.querySelector('label[for="passwordConfirm"]');
        confirmLabel?.classList.toggle('is-valid', passwordsMatch);
        confirmLabel?.classList.toggle('is-invalid', hasMismatch || isMissing);
        passwordConfirmFeedback.classList.toggle('is-valid', passwordsMatch);
        passwordConfirmFeedback.classList.toggle('is-invalid', hasMismatch || isMissing);
        passwordConfirmFeedback.textContent = passwordsMatch
            ? 'Las contraseñas coinciden.'
            : hasMismatch
                ? 'Las contraseñas todavía no coinciden.'
                : isMissing
                    ? 'Este campo es obligatorio.'
                    : 'Repite la contraseña para confirmarla.';
    }

    passwordInput?.addEventListener('input', function() {
        updateFieldState(passwordInput);
        updatePasswordStrength();
        updatePasswordConfirmation();
    });
    passwordConfirmInput?.addEventListener('input', function() {
        passwordConfirmInput.dataset.touched = 'true';
        updatePasswordConfirmation();
    });

    registroForm?.querySelectorAll('input:not([type="checkbox"]):not([type="hidden"])').forEach(function(field) {
        field.addEventListener('input', function() {
            if (field !== passwordConfirmInput && field !== passwordInput) updateFieldState(field);
        });
    });

    if (registroForm) {
        registroForm.addEventListener('submit', function(e) {
            const password = document.getElementById('password').value;
            const passwordConfirm = document.getElementById('passwordConfirm').value;
            const terminos = document.getElementById('terminos').checked;

            if (password !== passwordConfirm) {
                e.preventDefault();
                updatePasswordConfirmation();
                document.getElementById('passwordConfirm').focus();
                return false;
            }

            if (password.length < 6) {
                e.preventDefault();
                alert('❌ La contraseña debe tener al menos 6 caracteres.');
                document.getElementById('password').focus();
                return false;
            }

            if (!terminos) {
                e.preventDefault();
                alert('❌ Debes aceptar los Términos y Condiciones para continuar.');
                document.getElementById('terminos').focus();
                return false;
            }

            const submitBtn = this.querySelector('button[type="submit"]');
            if (submitBtn) {
                submitBtn.disabled = true;
                submitBtn.innerHTML = '<i class="fas fa-spinner fa-spin"></i> Creando cuenta...';
            }
        });
    }

    const card = document.querySelector('.card');
    if (card) {
        card.style.opacity = '0';
        card.style.transform = 'translateY(20px)';
        setTimeout(() => {
            card.style.transition = 'all 0.4s ease';
            card.style.opacity = '1';
            card.style.transform = 'translateY(0)';
        }, 100);
    }
});
