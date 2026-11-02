const modal = document.getElementById('modalAdmin');
const gate = document.getElementById('adminAccessGate');
const gateTrigger = document.getElementById('openAdminGate');
const openBtn = document.getElementById('LoginAdminBtn');
const closeBtn = document.getElementById('closeModalAdmin');
const continueBtn = document.getElementById('continueAdmin');
let handingOffToAdminModal = false;

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

if (gateTrigger && gate) {
    gateTrigger.addEventListener('click', function() {
        if (!gate.open) {
            gate.showModal();
        }
    });
}

if (openBtn && modal && gate) {
    openBtn.addEventListener('click', function() {
        handingOffToAdminModal = true;
        gate.close();
        setModalOpen(true);
    });
}

if (gate && gateTrigger) {
    gate.addEventListener('close', function() {
        if (!handingOffToAdminModal) {
            gateTrigger.focus();
        }
        handingOffToAdminModal = false;
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
        gateTrigger?.focus();
    });
}

if (continueBtn) {
    continueBtn.addEventListener('click', function() {
        const loginUrl = continueBtn.dataset.adminLoginUrl;
        if (loginUrl) {
            window.location.assign(loginUrl);
        }
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
