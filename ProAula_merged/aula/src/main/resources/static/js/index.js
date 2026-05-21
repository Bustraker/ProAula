const modal = document.getElementById('modalAdmin');
const openBtn = document.getElementById('LoginAdminBtn');
const closeBtn = document.getElementById('closeModalAdmin');
const continueBtn = document.getElementById('continueAdmin');

if (openBtn && modal) {
    openBtn.addEventListener('click', function() {
        modal.classList.add('active');
    });
}

if (closeBtn && modal) {
    closeBtn.addEventListener('click', function() {
        modal.classList.remove('active');
    });
}

window.addEventListener('click', function(event) {
    if (event.target === modal) {
        modal.classList.remove('active');
    }
});

if (continueBtn) {
    continueBtn.addEventListener('click', function() {
        window.location.href = '/admin-login';
    });
}

document.addEventListener('DOMContentLoaded', function() {
    const alerts = document.querySelectorAll('.alert');
    alerts.forEach(alert => {
        setTimeout(() => {
            alert.style.transition = 'opacity 0.3s ease';
            alert.style.opacity = '0';
            setTimeout(() => alert.remove(), 300);
        }, 5000);
    });
});
