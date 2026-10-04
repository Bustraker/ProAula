// Served by Spring from /js/viajar_public.js.

var map;
var rutas = [];
var barrios = [];
var barriosData = [];
var coordenadasCache = {};
var origenMarker = null;
var destinoMarker = null;
var rutaLine = null;
var rutaGlow = null;
var routeFlow = null;
var rutaSolicitud = 0;
var barrioMarkers = [];
var barrioLabelMarkers = [];
var stopMarkers = [];
var mostrarParadas = true;
var trazadosTranscaribe = [];
var trazadoTranscaribeLayer = null;
var trayectoGeometry = null;
var barriosTrayecto = [];
var seguimientoUbicacionId = null;
var ubicacionActual = null;
var ubicacionActualMarker = null;
var ubicacionPrecisionCircle = null;

const TRANSCaribeGeoJsonUrl = 'https://services7.arcgis.com/t784NacZjQPpWVsA/arcgis/rest/services/Cobertura_Transcaribe_2024_WFL1/FeatureServer/1/query';

document.addEventListener('DOMContentLoaded', function() {
    cargarBarrios();
    cargarRutas();
    initMap();
    cargarTrazadosTranscaribe();
    document.getElementById('trazadoTranscaribe')?.addEventListener('change', mostrarTrazadoTranscaribe);
    document.getElementById('mostrarParadas')?.addEventListener('change', function(e) {
        toggleMostrarParadas(e.target.checked);
    });
});

function initMap() {
    map = BustrakerMap.crearMapa('map', {
        obtenerLimites: function () {
            if (rutaLine) return rutaLine.getBounds();
            if (trazadoTranscaribeLayer) return trazadoTranscaribeLayer.getBounds();
            return null;
        }
    });
    map.on('zoomend', actualizarEtiquetasBarrios);
    setTimeout(() => {
        if (map) {
            map.invalidateSize();
            actualizarEtiquetasBarrios();
        }
    }, 200);
}

async function cargarBarrios() {
    try {
        const response = await fetch('/api/barrios/todos');
        if (response.ok) {
            const data = await response.json();
            barriosData = data;
            barrios = barriosData.map(b => b.nombre);
            barriosData.forEach(barrio => {
                const key = normalizarNombre(barrio.nombre);
                coordenadasCache[key] = { lat: Number.parseFloat(barrio.latitud), lng: Number.parseFloat(barrio.longitud) };
            });
            actualizarEtiquetasBarrios();
            const origenSelect = document.getElementById('origen');
            origenSelect.innerHTML = '<option value="">Selecciona tu barrio actual</option>';
            [...barrios].sort().forEach(barrio => {
                const opt = document.createElement('option'); opt.value = barrio; opt.textContent = barrio.charAt(0).toUpperCase() + barrio.slice(1); origenSelect.appendChild(opt);
            });
            const detectarBtn = document.getElementById('btnDetectarUbicacion'); if (detectarBtn) { detectarBtn.disabled = false; detectarBtn.style.opacity = '1'; detectarBtn.style.cursor = 'pointer'; }
        }
    } catch (error) { console.error('❌ Error cargando barrios:', error); }
}

async function cargarRutas() {
    try { const response = await fetch('/api/rutas-con-coordenadas'); if (response.ok) { rutas = await response.json(); } } catch (error) { console.error('❌ Error cargando rutas:', error); }
}

async function cargarTrazadosTranscaribe() {
    const select = document.getElementById('trazadoTranscaribe');
    if (!select) return;

    const params = new URLSearchParams({
        where: '1=1',
        outFields: 'name,cod_ruta,ruta,tipo,h_lu_vi,h_sa,h_do_fe,f_act',
        returnGeometry: 'true',
        outSR: '4326',
        f: 'geojson'
    });

    try {
        const response = await fetch(`${TRANSCaribeGeoJsonUrl}?${params}`);
        if (!response.ok) throw new Error(`GIS respondió ${response.status}`);
        const data = await response.json();
        trazadosTranscaribe = data.features || [];
        select.replaceChildren(new Option('Selecciona un recorrido', ''));

        trazadosTranscaribe.forEach((feature, index) => {
            const properties = feature.properties || {};
            select.add(new Option(`${properties.cod_ruta || ''} · ${properties.name || properties.ruta || 'Ruta'}`, index));
        });
        select.disabled = trazadosTranscaribe.length === 0;
    } catch (error) {
        console.error('No se pudieron cargar los trazados GIS de TransCaribe:', error);
        select.replaceChildren(new Option('Trazados no disponibles', ''));
        select.disabled = true;
    }
}

function mostrarTrazadoTranscaribe() {
    if (trazadoTranscaribeLayer) {
        map.removeLayer(trazadoTranscaribeLayer);
        trazadoTranscaribeLayer = null;
    }

    const select = document.getElementById('trazadoTranscaribe');
    const feature = select && trazadosTranscaribe[Number(select.value)];
    if (!feature) return;

    limpiarMapa();
    trazadoTranscaribeLayer = L.geoJSON(feature, {
        style: { color: '#1cb9bf', weight: 6, opacity: 0.95, lineCap: 'round', lineJoin: 'round' },
        onEachFeature: (routeFeature, layer) => {
            const properties = routeFeature.properties || {};
            const popup = document.createElement('div');
            const title = document.createElement('strong');
            title.textContent = `${properties.cod_ruta || ''} · ${properties.ruta || properties.name || 'TransCaribe'}`;
            popup.appendChild(title);
            if (properties.tipo) {
                const type = document.createElement('div');
                type.textContent = properties.tipo;
                popup.appendChild(type);
            }
            [['Lun-Vie', properties.h_lu_vi], ['Sábado', properties.h_sa], ['Domingos y festivos', properties.h_do_fe]]
                .filter(([, schedule]) => schedule)
                .forEach(([day, schedule]) => {
                    const hours = document.createElement('div');
                    hours.textContent = `${day}: ${schedule}`;
                    popup.appendChild(hours);
                });
            const year = document.createElement('div');
            year.textContent = `Trazado de referencia ${properties.f_act ? String(properties.f_act).slice(0, 4) : '2024'}`;
            popup.appendChild(year);
            layer.bindPopup(popup);
        }
    }).addTo(map);
    mostrarFlujoTrayecto(feature.geometry, '#d6ffff');

    const bounds = trazadoTranscaribeLayer.getBounds();
    if (ubicacionActualMarker) bounds.extend(ubicacionActualMarker.getLatLng());
    if (bounds.isValid()) BustrakerMap.ajustarVista(map, bounds);
    establecerTrayecto(feature.geometry);
}

function limpiarTrazadoTranscaribe() {
    if (trazadoTranscaribeLayer) {
        map.removeLayer(trazadoTranscaribeLayer);
        trazadoTranscaribeLayer = null;
    }
    if (routeFlow) {
        map.removeLayer(routeFlow);
        routeFlow = null;
    }
    const select = document.getElementById('trazadoTranscaribe');
    if (select) select.value = '';
}

function normalizarNombre(nombre) { return nombre.toLowerCase().normalize('NFD').replace(/[\u0300-\u036f]/g, '').trim(); }
function obtenerCoordenadas(barrio) { return coordenadasCache[normalizarNombre(barrio)] || null; }
function distanciaHaversine(lat1, lng1, lat2, lng2) { const R = 6371; const dLat = (lat2 - lat1) * Math.PI / 180; const dLng = (lng2 - lng1) * Math.PI / 180; const a = Math.sin(dLat / 2) * Math.sin(dLat / 2) + Math.cos(lat1 * Math.PI / 180) * Math.cos(lat2 * Math.PI / 180) * Math.sin(dLng / 2) * Math.sin(dLng / 2); const c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a)); return R * c; }

function proyectarPuntoEnTrayecto(lat, lng, geometry) {
    if (!geometry || !geometry.coordinates) return null;
    const lines = geometry.type === 'MultiLineString' ? geometry.coordinates : [geometry.coordinates];
    let distanciaAcumuladaKm = 0;
    let mejorProyeccion = null;

    lines.forEach(linea => {
        for (let index = 1; index < linea.length; index++) {
            const inicio = linea[index - 1];
            const fin = linea[index];
            const latitudReferencia = (lat + inicio[1] + fin[1]) / 3;
            const escalaX = 111.32 * Math.cos(latitudReferencia * Math.PI / 180);
            const dx = (fin[0] - inicio[0]) * escalaX;
            const dy = (fin[1] - inicio[1]) * 110.574;
            const px = (lng - inicio[0]) * escalaX;
            const py = (lat - inicio[1]) * 110.574;
            const longitudCuadrada = dx * dx + dy * dy;
            const fraccion = longitudCuadrada === 0 ? 0 : Math.max(0, Math.min(1, (px * dx + py * dy) / longitudCuadrada));
            const distanciaAlSegmentoKm = Math.hypot(px - fraccion * dx, py - fraccion * dy);

            if (!mejorProyeccion || distanciaAlSegmentoKm < mejorProyeccion.distanciaAlTrayectoKm) {
                mejorProyeccion = {
                    distanciaAlTrayectoKm: distanciaAlSegmentoKm,
                    distanciaDesdeInicioKm: distanciaAcumuladaKm + fraccion * distanciaHaversine(inicio[1], inicio[0], fin[1], fin[0])
                };
            }

            distanciaAcumuladaKm += distanciaHaversine(inicio[1], inicio[0], fin[1], fin[0]);
        }
    });

    return mejorProyeccion;
}

function obtenerBarriosEnTrayecto(geometry, radioKm = 0.7) {
    const encontrados = new Map();
    barriosData.forEach(barrio => {
        const lat = Number.parseFloat(barrio.latitud);
        const lng = Number.parseFloat(barrio.longitud);
        if (!Number.isFinite(lat) || !Number.isFinite(lng)) return;

        const proyeccion = proyectarPuntoEnTrayecto(lat, lng, geometry);
        const clave = normalizarNombre(barrio.nombre);
        if (proyeccion && proyeccion.distanciaAlTrayectoKm <= radioKm && !encontrados.has(clave)) {
            encontrados.set(clave, {
                nombre: barrio.nombre,
                distanciaDesdeInicioKm: proyeccion.distanciaDesdeInicioKm,
                distanciaAlTrayectoKm: proyeccion.distanciaAlTrayectoKm
            });
        }
    });

    return [...encontrados.values()].sort((primero, segundo) => primero.distanciaDesdeInicioKm - segundo.distanciaDesdeInicioKm);
}

function establecerTrayecto(geometry) {
    trayectoGeometry = geometry;
    barriosTrayecto = geometry ? obtenerBarriosEnTrayecto(geometry) : [];
    const resumen = document.getElementById('barriosTrayecto');
    if (resumen) {
        resumen.textContent = barriosTrayecto.length
            ? `Barrios cercanos al trayecto, en orden aproximado: ${barriosTrayecto.map(barrio => barrio.nombre).join(' → ')}`
            : 'No se identificaron barrios cercanos a este trazado.';
    }
    actualizarEstadoUbicacion();
}

function mostrarFlujoTrayecto(geometry, color = '#c9ffff') {
    if (routeFlow) map.removeLayer(routeFlow);
    routeFlow = L.geoJSON(geometry, {
        style: { color, weight: 3, opacity: 0.95, dashArray: '8 18', className: 'route-flow', lineCap: 'round' }
    }).addTo(map);
}

function actualizarVisibilidadParadas() {
    if (mostrarParadas) {
        stopMarkers.forEach(marker => { if (!map.hasLayer(marker)) marker.addTo(map); });
    } else {
        stopMarkers.forEach(marker => { if (map.hasLayer(marker)) map.removeLayer(marker); });
    }
}

function toggleMostrarParadas(show) {
    mostrarParadas = show;
    actualizarVisibilidadParadas();
}

function crearMarkerParada(point, index, rol) {
    // rol: 'origen' | 'destino' | null (punto intermedio)
    if (rol === 'origen' || rol === 'destino') {
        const pin = BustrakerMap.crearPin(point.lat, point.lng, rol, point.nombre).addTo(map);
        if (rol === 'origen') origenMarker = pin; else destinoMarker = pin;
        return;
    }
    const titulo = point.tipo === 'parada' ? `Parada ${index}` : `Barrio del tramo ${index}`;
    const marker = BustrakerMap.crearPuntoIntermedio(point.lat, point.lng, titulo, point.nombre);
    if (mostrarParadas) marker.addTo(map);
    stopMarkers.push(marker);
}

function obtenerBarrioMasCercano(lat, lng) {
    if (!barriosData.length) return null;
    let mejorBarrio = null; let distanciaMinima = Infinity;
    barriosData.forEach(barrio => { const latBarrio = Number.parseFloat(barrio.latitud); const lngBarrio = Number.parseFloat(barrio.longitud); if (Number.isNaN(latBarrio) || Number.isNaN(lngBarrio)) return; const distancia = distanciaHaversine(lat, lng, latBarrio, lngBarrio); if (distancia < distanciaMinima) { distanciaMinima = distancia; mejorBarrio = barrio; } });
    return mejorBarrio;
}

function actualizarEstadoUbicacion() {
    const status = document.getElementById('locationStatus');
    if (!status || !ubicacionActual) return;

    const barrioCercano = obtenerBarrioMasCercano(ubicacionActual.lat, ubicacionActual.lng);
    const precision = Number.isFinite(ubicacionActual.accuracy) ? ` ±${Math.round(ubicacionActual.accuracy)} m` : '';
    let mensaje = `Ubicación en vivo${precision}.`;
    if (barrioCercano) mensaje += ` Barrio de referencia cercano: ${barrioCercano.nombre}.`;

    const proyeccion = proyectarPuntoEnTrayecto(ubicacionActual.lat, ubicacionActual.lng, trayectoGeometry);
    if (proyeccion && barriosTrayecto.length && proyeccion.distanciaAlTrayectoKm <= 1.2) {
        const siguiente = barriosTrayecto.find(barrio => barrio.distanciaDesdeInicioKm >= proyeccion.distanciaDesdeInicioKm - 0.15);
        if (siguiente) mensaje += ` Siguiente barrio aproximado del trazado: ${siguiente.nombre}.`;
    } else if (trayectoGeometry && proyeccion) {
        mensaje += ` Estás a ${proyeccion.distanciaAlTrayectoKm.toFixed(1)} km del trazado seleccionado.`;
    }

    status.textContent = mensaje;
}

function actualizarMarcadorUbicacion(position) {
    const lat = position.coords.latitude;
    const lng = position.coords.longitude;
    const primeraUbicacion = ubicacionActual === null;
    ubicacionActual = { lat, lng, accuracy: position.coords.accuracy };

    if (!ubicacionActualMarker) {
        const icon = L.divIcon({ className: 'user-location-icon', html: '<span></span>', iconSize: [28, 28], iconAnchor: [14, 14] });
        ubicacionActualMarker = L.marker([lat, lng], { icon }).addTo(map);
    } else {
        ubicacionActualMarker.setLatLng([lat, lng]);
    }

    const popup = document.createElement('div');
    popup.textContent = 'Tu ubicación actual (GPS)';
    ubicacionActualMarker.setPopupContent(popup);

    if (Number.isFinite(position.coords.accuracy)) {
        if (!ubicacionPrecisionCircle) {
            ubicacionPrecisionCircle = L.circle([lat, lng], {
                radius: position.coords.accuracy,
                color: '#1877f2',
                weight: 1,
                fillColor: '#1877f2',
                fillOpacity: 0.08
            }).addTo(map);
        } else {
            ubicacionPrecisionCircle.setLatLng([lat, lng]);
            ubicacionPrecisionCircle.setRadius(position.coords.accuracy);
        }
    }

    const origenSelect = document.getElementById('origen');
    const barrioCercano = obtenerBarrioMasCercano(lat, lng);
    if (primeraUbicacion && origenSelect && !origenSelect.value && barrioCercano) {
        origenSelect.value = barrioCercano.nombre;
        actualizarDestinosDisponibles();
    }

    if (primeraUbicacion) map.setView([lat, lng], Math.max(map.getZoom(), 15));
    actualizarEstadoUbicacion();
}

function establecerBotonSeguimiento(activo) {
    const button = document.getElementById('btnSeguirUbicacion') || document.getElementById('btnDetectarUbicacion');
    if (!button) return;
    button.innerHTML = activo
        ? '<i class="fas fa-location-crosshairs" aria-hidden="true"></i> Dejar de seguir'
        : '<i class="fas fa-location-arrow" aria-hidden="true"></i> Seguir mi ubicación';
    button.setAttribute('aria-pressed', String(activo));
}

function usarUbicacionActual() {
    if (seguimientoUbicacionId !== null) {
        navigator.geolocation.clearWatch(seguimientoUbicacionId);
        seguimientoUbicacionId = null;
        establecerBotonSeguimiento(false);
        const status = document.getElementById('locationStatus');
        if (status) status.textContent = 'Seguimiento pausado. Se conserva la última posición en el mapa.';
        return;
    }

    if (!navigator.geolocation) {
        const status = document.getElementById('locationStatus');
        if (status) status.textContent = 'Este navegador no permite acceder a la ubicación.';
        return;
    }

    const status = document.getElementById('locationStatus');
    if (status) status.textContent = 'Esperando permiso y señal GPS…';
    establecerBotonSeguimiento(true);
    seguimientoUbicacionId = navigator.geolocation.watchPosition(actualizarMarcadorUbicacion, error => {
        const mensajes = {
            1: 'Permiso de ubicación denegado. Puedes habilitarlo en el navegador.',
            2: 'No se pudo determinar la ubicación. Intenta de nuevo al aire libre.',
            3: 'La ubicación tardó demasiado. Intenta de nuevo.'
        };
        if (status) status.textContent = mensajes[error.code] || 'No se pudo obtener la ubicación.';
        navigator.geolocation.clearWatch(seguimientoUbicacionId);
        seguimientoUbicacionId = null;
        establecerBotonSeguimiento(false);
    }, { enableHighAccuracy: true, maximumAge: 5000, timeout: 20000 });
}

function actualizarDestinosDisponibles() { const origen = document.getElementById('origen').value; const destinoSelect = document.getElementById('destino'); const rutaSelect = document.getElementById('ruta'); if (origen) { destinoSelect.disabled = false; destinoSelect.innerHTML = '<option value="">Selecciona tu barrio de destino</option>'; [...barrios].sort().forEach(barrio => { if (barrio !== origen) { const opt = document.createElement('option'); opt.value = barrio; opt.textContent = barrio.charAt(0).toUpperCase() + barrio.slice(1); destinoSelect.appendChild(opt); } }); rutaSelect.disabled = true; rutaSelect.innerHTML = '<option value="">Primero selecciona origen y destino</option>'; document.getElementById('rutaInfo').style.display = 'none'; } else { destinoSelect.disabled = true; destinoSelect.innerHTML = '<option value="">Primero selecciona tu barrio de origen</option>'; } }

function buscarRutasDisponibles() {
    const origen = document.getElementById('origen').value; const destino = document.getElementById('destino').value; const rutaSelect = document.getElementById('ruta');
    if (origen && destino) {
        const rutasFiltradas = rutas.map(ruta => {
            const barriosRuta = ruta.barrios || [];
            const inicio = barriosRuta.findIndex(b => normalizarNombre(b) === normalizarNombre(origen));
            const fin = barriosRuta.findIndex(b => normalizarNombre(b) === normalizarNombre(destino));
            if (inicio < 0 || fin <= inicio) return null;
            return { ...ruta, barrios: barriosRuta.slice(inicio, fin + 1) };
        }).filter(Boolean);
        rutaSelect.disabled = false; rutaSelect.innerHTML = '<option value="">Selecciona una ruta</option>';
        if (rutasFiltradas.length === 0) { const opt = document.createElement('option'); opt.value = ''; opt.textContent = 'No hay rutas verificadas en ese sentido'; opt.disabled = true; rutaSelect.appendChild(opt); return; }
        rutasFiltradas.forEach((ruta, idx) => { const opt = document.createElement('option'); opt.value = idx; opt.textContent = `${ruta.nombre} · ${ruta.horaAproximada || 'Horario no disponible'}`; opt.setAttribute('data-ruta-json', JSON.stringify(ruta)); rutaSelect.appendChild(opt); });
    } else { rutaSelect.disabled = true; rutaSelect.innerHTML = '<option value="">Primero selecciona origen y destino</option>'; }
}

async function mostrarRutaEnMapa() {
    limpiarTrazadoTranscaribe();
    const rutaSelect = document.getElementById('ruta'); const selectedOption = rutaSelect.options[rutaSelect.selectedIndex]; const rutaJson = selectedOption ? selectedOption.getAttribute('data-ruta-json') : null; if (!rutaJson) return; const ruta = JSON.parse(rutaJson);
    document.getElementById('infoBarrios').innerHTML = ruta.barrios.map(b => '<span class="badge">' + BustrakerMap.escapeHtml(b) + '</span>').join(''); document.getElementById('infoHora').textContent = ruta.horaAproximada || 'No especificada'; document.getElementById('rutaInfo').style.display = 'block'; limpiarMapa(); await mostrarRutaEnMapaConCoords(ruta);
}

async function mostrarRutaEnMapaConCoords(ruta) {
    const origen = document.getElementById('origen').value;
    const destino = document.getElementById('destino').value;
    const coordenadasRuta = ruta.coordenadasBarrios;
    if (!coordenadasRuta) {
        BustrakerMap.mostrarAviso('Esta ruta no tiene coordenadas disponibles.', 'warn');
        return;
    }

    const barriosDelTramo = new Set((ruta.barrios || []).map(normalizarNombre));
    const paradasConCoordenadas = (ruta.paradas || [])
        .filter(parada => parada.barrio && barriosDelTramo.has(normalizarNombre(parada.barrio))
            && Number.isFinite(Number(parada.latitud)) && Number.isFinite(Number(parada.longitud)))
        .sort((primera, segunda) => (primera.orden || 0) - (segunda.orden || 0));
    const waypoints = paradasConCoordenadas.length >= 2
        ? paradasConCoordenadas.map(parada => ({
            lat: Number(parada.latitud),
            lng: Number(parada.longitud),
            nombre: parada.nombre,
            tipo: 'parada'
        }))
        : ruta.barrios.map(barrio => {
            const coords = coordenadasRuta[barrio];
            return coords ? { lat: parseFloat(coords[0]), lng: parseFloat(coords[1]), nombre: barrio } : null;
        }).filter(Boolean);

    if (waypoints.length < 2) {
        BustrakerMap.mostrarAviso('Esta ruta no tiene suficientes coordenadas para trazar el recorrido.', 'warn');
        return;
    }

    const extremos = BustrakerMap.ubicarExtremos(waypoints, origen, destino, normalizarNombre);
    waypoints.forEach((wp, index) => crearMarkerParada(wp, index + 1,
        index === extremos.origen ? 'origen' : index === extremos.destino ? 'destino' : null));

    const solicitud = ++rutaSolicitud;
    BustrakerMap.mostrarCargando('Calculando ruta por calles…');
    try {
        const resultado = await BustrakerMap.obtenerRutaOSRM(waypoints);
        if (solicitud !== rutaSolicitud) return; // el usuario cambió de ruta mientras se calculaba

        if (rutaGlow) { map.removeLayer(rutaGlow); rutaGlow = null; }
        if (routeFlow) { map.removeLayer(routeFlow); routeFlow = null; }
        const capas = BustrakerMap.crearCapasRuta(resultado.geometry);
        rutaGlow = capas.base.addTo(map);
        rutaLine = capas.linea.addTo(map);
        mostrarFlujoTrayecto(resultado.geometry);
        establecerTrayecto(resultado.geometry);
        const routeBounds = rutaLine.getBounds();
        if (ubicacionActualMarker) routeBounds.extend(ubicacionActualMarker.getLatLng());
        BustrakerMap.ajustarVista(map, routeBounds);
        BustrakerMap.mostrarEstadisticas('rutaInfo', {
            distanciaKm: resultado.distanciaKm,
            tiempoMin: resultado.tiempoMin,
            puntos: waypoints.length
        });
    } catch (err) {
        if (solicitud !== rutaSolicitud) return;
        console.error('❌ Error OSRM:', err);
        BustrakerMap.mostrarEstadisticas('rutaInfo', { error: true });
        BustrakerMap.mostrarAviso(err.name === 'AbortError'
            ? 'El servicio de rutas tardó demasiado. Intenta de nuevo.'
            : 'No se pudo calcular el trazado por calles. Intenta de nuevo en unos segundos.', 'error');
    } finally {
        BustrakerMap.ocultarCargando();
    }
}

function actualizarEtiquetasBarrios() {
    if (!map) return;

    barrioLabelMarkers.forEach(marker => {
        if (map.hasLayer(marker)) {
            map.removeLayer(marker);
        }
    });
    barrioLabelMarkers = [];
}

function limpiarMapa() { rutaSolicitud++; if (origenMarker) { map.removeLayer(origenMarker); origenMarker = null; } if (destinoMarker) { map.removeLayer(destinoMarker); destinoMarker = null; } if (rutaLine) { map.removeLayer(rutaLine); rutaLine = null; } if (rutaGlow) { map.removeLayer(rutaGlow); rutaGlow = null; } if (routeFlow) { map.removeLayer(routeFlow); routeFlow = null; } barrioMarkers.forEach(m => map.removeLayer(m)); barrioMarkers = []; stopMarkers.forEach(m => map.removeLayer(m)); stopMarkers = []; const statsEl = document.getElementById('rutaStats'); if (statsEl) statsEl.remove(); actualizarEtiquetasBarrios(); establecerTrayecto(null); }
