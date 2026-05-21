document.addEventListener('DOMContentLoaded', function() {
    const roleSelect = document.getElementById('role');
    const adminCodeGroup = document.getElementById('adminCodeGroup');
    const adminCodeInput = document.getElementById('adminCode');

    function toggleAdminCode() {
        if (!roleSelect || !adminCodeGroup || !adminCodeInput) return;

        if (roleSelect.value === 'ADMIN') {
            adminCodeGroup.style.display = 'block';
            adminCodeInput.required = true;
            adminCodeInput.focus();
        } else {
            adminCodeGroup.style.display = 'none';
            adminCodeInput.required = false;
            adminCodeInput.value = '';
        }
    }

    roleSelect?.addEventListener('change', toggleAdminCode);
    toggleAdminCode();

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
                alert('❌ Las contraseñas no coinciden. Por favor, verifica e intenta de nuevo.');
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
        if (username.length > 0 && (username.length < minLength || username.length > maxLength)) {
            this.style.borderColor = 'var(--danger)';
        } else {
            this.style.borderColor = 'var(--gray-200)';
        }
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
