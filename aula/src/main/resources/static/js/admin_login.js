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
            } else {
                if (icon) {
                    icon.style.color = 'var(--error-color)';
                }
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
            if (passwordInput.type === 'password') {
                passwordInput.type = 'text';
                this.classList.remove('fa-eye');
                this.classList.add('fa-eye-slash');
                this.style.opacity = '0.6';
            } else {
                passwordInput.type = 'password';
                this.classList.remove('fa-eye-slash');
                this.classList.add('fa-eye');
                this.style.opacity = '1';
            }
        });
    });
});
