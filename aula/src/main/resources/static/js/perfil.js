document.addEventListener('DOMContentLoaded', function() {
    const perfilForm = document.querySelector('form');
    if (!perfilForm) return;

    perfilForm.addEventListener('submit', function(e) {
        const password = document.getElementById('password').value;
        const confirmPassword = document.getElementById('confirmPassword').value;

        if (password && password !== confirmPassword) {
            e.preventDefault();
            alert('❌ Las contraseñas no coinciden. Por favor, verifica e intenta de nuevo.');
            return false;
        }

        if (password && password.length < 6) {
            e.preventDefault();
            alert('❌ La contraseña debe tener al menos 6 caracteres.');
            return false;
        }
    });
});

function confirmarEliminacion() {
    const confirmado = confirm('⚠️ ¿Estás absolutamente seguro de que deseas eliminar tu cuenta?\n\nEsta acción no se puede deshacer y perderás todos tus datos.');
    if (confirmado) {
        alert('Función en desarrollo - Contacta al administrador');
    }
}
