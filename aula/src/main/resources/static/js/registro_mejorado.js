document.addEventListener('DOMContentLoaded', function() {
    const roleSelect = document.getElementById('role');
    const adminCodeGroup = document.getElementById('adminCodeGroup');
    const adminCodeInput = document.getElementById('adminCode');
    const passwordInput = document.getElementById('password');
    const passwordConfirmInput = document.getElementById('passwordConfirm');
    const passwordStrength = document.querySelector('.auth-strength');
    const passwordStrengthText = document.getElementById('passwordStrengthText');
    const passwordConfirmFeedback = document.getElementById('passwordConfirmFeedback');

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
        const passwordsMatch = confirmationValue.length > 0 && confirmationValue === passwordInput.value;
        const hasMismatch = confirmationValue.length > 0 && !passwordsMatch;

        passwordConfirmInput.classList.toggle('is-valid', passwordsMatch);
        passwordConfirmInput.classList.toggle('is-invalid', hasMismatch);
        passwordConfirmInput.setAttribute('aria-invalid', String(hasMismatch));
        passwordConfirmFeedback.classList.toggle('is-valid', passwordsMatch);
        passwordConfirmFeedback.classList.toggle('is-invalid', hasMismatch);
        passwordConfirmFeedback.textContent = passwordsMatch
            ? 'Las contraseñas coinciden.'
            : hasMismatch
                ? 'Las contraseñas todavía no coinciden.'
                : 'Repite la contraseña para confirmarla.';
    }

    function toggleAdminCode() {
        if (!roleSelect || !adminCodeGroup || !adminCodeInput) return;

        if (roleSelect.value === 'ADMIN') {
            adminCodeGroup.classList.remove('hidden');
            adminCodeGroup.style.display = 'block';
            adminCodeInput.required = true;
            adminCodeInput.focus();
        } else {
            adminCodeGroup.classList.add('hidden');
            adminCodeGroup.style.display = 'none';
            adminCodeInput.required = false;
            adminCodeInput.value = '';
        }
    }

    roleSelect?.addEventListener('change', toggleAdminCode);
    toggleAdminCode();

    passwordInput?.addEventListener('input', function() {
        updatePasswordStrength();
        updatePasswordConfirmation();
    });
    passwordConfirmInput?.addEventListener('input', updatePasswordConfirmation);

    const registroForm = document.getElementById('registroForm');
    if (registroForm) {
        registroForm.addEventListener('submit', function(e) {
            const password = document.getElementById('password').value;
            const passwordConfirm = document.getElementById('passwordConfirm').value;
            const terminos = document.getElementById('terminos').checked;
            const role = document.getElementById('role').value;
            const adminCode = document.getElementById('adminCode').value;

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

            if (role === 'ADMIN' && adminCode.trim() === '') {
                e.preventDefault();
                alert('❌ Debes ingresar el código de administrador para crear una cuenta de administrador.');
                document.getElementById('adminCode').focus();
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

    document.getElementById('username')?.addEventListener('input', function() {
        const username = this.value;
        const minLength = 4;
        const maxLength = 20;
        const hasValue = username.length > 0;
        const isInvalid = hasValue && (username.length < minLength || username.length > maxLength);
        this.classList.toggle('is-invalid', isInvalid);
        this.classList.toggle('is-valid', hasValue && !isInvalid);
        this.setAttribute('aria-invalid', String(isInvalid));
    });

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
