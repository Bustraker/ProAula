const TAMANO_PAGINA = 20;
const etiquetasTipo = {
    RETRASO: 'Retraso de ruta',
    FALLA_SERVICIO: 'Falla del bus o del servicio',
    ACCIDENTE: 'Accidente o bloqueo',
    RUTA_DESVIADA: 'Desvío de ruta',
    OTRO: 'Otro'
};
const estados = {
    PENDIENTE: { etiqueta: 'Pendiente', clase: 'pending' },
    EN_ATENCION: { etiqueta: 'En atención', clase: 'attention' },
    RESUELTO: { etiqueta: 'Resuelto', clase: 'resolved' },
    DESCARTADO: { etiqueta: 'Descartado', clase: 'dismissed' }
};

let paginaActual = 0;
let solicitudActiva;

const elementos = {
    formulario: document.getElementById('filtroIncidentes'),
    tipo: document.getElementById('filtroTipo'),
    ruta: document.getElementById('filtroRuta'),
    tabla: document.getElementById('tablaIncidentes'),
    feedback: document.getElementById('panelFeedback'),
    total: document.getElementById('totalIncidentes'),
    resumenPagina: document.getElementById('resumenPagina'),
    etiquetaPagina: document.getElementById('paginaActual'),
    anterior: document.getElementById('paginaAnterior'),
    siguiente: document.getElementById('paginaSiguiente')
};

async function cargarIncidentes(pagina = 0) {
    paginaActual = pagina;
    solicitudActiva?.abort();
    solicitudActiva = new AbortController();
    elementos.tabla.replaceChildren(crearFilaMensaje('Cargando reportes...'));
    mostrarFeedback('Cargando reportes...');

    const parametros = new URLSearchParams({
        page: String(pagina),
        size: String(TAMANO_PAGINA)
    });
    if (elementos.tipo.value) {
        parametros.set('tipo', elementos.tipo.value);
    }
    if (elementos.ruta.value.trim()) {
        parametros.set('ruta', elementos.ruta.value.trim());
    }

    try {
        const respuesta = await fetch(`/api/incidentes?${parametros}`, {
            signal: solicitudActiva.signal,
            headers: { Accept: 'application/json' }
        });
        const datos = await respuesta.json();
        if (!respuesta.ok) {
            throw new Error(datos.message || `La consulta respondió ${respuesta.status}.`);
        }
        if (!Array.isArray(datos.content)) {
            throw new Error('El servidor devolvió un formato de respuesta inesperado.');
        }

        renderizarIncidentes(datos.content);
        actualizarPaginacion(datos);
        elementos.total.textContent = `${datos.totalElements} ${
            datos.totalElements === 1 ? 'reporte' : 'reportes'
        }`;
        mostrarFeedback(
            datos.totalElements === 0
                ? 'No hay reportes que coincidan con los filtros seleccionados.'
                : `${datos.totalElements} ${
                    datos.totalElements === 1 ? 'reporte encontrado' : 'reportes encontrados'
                }.`
        );
    } catch (error) {
        if (error.name === 'AbortError') {
            return;
        }
        console.error('Error al cargar los incidentes:', error);
        elementos.tabla.replaceChildren(crearFilaMensaje('No fue posible cargar los reportes.', true));
        elementos.total.textContent = '—';
        actualizarPaginacion({ page: 0, totalPages: 0, totalElements: 0, size: TAMANO_PAGINA });
        mostrarFeedback(error.message || 'No fue posible cargar los reportes. Inténtalo de nuevo.', 'error');
    }
}

function renderizarIncidentes(incidentes) {
    if (incidentes.length === 0) {
        elementos.tabla.replaceChildren(crearFilaMensaje('No se encontraron reportes.'));
        return;
    }

    const filas = incidentes.map(crearFilaIncidente);
    elementos.tabla.replaceChildren(...filas);
}

function crearFilaIncidente(incidente) {
    const fila = document.createElement('tr');
    const reporte = document.createElement('td');
    const identificador = document.createElement('span');
    identificador.className = 'report-id';
    identificador.textContent = `REPORTE #${incidente.id}`;
    const descripcion = document.createElement('span');
    descripcion.className = 'report-description';
    descripcion.textContent = incidente.descripcion || 'Sin descripción';
    reporte.append(identificador, descripcion);

    const tipo = document.createElement('td');
    tipo.className = 'incident-type';
    tipo.textContent = etiquetasTipo[incidente.tipo] || incidente.tipo || 'Sin tipo';

    const ruta = document.createElement('td');
    ruta.textContent = incidente.ruta || 'General';

    const fecha = document.createElement('td');
    fecha.className = 'incident-date';
    fecha.textContent = formatearFecha(incidente.fecha);

    const estado = document.createElement('td');
    estado.append(crearEtiquetaEstado(incidente.estado));

    const accion = document.createElement('td');
    const control = document.createElement('div');
    control.className = 'status-control';
    const selector = crearSelectorEstado(incidente.estado, incidente.id);
    const boton = document.createElement('button');
    boton.className = 'button button--primary button--small';
    boton.type = 'button';
    boton.textContent = 'Guardar';
    boton.disabled = true;
    selector.addEventListener('change', () => {
        boton.disabled = selector.value === selector.dataset.savedStatus;
        boton.textContent = boton.disabled ? 'Guardar' : 'Guardar';
    });
    boton.addEventListener('click', () =>
        actualizarEstado(incidente.id, selector, boton, estado));
    control.append(selector, boton);
    accion.append(control);

    fila.append(reporte, tipo, ruta, fecha, estado, accion);
    return fila;
}

function crearSelectorEstado(estadoActual, incidenteId) {
    const selector = document.createElement('select');
    selector.className = 'status-select';
    selector.setAttribute('aria-label', `Nuevo estado del reporte ${incidenteId}`);
    Object.entries(estados).forEach(([valor, detalle]) => {
        const opcion = document.createElement('option');
        opcion.value = valor;
        opcion.textContent = detalle.etiqueta;
        selector.append(opcion);
    });
    selector.value = estados[estadoActual] ? estadoActual : 'PENDIENTE';
    selector.dataset.savedStatus = selector.value;
    return selector;
}

function crearEtiquetaEstado(estado) {
    const detalle = estados[estado] || { etiqueta: estado || 'Sin estado', clase: 'dismissed' };
    const etiqueta = document.createElement('span');
    etiqueta.className = `status-badge status-badge--${detalle.clase}`;
    etiqueta.textContent = detalle.etiqueta;
    return etiqueta;
}

function crearFilaMensaje(mensaje, error = false) {
    const fila = document.createElement('tr');
    const celda = document.createElement('td');
    celda.colSpan = 6;
    celda.className = `table-message${error ? ' table-message--error' : ''}`;
    celda.textContent = mensaje;
    fila.append(celda);
    return fila;
}

async function actualizarEstado(id, selector, boton, celdaEstado) {
    const estadoAnterior = selector.dataset.savedStatus;
    const estadoNuevo = selector.value;
    boton.disabled = true;
    boton.textContent = 'Guardando...';
    selector.disabled = true;
    mostrarFeedback(`Actualizando el estado del reporte #${id}...`);

    const csrfToken = document.querySelector('meta[name="csrf-token"]').content;
    const csrfHeader = document.querySelector('meta[name="csrf-header"]').content;
    try {
        const respuesta = await fetch(`/api/incidentes/${id}/estado`, {
            method: 'PATCH',
            headers: {
                Accept: 'application/json',
                'Content-Type': 'application/json',
                [csrfHeader]: csrfToken
            },
            body: JSON.stringify({ estado: estadoNuevo })
        });
        const datos = await respuesta.json();
        if (!respuesta.ok) {
            throw new Error(datos.message || `No se pudo actualizar el estado (${respuesta.status}).`);
        }

        selector.dataset.savedStatus = datos.estado;
        selector.value = datos.estado;
        celdaEstado.replaceChildren(crearEtiquetaEstado(datos.estado));
        boton.textContent = 'Guardado';
        mostrarFeedback(`Estado del reporte #${id} actualizado correctamente.`, 'success');
    } catch (error) {
        boton.disabled = selector.value === selector.dataset.savedStatus;
        boton.textContent = 'Guardar';
        console.error(`Error al actualizar el estado del reporte #${id}:`, error);
        mostrarFeedback(error.message || 'No fue posible actualizar el reporte.', 'error');
    } finally {
        selector.disabled = false;
        boton.disabled = selector.value === selector.dataset.savedStatus;
    }
}

function actualizarPaginacion(datos) {
    const total = Number(datos.totalElements || 0);
    const cantidadPaginas = Number(datos.totalPages || 0);
    const pagina = Number(datos.page || 0);
    const tamano = Number(datos.size || TAMANO_PAGINA);
    const desde = total === 0 ? 0 : pagina * tamano + 1;
    const hasta = Math.min((pagina + 1) * tamano, total);

    elementos.resumenPagina.textContent = total === 0
        ? 'No hay reportes para mostrar'
        : `Mostrando ${desde}–${hasta} de ${total}`;
    elementos.etiquetaPagina.textContent = cantidadPaginas === 0
        ? 'Página 0 de 0'
        : `Página ${pagina + 1} de ${cantidadPaginas}`;
    elementos.anterior.disabled = pagina <= 0;
    elementos.siguiente.disabled = cantidadPaginas === 0 || pagina + 1 >= cantidadPaginas;
}

function formatearFecha(valor) {
    if (!valor) {
        return '—';
    }
    const fecha = new Date(valor);
    if (Number.isNaN(fecha.getTime())) {
        return '—';
    }
    return new Intl.DateTimeFormat('es-CO', {
        dateStyle: 'medium',
        timeStyle: 'short'
    }).format(fecha);
}

function mostrarFeedback(mensaje, tipo = '') {
    elementos.feedback.textContent = mensaje;
    elementos.feedback.className = `panel-feedback${tipo ? ` panel-feedback--${tipo}` : ''}`;
}

elementos.formulario.addEventListener('submit', event => {
    event.preventDefault();
    cargarIncidentes(0);
});

document.getElementById('limpiarFiltros').addEventListener('click', () => {
    elementos.formulario.reset();
    cargarIncidentes(0);
});

elementos.anterior.addEventListener('click', () => {
    if (paginaActual > 0) {
        cargarIncidentes(paginaActual - 1);
    }
});

elementos.siguiente.addEventListener('click', () => {
    cargarIncidentes(paginaActual + 1);
});

cargarIncidentes();
