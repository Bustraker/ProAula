document.addEventListener('DOMContentLoaded', function() {
    const toggleButton = document.querySelector('.js-toggle-vista');
    const toggleIcon = toggleButton?.querySelector('i');
    const toggleLabel = toggleButton?.querySelector('span');

    if (toggleButton && toggleIcon && toggleLabel) {
        toggleButton.addEventListener('click', () => {
            const grid = document.getElementById('rutasGrid');
            if (!grid) {
                return;
            }

            const isListView = grid.classList.toggle('ruta-grid--list');
            toggleButton.setAttribute('aria-pressed', String(isListView));
            toggleIcon.classList.toggle('fa-list', !isListView);
            toggleIcon.classList.toggle('fa-grip', isListView);
            toggleLabel.textContent = isListView ? 'Vista de cuadrícula' : 'Vista de lista';
        });
    }
});
