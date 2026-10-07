function updateDashboardDate() {
    const date = document.getElementById('dashboardDate');
    if (!date) return;

    date.textContent = new Intl.DateTimeFormat('es-CO', {
        weekday: 'long',
        day: 'numeric',
        month: 'long',
        year: 'numeric'
    }).format(new Date());
    date.dateTime = new Date().toISOString();
}

function animateDashboardNumber(element) {
    if (element.dataset.counted === 'true') return;

    const target = Number(element.textContent.trim());
    if (!Number.isFinite(target) || target < 0) return;

    element.dataset.counted = 'true';
    const duration = 1100;
    const startTime = performance.now();

    function updateNumber(now) {
        const progress = Math.min((now - startTime) / duration, 1);
        const easedProgress = 1 - Math.pow(1 - progress, 4);
        element.textContent = Math.round(target * easedProgress).toLocaleString('es-CO');

        if (progress < 1) {
            requestAnimationFrame(updateNumber);
        }
    }

    requestAnimationFrame(updateNumber);
}

function initializeDashboardMotion(root = document) {
    const motionReduced = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
    const revealElements = root.querySelectorAll(
        '.dashboard-welcome, .stat-card, .card, .activity-item, .quick-action'
    );

    revealElements.forEach((element, index) => {
        element.classList.add('dashboard-reveal');
        element.style.setProperty('--reveal-order', index % 8);
    });

    if (motionReduced || !('IntersectionObserver' in window)) {
        revealElements.forEach(element => element.classList.add('is-visible'));
        root.querySelectorAll('.stat-info h3').forEach(element => {
            element.dataset.counted = 'true';
        });
        return;
    }

    const revealObserver = new IntersectionObserver((entries, observer) => {
        entries.forEach(entry => {
            if (!entry.isIntersecting) return;

            entry.target.classList.add('is-visible');
            const value = entry.target.querySelector('.stat-info h3');
            if (value) animateDashboardNumber(value);
            observer.unobserve(entry.target);
        });
    }, { threshold: 0.16, rootMargin: '0px 0px -24px 0px' });

    revealElements.forEach(element => revealObserver.observe(element));

    root.querySelectorAll('.stat-card').forEach(card => {
        card.addEventListener('pointermove', event => {
            if (event.pointerType !== 'mouse') return;

            const bounds = card.getBoundingClientRect();
            const x = (event.clientX - bounds.left) / bounds.width;
            const y = (event.clientY - bounds.top) / bounds.height;
            card.style.setProperty('--pointer-x', `${x * 100}%`);
            card.style.setProperty('--pointer-y', `${y * 100}%`);
            card.style.setProperty('--tilt-x', `${(x - 0.5) * 3}deg`);
            card.style.setProperty('--tilt-y', `${(0.5 - y) * 3}deg`);
        });

        card.addEventListener('pointerleave', () => {
            card.style.setProperty('--tilt-x', '0deg');
            card.style.setProperty('--tilt-y', '0deg');
        });
    });
}

async function refreshDashboard(button, status) {
    const statusLabel = status.querySelector('.refresh-status-label');
    const statusIcon = status.querySelector('i');
    button.disabled = true;
    status.classList.remove('is-error');
    status.classList.add('loading');
    statusLabel.textContent = 'Actualizando...';
    statusIcon.className = 'fas fa-circle-notch fa-spin';

    try {
        const response = await fetch(window.location.href, {
            headers: { 'X-Requested-With': 'XMLHttpRequest' }
        });
        if (!response.ok) {
            throw new Error(`La solicitud terminó con estado ${response.status}`);
        }

        const html = await response.text();
        const refreshedDocument = new DOMParser().parseFromString(html, 'text/html');
        const refreshedContent = refreshedDocument.querySelector('.content-wrapper');
        const currentContent = document.querySelector('.content-wrapper');
        if (!refreshedContent || !currentContent) {
            throw new Error('No se encontró el contenido del panel para actualizar');
        }

        currentContent.replaceWith(refreshedContent);
        updateDashboardDate();
        initializeDashboardMotion(refreshedContent);
        statusLabel.textContent = 'Al día';
        statusIcon.className = 'fas fa-check-circle';
    } catch (error) {
        console.error('Error al actualizar el dashboard:', error);
        status.classList.add('is-error');
        statusLabel.textContent = 'No se pudo actualizar';
        statusIcon.className = 'fas fa-exclamation-circle';
    } finally {
        status.classList.remove('loading');
        button.disabled = false;
    }
}

window.addEventListener('DOMContentLoaded', function() {
    updateDashboardDate();
    initializeDashboardMotion();

    const refreshBtn = document.getElementById('refreshDashboardBtn');
    const status = document.getElementById('refreshStatus');
    if (refreshBtn && status) {
        refreshBtn.addEventListener('click', () => refreshDashboard(refreshBtn, status));
    }
});
