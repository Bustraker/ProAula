document.addEventListener('DOMContentLoaded', function () {
    const passwordForm = document.querySelector('.profile-password-form');
    const newPassword = document.getElementById('passwordNuevo');
    const confirmation = document.getElementById('passwordConfirmar');
    const matchMessage = document.getElementById('passwordMatch');

    if (!passwordForm || !newPassword || !confirmation || !matchMessage) {
        return;
    }

    const validateConfirmation = function () {
        if (!confirmation.value) {
            confirmation.setCustomValidity('');
            matchMessage.textContent = '';
            matchMessage.classList.remove('is-match', 'is-mismatch');
            return true;
        }

        const matches = newPassword.value === confirmation.value;
        confirmation.setCustomValidity(matches ? '' : 'Las contraseñas no coinciden.');
        matchMessage.textContent = matches ? 'Las contraseñas coinciden.' : 'Las contraseñas no coinciden.';
        matchMessage.classList.toggle('is-match', matches);
        matchMessage.classList.toggle('is-mismatch', !matches);
        return matches;
    };

    newPassword.addEventListener('input', validateConfirmation);
    confirmation.addEventListener('input', validateConfirmation);
    passwordForm.addEventListener('submit', function (event) {
        if (!validateConfirmation()) {
            event.preventDefault();
            confirmation.reportValidity();
        }
    });
});
