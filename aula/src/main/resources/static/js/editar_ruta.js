(() => {
    const routeSelect = document.querySelector('.js-route-select');
    const orderedNeighborhoods = document.getElementById('barriosOrdenadosTexto');
    const previewStops = document.getElementById('routePreviewStops');
    const stopCount = document.getElementById('routeStopCount');
    const previewSummary = document.getElementById('routePreviewSummary');
    const mapStopCount = document.getElementById('routeMapStopCount');
    const verifiedCheckbox = document.getElementById('verificada');
    const verificationState = document.querySelector('.route-editor-state');

    if (routeSelect) {
        routeSelect.addEventListener('change', () => {
            const routeId = routeSelect.value;
            if (/^\d+$/.test(routeId)) {
                window.location.assign(`/editar-ruta/${routeId}`);
            }
        });
    }

    if (orderedNeighborhoods && previewStops && stopCount && previewSummary && mapStopCount) {
        const renderPreview = () => {
            const neighborhoods = orderedNeighborhoods.value
                .split(',')
                .map((neighborhood) => neighborhood.trim())
                .filter(Boolean);

            stopCount.textContent = String(neighborhoods.length);
            previewSummary.textContent = String(neighborhoods.length);
            mapStopCount.textContent = neighborhoods.length === 0
                ? 'SECUENCIA SIN DEFINIR'
                : `${neighborhoods.length} ${neighborhoods.length === 1 ? 'PARADA' : 'PARADAS'} DEFINIDAS`;
            previewStops.replaceChildren();

            if (neighborhoods.length === 0) {
                const empty = document.createElement('li');
                empty.className = 'route-preview-empty';

                const icon = document.createElement('i');
                icon.className = 'fas fa-arrow-down-wide-short';
                icon.setAttribute('aria-hidden', 'true');

                const message = document.createElement('span');
                message.textContent = 'Escribe los barrios para ver el recorrido';

                empty.append(icon, message);
                previewStops.append(empty);
                return;
            }

            neighborhoods.forEach((neighborhood, index) => {
                const item = document.createElement('li');
                item.className = 'route-preview-stop';
                item.style.animationDelay = `${Math.min(index, 12) * 24}ms`;

                const marker = document.createElement('span');
                marker.className = 'route-preview-stop__marker';
                marker.textContent = String(index + 1).padStart(2, '0');
                marker.setAttribute('aria-hidden', 'true');

                const name = document.createElement('span');
                name.className = 'route-preview-stop__name';
                name.textContent = neighborhood;

                const kind = document.createElement('span');
                kind.className = 'route-preview-stop__kind';
                kind.textContent = neighborhoods.length === 1
                    ? 'Única parada'
                    : index === 0
                        ? 'Inicio'
                        : index === neighborhoods.length - 1
                            ? 'Destino'
                            : 'Parada';

                item.append(marker, name, kind);
                previewStops.append(item);
            });
        };

        orderedNeighborhoods.addEventListener('input', renderPreview);
        renderPreview();
    }

    if (verifiedCheckbox && verificationState) {
        verifiedCheckbox.addEventListener('change', () => {
            const isVerified = verifiedCheckbox.checked;
            verificationState.classList.toggle('is-verified', isVerified);

            const icon = verificationState.querySelector('i');
            const label = verificationState.querySelector('span');

            if (icon) {
                icon.classList.toggle('fa-circle-check', isVerified);
                icon.classList.toggle('fa-clock', !isVerified);
            }

            if (label) {
                label.textContent = isVerified ? 'Verificada' : 'Pendiente';
            }
        });
    }
})();
