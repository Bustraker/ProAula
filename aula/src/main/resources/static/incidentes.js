// Cargar e Implementar filtros de consulta (BUS-39)
async function filtrarIncidentes() {
    const tipo = document.getElementById('filtroTipo').value;
    const ruta = document.getElementById('filtroRuta').value;

    let url = `/api/incidentes?tipo=${encodeURIComponent(tipo)}&ruta=${encodeURIComponent(ruta)}`;

    try {
        const respuesta = await fetch(url);
        
        if (!respuesta.ok) {
            console.warn('Endpoint de API aún no conectado o retornó código:', respuesta.status);
            return;
        }

        const incidentes = await respuesta.json();
        const tbody = document.getElementById('tablaIncidentes');
        tbody.innerHTML = '';

        if (incidentes.length === 0) {
            tbody.innerHTML = `<tr><td colspan="6" class="text-center text-muted">No se encontraron incidentes registrados.</td></tr>`;
            return;
        }

        incidentes.forEach(inc => {
            tbody.innerHTML += `
                <tr>
                    <td><strong>#${inc.id || 'N/A'}</strong></td>
                    <td>${inc.ruta || 'General'}</td>
                    <td><span class="badge bg-warning text-dark">${inc.tipo || 'REPORTADO'}</span></td>
                    <td>${inc.descripcion || ''}</td>
                    <td>${inc.fecha ? new Date(inc.fecha).toLocaleString() : 'Reciente'}</td>
                    <td><span class="badge bg-success">Activo</span></td>
                </tr>
            `;
        });
    } catch (error) {
        console.error('Error al filtrar los incidentes:', error);
    }
}

// Integrar envío de nuevo incidente vía API (BUS-35)
async function registrarIncidente(event) {
    if (event) event.preventDefault();

    const nuevoIncidente = {
        ruta: document.getElementById('rutaInput')?.value || '',
        tipo: document.getElementById('tipoInput')?.value || 'OTRO',
        descripcion: document.getElementById('descripcionInput')?.value || '',
        fecha: new Date().toISOString()
    };

    try {
        const respuesta = await fetch('/api/incidentes', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify(nuevoIncidente)
        });

        if (respuesta.ok) {
            alert('¡Incidente reportado con éxito!');
            filtrarIncidentes();
        } else {
            alert('Error al registrar el incidente.');
        }
    } catch (error) {
        console.error('Error de red al registrar:', error);
    }
}

// Cargar la tabla automáticamente al abrir la vista
document.addEventListener("DOMContentLoaded", filtrarIncidentes);