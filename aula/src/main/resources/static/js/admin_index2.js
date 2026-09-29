function refreshDashboard() {
    const status = document.getElementById('refreshStatus');
    if (!status) return;

    status.classList.add('loading');
    status.innerHTML = '<span class="spinner"></span> Actualizando...';

    fetch(window.location.href)
        .then(response => response.text())
        .then(html => {
            const parser = new DOMParser();
            const newDoc = parser.parseFromString(html, 'text/html');
            const newContent = newDoc.querySelector('main');
            if (newContent) {
                const existingMain = document.querySelector('main');
                existingMain?.replaceWith(newContent);
            }
        })
        .catch(error => {
            console.error('Error al actualizar el dashboard:', error);
        })
        .finally(() => {
            status.classList.remove('loading');
            if (status) {
                status.innerHTML = 'Última actualización: ahora';
            }
        });
}

window.addEventListener('load', function() {
    const refreshBtn = document.getElementById('refreshDashboardBtn');
    if (refreshBtn) {
        refreshBtn.addEventListener('click', refreshDashboard);
    }
});
