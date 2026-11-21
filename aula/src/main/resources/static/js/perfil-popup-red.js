(function () {
    const wrapper = document.createElement('div');
    wrapper.className = 'popup-wrapper';
    wrapper.id = 'viajePopupWrapper';
    document.body.appendChild(wrapper);

    const template = `
        <div class="popup" id="viajePopup">
            <div class="popup-header">
                <h3>Resumen del viaje</h3>
                <button id="closePopup" aria-label="Cerrar">&times;</button>
            </div>
            <section class="popup-section">
                <h4><i class="fas fa-map-marker-alt"></i>Recorrido</h4>
                <p id="recorridoInfo"></p>
            </section>
            <section class="popup-section private-only">
                <h4><i class="fas fa-info-circle"></i>Detalles</h4>
                <p id="detalleInfo"></p>
                <div class="accordion-content" id="extraInfo">
                    <p id="precioInfo"></p>
                    <p id="asientosInfo"></p>
                </div>
                <button class="toggle-accordion" aria-expanded="false">
                    Ver más <i class="fas fa-chevron-down"></i>
                </button>
            </section>
            <div class="popup-action">
                <button id="actionBtn">Reservar ahora</button>
            </div>
        </div>
    `;
    wrapper.innerHTML = template;

    const popup = document.getElementById('viajePopup');
    const closeBtn = document.getElementById('closePopup');
    const toggleBtn = document.querySelector('.toggle-accordion');
    const extraContent = document.getElementById('extraInfo');

    closeBtn.addEventListener('click', () => wrapper.classList.remove('show'));
    if (toggleBtn) {
        toggleBtn.addEventListener('click', () => {
            const expanded = toggleBtn.getAttribute('aria-expanded') === 'true';
            toggleBtn.setAttribute('aria-expanded', !expanded);
            extraContent.classList.toggle('show');
            const icon = toggleBtn.querySelector('i');
            if (icon) {
                icon.classList.toggle('fa-chevron-down');
                icon.classList.toggle('fa-chevron-up');
            }
        });
    }

    // Expose a global function to open the popup
    window.showViajePopup = function (data, isPrivate = false) {
        const recorrido = `${data.origen} → ${data.destino} (${data.tipo})`;
        document.getElementById('recorridoInfo').textContent = recorrido;

        // Show/Hide private sections
        const privateSections = document.querySelectorAll('.private-only');
        privateSections.forEach(el => el.style.display = isPrivate ? 'block' : 'none');

        if (isPrivate) {
            document.getElementById('detalleInfo').textContent =
                `Duración: ${data.duracion || '≈ 45 min'} – Hora estimada: ${data.hora}`;
            document.getElementById('precioInfo').textContent = `Precio: $${data.precio ?? '--'}`;
            document.getElementById('asientosInfo').textContent = `Asientos libres: ${data.asientos ?? '--'}`;
        } else {
            document.getElementById('detalleInfo').textContent = `Hora estimada: ${data.hora}`;
        }

        const actionBtn = document.getElementById('actionBtn');
        actionBtn.textContent = isPrivate ? 'Reservar ahora' : 'Ver más';
        actionBtn.onclick = () => {
            if (isPrivate) {
                alert(`Reserva confirmada para ${recorrido}`);
            } else {
                alert(`Detalles del viaje: ${recorrido}`);
            }
        };

        wrapper.classList.add('show');
    };
})();
