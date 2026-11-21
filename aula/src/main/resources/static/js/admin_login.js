function togglePassword() {
    const passwordInput = document.getElementById('password');
    const toggleIcon = document.querySelector('.password-toggle');
    if (!passwordInput || !toggleIcon) return;

    if (passwordInput.type === 'password') {
        passwordInput.type = 'text';
        toggleIcon.classList.remove('fa-eye');
        toggleIcon.classList.add('fa-eye-slash');
        toggleIcon.style.opacity = '0.6';
    } else {
        passwordInput.type = 'password';
        toggleIcon.classList.remove('fa-eye-slash');
        toggleIcon.classList.add('fa-eye');
        toggleIcon.style.opacity = '1';
    }
}

document.addEventListener('DOMContentLoaded', function() {
    const codeInput = document.getElementById('codigoAdmin');
    if (codeInput) {
        codeInput.focus();
    }

    const step1 = document.getElementById('step1');
    const step2 = document.getElementById('step2');
    if (step1 && step2) {
        if (step1.classList.contains('completed')) {
            step1.classList.add('completed');
            step2.classList.add('active');
        } else {
            step1.classList.add('active');
        }
    }

    const inputs = document.querySelectorAll('input');
    inputs.forEach(input => {
        input.addEventListener('input', () => {
            const label = document.querySelector(`label[for="${input.id}"]`);
            const icon = label ? label.querySelector('i') : null;
            if (input.checkValidity()) {
                if (icon) {
                    icon.style.color = 'var(--success-color)';
                }
            } else if (icon) {
                icon.style.color = 'var(--error-color)';
            }
        });
    });
    // Delegate password toggle for icons with .js-toggle-password
    document.querySelectorAll('.js-toggle-password').forEach(icon => {
        icon.addEventListener('click', function(e) {
            e.preventDefault();
            const targetSelector = this.dataset.target || '#password';
            const passwordInput = document.querySelector(targetSelector);
            if (!passwordInput) return;
            const eyeIcon = this.querySelector('i') || this;
            if (passwordInput.type === 'password') {
                passwordInput.type = 'text';
                eyeIcon.classList.remove('fa-eye');
                eyeIcon.classList.add('fa-eye-slash');
                this.setAttribute('aria-label', 'Ocultar contraseña');
                this.setAttribute('aria-pressed', 'true');
                this.setAttribute('title', 'Ocultar contraseña');
            } else {
                passwordInput.type = 'password';
                eyeIcon.classList.remove('fa-eye-slash');
                eyeIcon.classList.add('fa-eye');
                this.setAttribute('aria-label', 'Mostrar contraseña');
                this.setAttribute('aria-pressed', 'false');
                this.setAttribute('title', 'Mostrar contraseña');
            }
        });
    });

    document.querySelectorAll('.js-toggle-code').forEach(button => {
        button.addEventListener('click', function(e) {
            e.preventDefault();
            const input = document.querySelector(this.dataset.target || '#codigoAdmin');
            if (!input) return;
            const eyeIcon = this.querySelector('i') || this;
            const showing = input.type === 'text';
            input.type = showing ? 'password' : 'text';
            eyeIcon.classList.toggle('fa-eye', showing);
            eyeIcon.classList.toggle('fa-eye-slash', !showing);
            this.setAttribute('aria-label', showing ? 'Mostrar código' : 'Ocultar código');
            this.setAttribute('aria-pressed', showing ? 'false' : 'true');
        });
    });

    const newPassword = document.getElementById('newPassword');
    const strength = document.getElementById('passwordStrength');
    if (newPassword && strength) {
        newPassword.addEventListener('input', () => {
            const value = newPassword.value;
            let score = 0;
            if (value.length >= 12) score++;
            if (/[A-Z]/.test(value) && /[a-z]/.test(value)) score++;
            if (/\d/.test(value)) score++;
            if (/[^A-Za-z0-9]/.test(value)) score++;
            strength.dataset.score = String(score);
            strength.querySelector('small').textContent =
                score >= 4 ? 'Contraseña fuerte.' : 'Usa 12 caracteres con mayúscula, número y símbolo.';
        });
    }
});
