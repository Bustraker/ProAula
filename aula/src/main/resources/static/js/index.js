const modal = document.getElementById('modalAdmin');
const gate = document.getElementById('adminAccessGate');
const gateTrigger = document.getElementById('openAdminGate');
const openBtn = document.getElementById('LoginAdminBtn');
const closeBtn = document.getElementById('closeModalAdmin');
const continueBtn = document.getElementById('continueAdmin');
let handingOffToAdminModal = false;

const landingFrame = document.querySelector('.landing-frame');
const prefersReducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)');

if (landingFrame && !prefersReducedMotion.matches) {
    let pointerPosition = null;
    let pointerFrame = 0;

    landingFrame.addEventListener('pointermove', function(event) {
        if (event.pointerType === 'touch' || prefersReducedMotion.matches) return;

        pointerPosition = { x: event.clientX, y: event.clientY };
        if (pointerFrame) return;

        pointerFrame = window.requestAnimationFrame(function() {
            pointerFrame = 0;
            if (!pointerPosition || prefersReducedMotion.matches) return;

            const bounds = landingFrame.getBoundingClientRect();
            const pointerX = Math.min(1, Math.max(0, (pointerPosition.x - bounds.left) / bounds.width));
            const pointerY = Math.min(1, Math.max(0, (pointerPosition.y - bounds.top) / bounds.height));
            const centerDistance = Math.hypot(pointerX - 0.5, pointerY - 0.5);
            const lift = Math.max(0, 1 - centerDistance * 1.8) * -7.2;

            landingFrame.style.setProperty('--landing-tilt-x', `${((0.5 - pointerY) * 5.6).toFixed(2)}deg`);
            landingFrame.style.setProperty('--landing-tilt-y', `${((pointerX - 0.5) * 6.4).toFixed(2)}deg`);
            landingFrame.style.setProperty('--landing-lift', `${lift.toFixed(2)}px`);
            landingFrame.style.setProperty('--landing-shadow-x', `${((0.5 - pointerX) * 20).toFixed(2)}px`);
            landingFrame.style.setProperty('--landing-shadow-y', `${((0.5 - pointerY) * 18).toFixed(2)}px`);
        });
    });

    landingFrame.addEventListener('pointerleave', function() {
        pointerPosition = null;
        if (pointerFrame) {
            window.cancelAnimationFrame(pointerFrame);
            pointerFrame = 0;
        }
        landingFrame.style.setProperty('--landing-tilt-x', '0deg');
        landingFrame.style.setProperty('--landing-tilt-y', '0deg');
        landingFrame.style.setProperty('--landing-lift', '0px');
        landingFrame.style.setProperty('--landing-shadow-x', '0px');
        landingFrame.style.setProperty('--landing-shadow-y', '0px');
    });
}

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
