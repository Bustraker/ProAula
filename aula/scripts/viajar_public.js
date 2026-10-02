// Copia de script extraído de viajar_public.html
// Desarrollo: el servicio usa static/js/viajar_public.js

// Variables globales
var map;
var rutas = [];
var barrios = [];
var barriosData = [];
var coordenadasCache = {};
var origenMarker = null;
var destinoMarker = null;
var rutaLine = null;
var barrioMarkers = [];

// Inicializar aplicación
document.addEventListener('DOMContentLoaded', function() {
    console.log('🚀 Iniciando Bustraker Público...');
    cargarBarrios();
    cargarRutas();
    initMap();
});

function initMap() {
    map = L.map('map').setView([10.4236, -75.5478], 13);
    L.tileLayer('/api/map/tiles?z={z}&x={x}&y={y}', {
        attribution: '&copy; OpenStreetMap contributors &copy; CARTO',
        maxZoom: 20
    }).addTo(map);
    console.log('✅ Mapa inicializado');
}

async function cargarBarrios() {
    try {
        const response = await fetch('/api/barrios/todos');
        if (response.ok) {
            const data = await response.json();
            barriosData = data;
            barrios = barriosData.map(b => b.nombre);
            console.log('✅', barrios.length, 'barrios cargados');

            barriosData.forEach(barrio => {
                const key = normalizarNombre(barrio.nombre);
                coordenadasCache[key] = {
                    lat: Number.parseFloat(barrio.latitud),
                    lng: Number.parseFloat(barrio.longitud)
                };
            });

            const origenSelect = document.getElementById('origen');
            origenSelect.innerHTML = '<option value="">Selecciona tu barrio actual</option>';
            [...barrios].sort().forEach(barrio => {
                const opt = document.createElement('option');
                opt.value = barrio;
                opt.textContent = barrio.charAt(0).toUpperCase() + barrio.slice(1);
                origenSelect.appendChild(opt);
            });

            const detectarBtn = document.getElementById('btnDetectarUbicacion');
            if (detectarBtn) {
                detectarBtn.disabled = false;
                detectarBtn.style.opacity = '1';
                detectarBtn.style.cursor = 'pointer';
            }
        }
    } catch (error) {
        console.error('❌ Error cargando barrios:', error);
    }
}

async function cargarRutas() {
    try {
        const response = await fetch('/api/rutas-con-coordenadas');
        if (response.ok) {
            rutas = await response.json();
            console.log('✅', rutas.length, 'rutas cargadas');
        }
    } catch (error) {
        console.error('❌ Error cargando rutas:', error);
    }
}

function normalizarNombre(nombre) {
    return nombre.toLowerCase()
        .normalize('NFD')
        .replace(/[\u0300-\u036f]/g, '')
        .trim();
}

function obtenerCoordenadas(barrio) {
    return coordenadasCache[normalizarNombre(barrio)] || null;
}

function distanciaHaversine(lat1, lng1, lat2, lng2) {
    const R = 6371;
    const dLat = (lat2 - lat1) * Math.PI / 180;
    const dLng = (lng2 - lng1) * Math.PI / 180;
    const a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
        Math.cos(lat1 * Math.PI / 180) * Math.cos(lat2 * Math.PI / 180) *
        Math.sin(dLng / 2) * Math.sin(dLng / 2);
    const c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    return R * c;
}

function obtenerBarrioMasCercano(lat, lng) {
    if (!barriosData.length) return null;
    let mejorBarrio = null;
    let distanciaMinima = Infinity;
    barriosData.forEach(barrio => {
        const latBarrio = Number.parseFloat(barrio.latitud);
        const lngBarrio = Number.parseFloat(barrio.longitud);
        if (Number.isNaN(latBarrio) || Number.isNaN(lngBarrio)) return;
        const distancia = distanciaHaversine(lat, lng, latBarrio, lngBarrio);
        if (distancia < distanciaMinima) {
            distanciaMinima = distancia;
            mejorBarrio = barrio;
        }
    });
    return mejorBarrio;
}

function actualizarDestinosDisponibles() {
    const origen = document.getElementById('origen').value;
    const destinoSelect = document.getElementById('destino');
    const rutaSelect = document.getElementById('ruta');
    if (origen) {
        destinoSelect.disabled = false;
        destinoSelect.innerHTML = '<option value="">Selecciona tu barrio de destino</option>';
        [...barrios].sort().forEach(barrio => {
            if (barrio !== origen) {
                const opt = document.createElement('option');
                opt.value = barrio;
                opt.textContent = barrio.charAt(0).toUpperCase() + barrio.slice(1);
                destinoSelect.appendChild(opt);
            }
        });
        rutaSelect.disabled = true;
        rutaSelect.innerHTML = '<option value="">Primero selecciona origen y destino</option>';
        document.getElementById('rutaInfo').style.display = 'none';
    } else {
        destinoSelect.disabled = true;
        destinoSelect.innerHTML = '<option value="">Primero selecciona tu barrio de origen</option>';
    }
}

function buscarRutasDisponibles() {
    const origen = document.getElementById('origen').value;
    const destino = document.getElementById('destino').value;
    const rutaSelect = document.getElementById('ruta');
    if (origen && destino) {
        const rutasFiltradas = rutas.filter(ruta => {
            const barriosRuta = ruta.barrios || [];
            const tieneOrigen = barriosRuta.some(b => normalizarNombre(b) === normalizarNombre(origen));
            const tieneDestino = barriosRuta.some(b => normalizarNombre(b) === normalizarNombre(destino));
            return tieneOrigen && tieneDestino;
        });
        if (rutasFiltradas.length === 0) {
            const origenCoords = obtenerCoordenadas(origen);
            const destinoCoords = obtenerCoordenadas(destino);
            if (origenCoords && destinoCoords) {
                rutasFiltradas.push({
                    id: 'directa-' + origen + '-' + destino,
                    nombre: 'Ruta directa ' + origen + ' → ' + destino,
                    barrios: [origen, destino],
                    horaAproximada: 'Directa',
                    coordenadasBarrios: {
                        [origen]: [origenCoords.lat, origenCoords.lng],
                        [destino]: [destinoCoords.lat, destinoCoords.lng]
                    }
                });
            }
        }
        rutaSelect.disabled = false;
        rutaSelect.innerHTML = '<option value="">Selecciona una ruta</option>';
        if (rutasFiltradas.length === 0) {
            const opt = document.createElement('option'); opt.value = ''; opt.textContent = 'No hay rutas disponibles entre estos barrios'; opt.disabled = true; rutaSelect.appendChild(opt); return;
        }
        rutasFiltradas.forEach((ruta, idx) => {
            const opt = document.createElement('option');
            opt.value = idx;
            opt.textContent = `${ruta.nombre} · ${ruta.horaAproximada || 'Horario no disponible'}`;
            opt.setAttribute('data-ruta-json', JSON.stringify(ruta));
            rutaSelect.appendChild(opt);
        });
    } else {
        rutaSelect.disabled = true;
        rutaSelect.innerHTML = '<option value="">Primero selecciona origen y destino</option>';
    }
}

async function mostrarRutaEnMapa() {
    const rutaSelect = document.getElementById('ruta');
    const selectedOption = rutaSelect.options[rutaSelect.selectedIndex];
    const rutaJson = selectedOption ? selectedOption.getAttribute('data-ruta-json') : null;
    if (!rutaJson) return;
    const ruta = JSON.parse(rutaJson);
    document.getElementById('infoBarrios').innerHTML = ruta.barrios.map(b => '<span class="badge">' + b + '</span>').join('');
    document.getElementById('infoHora').textContent = ruta.horaAproximada || 'No especificada';
    document.getElementById('rutaInfo').style.display = 'block';
    limpiarMapa();
    await mostrarRutaEnMapaConCoords(ruta);
}

async function mostrarRutaEnMapaConCoords(ruta) {
    const origen = document.getElementById('origen').value;
    const destino = document.getElementById('destino').value;
    const coordenadasRuta = ruta.coordenadasBarrios;
    if (!coordenadasRuta) { alert('Esta ruta no tiene coordenadas disponibles'); return; }
    const waypoints = ruta.barrios.map(barrio => { const coords = coordenadasRuta[barrio]; return coords ? { lat: parseFloat(coords[0]), lng: parseFloat(coords[1]), nombre: barrio } : null; }).filter(Boolean);
    if (waypoints.length < 2) { alert('Esta ruta no tiene suficientes coordenadas para trazar el recorrido'); return; }
    waypoints.forEach((wp, i) => {
        const esOrigen = wp.nombre === origen; const esDestino = wp.nombre === destino;
        const color = esOrigen ? '#e74c3c' : esDestino ? '#27ae60' : '#3498db';
        const icono = esOrigen ? '🔴 Origen' : esDestino ? '🟢 Destino' : '🔵 Parada';
        const circleMarker = L.circleMarker([wp.lat, wp.lng], { radius: 9, fillColor: color, color: '#fff', weight: 2, fillOpacity: 0.95 }).addTo(map).bindPopup(`<strong>${icono}</strong><br>${wp.nombre}`);
        if (esOrigen) { circleMarker.openPopup(); origenMarker = circleMarker; } else if (esDestino) { destinoMarker = circleMarker; } else { barrioMarkers.push(circleMarker); }
    });
    const coordStr = waypoints.map(wp => `${wp.lng},${wp.lat}`).join(';');
    const osrmUrl = `https://router.project-osrm.org/route/v1/driving/${coordStr}?overview=full&geometries=geojson&steps=false`;
    const infoBarriosEl = document.getElementById('infoBarrios');
    const textoOriginal = infoBarriosEl.innerHTML; infoBarriosEl.innerHTML += ' <em style="color:#888">(calculando ruta…)</em>';
    try {
        const response = await fetch(osrmUrl);
        if (!response.ok) throw new Error('OSRM respondió con error ' + response.status);
        const data = await response.json();
        if (!data.routes || data.routes.length === 0) throw new Error('Sin ruta disponible');
        const routeGeoJSON = data.routes[0].geometry; const distanciaKm = (data.routes[0].distance / 1000).toFixed(1); const tiempoMin = Math.round(data.routes[0].duration / 60);
        rutaLine = L.geoJSON(routeGeoJSON, { style: { color: '#1e3a5f', weight: 8, opacity: 0.92, lineJoin: 'round', lineCap: 'round' } }).addTo(map);
        map.fitBounds(rutaLine.getBounds(), { padding: [60, 60] });
        infoBarriosEl.innerHTML = textoOriginal;
        let statsEl = document.getElementById('rutaStats'); if (!statsEl) { statsEl = document.createElement('p'); statsEl.id = 'rutaStats'; statsEl.style.marginTop = '10px'; statsEl.style.color = '#25304a'; document.getElementById('rutaInfo').appendChild(statsEl); }
        statsEl.innerHTML = `<strong>📏 Distancia aproximada:</strong> ${distanciaKm} km &nbsp; <strong>⏱️ Tiempo estimado:</strong> ~${tiempoMin} min`;
    } catch (err) {
        console.error('❌ Error OSRM:', err);
        const latlngs = waypoints.map(wp => [wp.lat, wp.lng]);
        rutaLine = L.polyline(latlngs, { color: '#e67e22', weight: 5, dashArray: '12, 8', opacity: 0.9 }).addTo(map);
        map.fitBounds(rutaLine.getBounds(), { padding: [60, 60] });
        infoBarriosEl.innerHTML = textoOriginal;
        let statsEl = document.getElementById('rutaStats'); if (!statsEl) { statsEl = document.createElement('p'); statsEl.id = 'rutaStats'; statsEl.style.marginTop = '10px'; statsEl.style.color = '#25304a'; document.getElementById('rutaInfo').appendChild(statsEl); }
        statsEl.innerHTML = '<strong>⚠️ Ruta aproximada</strong> — no se pudo cargar el trazado real en este momento.';
    }
}

function limpiarMapa() {
    if (origenMarker) { map.removeLayer(origenMarker); origenMarker = null; }
    if (destinoMarker) { map.removeLayer(destinoMarker); destinoMarker = null; }
    if (rutaLine) { map.removeLayer(rutaLine); rutaLine = null; }
    barrioMarkers.forEach(m => map.removeLayer(m)); barrioMarkers = [];
    const statsEl = document.getElementById('rutaStats'); if (statsEl) statsEl.remove();
}

function usarUbicacionActual() {
    if (!barriosData.length) { alert('⏳ Cargando barrios, por favor espera unos segundos e intenta de nuevo.'); return; }
    if (navigator.geolocation) {
        navigator.geolocation.getCurrentPosition(position => {
            const lat = position.coords.latitude; const lng = position.coords.longitude;
            map.setView([lat, lng], 15);
            if (origenMarker) map.removeLayer(origenMarker);
            origenMarker = L.marker([lat, lng]).addTo(map).bindPopup('<b>📍 Tu ubicación actual</b>').openPopup();
            const barrioCercano = obtenerBarrioMasCercano(lat, lng);
            if (barrioCercano) {
                const origenSelect = document.getElementById('origen'); origenSelect.value = barrioCercano.nombre; actualizarDestinosDisponibles(); origenMarker.bindPopup('<b>📍 Tu ubicación actual</b><br><small>' + barrioCercano.nombre + '</small>').openPopup(); alert('📍 Ubicación detectada en ' + barrioCercano.nombre + '. Origen seleccionado automáticamente.');
            } else { alert('📍 Ubicación detectada. Ahora selecciona tu destino.'); }
        }, error => { alert('❌ No se pudo detectar la ubicación: ' + error.message); });
    } else { alert('❌ Tu navegador no soporta geolocalización'); }
}
