const modal = document.getElementById('modalAdmin');
const openBtn = document.getElementById('LoginAdminBtn');
const closeBtn = document.getElementById('closeModalAdmin');
const continueBtn = document.getElementById('continueAdmin');

function setModalOpen(isOpen) {
    if (!modal) return;
    if (isOpen && !modal.open) {
        modal.showModal();
    } else if (!isOpen && modal.open) {
        modal.close();
    }
    if (isOpen && continueBtn) {
        continueBtn.focus();
    }
}

if (openBtn && modal) {
    openBtn.addEventListener('click', function() {
        setModalOpen(true);
    });
}

if (closeBtn && modal) {
    closeBtn.addEventListener('click', function() {
        setModalOpen(false);
    });
}

window.addEventListener('click', function(event) {
    if (event.target === modal && modal.open) {
        setModalOpen(false);
    }
});

window.addEventListener('keydown', function(event) {
    if (event.key === 'Escape' && modal?.open) {
        setModalOpen(false);
    }
});

if (modal && openBtn) {
    modal.addEventListener('close', function() {
        openBtn.focus();
    });
}

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
