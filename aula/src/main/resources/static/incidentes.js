async function filtrarIncidentes() {
    const tipo = document.getElementById('filtroTipo').value;
    const ruta = document.getElementById('filtroRuta').value;
    const parametros = new URLSearchParams();

    if (tipo) parametros.set('tipo', tipo);
    if (ruta.trim()) parametros.set('ruta', ruta.trim());
    const consulta = parametros.toString();
    const url = `/api/incidentes${consulta ? `?${consulta}` : ''}`;
    const tbody = document.getElementById('tablaIncidentes');

    try {
        const respuesta = await fetch(url);
        if (!respuesta.ok) {
            throw new Error(`La consulta de incidentes respondió ${respuesta.status}.`);
        }

        const incidentes = await respuesta.json();
        if (incidentes.length === 0) {
            mostrarMensajeTabla('No se encontraron incidentes registrados.');
            return;
        }

        const filas = incidentes.map(incidente => {
            const fila = document.createElement('tr');
            const valores = [
                `#${incidente.id}`,
                incidente.ruta || 'General',
                incidente.tipo,
                incidente.descripcion,
                incidente.fecha ? new Date(incidente.fecha).toLocaleString() : '—',
                incidente.estado
            ];

            valores.forEach((valor, indice) => {
                const celda = document.createElement('td');
                celda.textContent = valor || '—';
                if (indice === 0) {
                    const strong = document.createElement('strong');
                    strong.textContent = celda.textContent;
                    celda.replaceChildren(strong);
                } else if (indice === 2 || indice === 5) {
                    const badge = document.createElement('span');
                    badge.className = indice === 2 ? 'badge bg-warning text-dark' : 'badge bg-secondary';
                    badge.textContent = celda.textContent;
                    celda.replaceChildren(badge);
                }
                fila.append(celda);
            });
            return fila;
        });
        tbody.replaceChildren(...filas);
    } catch (error) {
        console.error('Error al filtrar los incidentes:', error);
        mostrarMensajeTabla('No fue posible cargar los incidentes. Inténtalo de nuevo.', 'text-danger');
    }
}

function mostrarMensajeTabla(mensaje, clase = 'text-muted') {
    const celda = document.createElement('td');
    celda.colSpan = 6;
    celda.className = `text-center ${clase}`;
    celda.textContent = mensaje;

    const fila = document.createElement('tr');
    fila.append(celda);
    document.getElementById('tablaIncidentes').replaceChildren(fila);
}

document.addEventListener('DOMContentLoaded', filtrarIncidentes);