document.getElementById('loginForm')?.addEventListener('submit', function(e) {
    const username = document.getElementById('username')?.value;
    const password = document.getElementById('password')?.value;
    if (!username || username.trim() === '') {
        e.preventDefault();
        alert('❌ Por favor, ingresa tu nombre de usuario.');
        document.getElementById('username')?.focus();
        return false;
    }
    if (!password || password.trim() === '') {
        e.preventDefault();
        alert('❌ Por favor, ingresa tu contraseña.');
        document.getElementById('password')?.focus();
        return false;
    }
    const submitBtn = this.querySelector('button[type="submit"]');
    if (submitBtn) {
        submitBtn.disabled = true;
        submitBtn.innerHTML = '<i class="fas fa-spinner fa-spin"></i> Iniciando sesión...';
    }
});

document.addEventListener('DOMContentLoaded', function() {
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

window.addEventListener('load', function() {
    const usernameField = document.getElementById('username');
    if (usernameField && !usernameField.value) {
        usernameField.focus();
    }
});

const inputs = document.querySelectorAll('#loginForm input');
inputs.forEach(input => {
    input.addEventListener('input', () => {
        const label = document.querySelector(`label[for="${input.id}"]`);
        const icon = label ? label.querySelector('i') : null;
        if (input.checkValidity()) {
            if (label) label.style.color = 'var(--success)';
            if (icon) icon.style.color = 'var(--success)';
        } else {
            if (label) label.style.color = 'var(--gray-700)';
            if (icon) icon.style.color = 'var(--gray-700)';
        }
    });
});
