var map;
var barrios = [];
var barriosData = [];
var buses = [];
var rutas = [];
var coordenadasCache = {};
var origenMarker = null;
var destinoMarker = null;
var rutaLine = null;
var rutaGlow = null;
var barrioMarkers = [];
var stopMarkers = [];
var mostrarParadas = true;
var busSeleccionado = null;

function initMap() {
    map = L.map('map').setView([10.4236, -75.5478], 13);
    L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
        attribution: '© OpenStreetMap contributors'
    }).addTo(map);
    // Ensure the map redraws correctly when the container is fully available.
    setTimeout(() => {
        if (map) {
            map.invalidateSize();
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
                const nombreNorm = normalizarNombre(barrio.nombre);
                coordenadasCache[nombreNorm] = {
                    lat: parseFloat(barrio.latitud),
                    lng: parseFloat(barrio.longitud)
                };
            });
            const origenSelect = document.getElementById('origen');
            origenSelect.innerHTML = '<option value="">Selecciona tu barrio actual</option>';
            barrios.sort().forEach(barrio => {
                const opt = document.createElement('option');
                opt.value = barrio;
                opt.textContent = barrio.charAt(0).toUpperCase() + barrio.slice(1);
                origenSelect.appendChild(opt);
            });
        }
    } catch (error) {
        console.error('❌ Error cargando barrios:', error);
    }
}

async function cargarBusesYRutas() {
    try {
        const [busesRes, rutasRes] = await Promise.all([
            fetch('/api/buses/todos'),
            fetch('/api/rutas-con-coordenadas')
        ]);
        if (busesRes.ok) {
            buses = await busesRes.json();
        }
        if (rutasRes.ok) {
            rutas = await rutasRes.json();
        }
    } catch (error) {
        console.error('❌ Error cargando buses/rutas:', error);
    }
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
        const latBarrio = parseFloat(barrio.latitud);
        const lngBarrio = parseFloat(barrio.longitud);
        if (Number.isNaN(latBarrio) || Number.isNaN(lngBarrio)) return;
        const distancia = distanciaHaversine(lat, lng, latBarrio, lngBarrio);
        if (distancia < distanciaMinima) {
            distanciaMinima = distancia;
            mejorBarrio = barrio;
        }
    });
    return mejorBarrio;
}

function obtenerCoordenadas(nombre) {
    return coordenadasCache[normalizarNombre(nombre)] || null;
}

function normalizarNombre(texto) {
    return texto.toString().trim().toLowerCase().normalize('NFD').replace(/\p{Diacritic}/gu, '');
}

function actualizarVisibilidadParadas() {
    if (mostrarParadas) {
        stopMarkers.forEach(marker => { if (!map.hasLayer(marker)) marker.addTo(map); });
    } else {
        stopMarkers.forEach(marker => { if (map.hasLayer(marker)) map.removeLayer(marker); });
    }
}

function crearMarkerParada(point, index, origen, destino) {
    const esOrigen = point.nombre === origen;
    const esDestino = point.nombre === destino;
    const color = esOrigen ? '#e74c3c' : esDestino ? '#27ae60' : '#38bdf8';
    const label = esOrigen ? '🔴 Origen' : esDestino ? '🟢 Destino' : `🔵 Parada ${index}`;
    const marker = L.circleMarker([point.lat, point.lng], {
        radius: esOrigen || esDestino ? 11 : 8,
        fillColor: color,
        color: '#ffffff',
        weight: 2,
        fillOpacity: 0.96
    }).bindPopup(`<strong>${label}</strong><br>${point.nombre}`)
      .bindTooltip(label, { direction: 'top', offset: [0, -10], opacity: 0.95 });

    marker.on('mouseover', () => marker.openPopup());
    marker.on('mouseout', () => marker.closePopup());

    if (mostrarParadas) {
        marker.addTo(map);
    }

    stopMarkers.push(marker);
    if (esOrigen) origenMarker = marker;
    if (esDestino) destinoMarker = marker;
    if (esOrigen || esDestino) marker.openPopup();
}

function toggleMostrarParadas(checked) {
    mostrarParadas = checked;
    actualizarVisibilidadParadas();
}

function limpiarMapa() {
    if (origenMarker) {
        map.removeLayer(origenMarker);
        origenMarker = null;
    }
    if (destinoMarker) {
        map.removeLayer(destinoMarker);
        destinoMarker = null;
    }
    if (rutaLine) {
        map.removeLayer(rutaLine);
        rutaLine = null;
    }
    barrioMarkers.forEach(marker => map.removeLayer(marker));
    barrioMarkers = [];
    stopMarkers.forEach(marker => map.removeLayer(marker));
    stopMarkers = [];
    const statsEl = document.getElementById('rutaStats');
    if (statsEl) statsEl.remove();
}

function actualizarDestinosDisponibles() {
    const origen = document.getElementById('origen').value;
    const destinoSelect = document.getElementById('destino');
    const busList = document.getElementById('busList');

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
        busList.innerHTML = '<p style="color: #757575; font-size: 0.9em; text-align: center; padding: 20px;">Selecciona destino para consultar rutas</p>';
        document.getElementById('rutaInfo').style.display = 'none';
        busSeleccionado = null;
    } else {
        destinoSelect.disabled = true;
        destinoSelect.innerHTML = '<option value="">Primero selecciona tu barrio de origen</option>';
    }
}

function buscarBusesDisponibles() {
    const origen = document.getElementById('origen').value;
    const destino = document.getElementById('destino').value;
    const busList = document.getElementById('busList');

    if (!origen || !destino) {
        busList.innerHTML = '<p style="color: #757575; font-size: 0.9em; text-align: center; padding: 20px;">Selecciona origen y destino</p>';
        return;
    }

    const origenNorm = normalizarNombre(origen);
    const destinoNorm = normalizarNombre(destino);
    const rutasFiltradas = rutas.filter(ruta => {
        const barriosNorm = ruta.barrios.map(b => normalizarNombre(b));
        return barriosNorm.includes(origenNorm) && barriosNorm.includes(destinoNorm);
    });

    if (rutasFiltradas.length === 0) {
        const origenCoords = obtenerCoordenadas(origen);
        const destinoCoords = obtenerCoordenadas(destino);
        if (origenCoords && destinoCoords) {
            const rutaDirecta = {
                id: 'directa-' + origen + '-' + destino,
                nombre: 'Ruta directa ' + origen + ' → ' + destino,
                barrios: [origen, destino],
                horaAproximada: 'Directa',
                coordenadasBarrios: {
                    [origen]: [origenCoords.lat, origenCoords.lng],
                    [destino]: [destinoCoords.lat, destinoCoords.lng]
                }
            };
            rutasFiltradas.push(rutaDirecta);
        } else {
            busList.innerHTML = '<p style="color: #d32f2f; font-size: 0.9em; text-align: center; padding: 20px;">❌ No hay rutas disponibles para esta combinación</p>';
            return;
        }
    }

    let busesDisponibles = buses.filter(bus => {
        return rutasFiltradas.some(ruta => ruta.id == bus.rutaId);
    });

    const esRutaDirecta = rutasFiltradas.length === 1 && typeof rutasFiltradas[0].id === 'string' && rutasFiltradas[0].id.startsWith('directa-');
    if (busesDisponibles.length === 0 && esRutaDirecta) {
        busesDisponibles = [{
            placa: 'Bus directo',
            conductor: 'Servicio directo',
            rutaId: rutasFiltradas[0].id
        }];
    }

    if (busesDisponibles.length === 0) {
        busList.innerHTML = '<p style="color: #d32f2f; font-size: 0.9em; text-align: center; padding: 20px;">❌ No hay buses asignados a esta ruta aún</p>';
        return;
    }

    busList.innerHTML = '';
    busesDisponibles.forEach(bus => {
        const ruta = rutasFiltradas.find(r => r.id == bus.rutaId);
        if (!ruta) return;
        const card = document.createElement('div');
        card.className = 'bus-card';
        card.onclick = event => seleccionarBus(bus, ruta, event);
        card.innerHTML = `
            <h4><i class="fas fa-route"></i> ${ruta.nombre}</h4>
            <p><strong>Bus:</strong> ${bus.placa}</p>
            <p><strong>Hora estimada:</strong> ${ruta.horaAproximada || 'N/A'}</p>
            <p><strong>Conductor:</strong> ${bus.conductor || 'N/A'}</p>
            <p style="margin-top: 10px; font-size: 0.9rem; color: #6b7a94;"><i class="fas fa-arrow-right"></i> Haz clic para ver la ruta en el mapa</p>
        `;
        busList.appendChild(card);
    });
}

function seleccionarBus(bus, ruta, event) {
    document.querySelectorAll('.bus-card').forEach(card => card.classList.remove('selected'));
    busSeleccionado = { bus, ruta };
    event.currentTarget.classList.add('selected');
    mostrarRutaEnMapa();
}

async function mostrarRutaEnMapa() {
    if (!busSeleccionado) {
        alert('⚠️ Primero selecciona un bus');
        return;
    }

    const { bus, ruta } = busSeleccionado;
    const infoBarriosEl = document.getElementById('infoBarrios');
    const infoRutaEl = document.getElementById('infoRuta');
    const infoBusEl = document.getElementById('infoBus');
    const rutaInfoEl = document.getElementById('rutaInfo');

    rutaInfoEl.style.display = 'block';
    infoBusEl.textContent = bus.placa;
    infoRutaEl.textContent = ruta.nombre;
    infoBarriosEl.textContent = ruta.barrios ? ruta.barrios.join(' → ') : 'Recorrido seleccionado';

    limpiarMapa();

    const origen = document.getElementById('origen').value;
    const destino = document.getElementById('destino').value;
    const coordenadasRuta = ruta.coordenadas || ruta.coordenadasBarrios || null;
    const waypoints = [];

    if (Array.isArray(coordenadasRuta) && coordenadasRuta.length > 0) {
        coordenadasRuta.forEach(c => {
            const lat = parseFloat(c.latitud);
            const lng = parseFloat(c.longitud);
            if (!Number.isNaN(lat) && !Number.isNaN(lng)) {
                waypoints.push({ lat, lng, nombre: c.nombre || '' });
            }
        });
    } else if (coordenadasRuta && typeof coordenadasRuta === 'object') {
        ruta.barrios.forEach(barrio => {
            const coords = coordenadasRuta[barrio];
            if (Array.isArray(coords) && coords.length === 2) {
                waypoints.push({ lat: parseFloat(coords[0]), lng: parseFloat(coords[1]), nombre: barrio });
            }
        });
    }

    if (waypoints.length < 2) {
        const origenCoords = obtenerCoordenadas(ruta.barrios[0]);
        const destinoCoords = obtenerCoordenadas(ruta.barrios[ruta.barrios.length - 1]);
        if (origenCoords && destinoCoords) {
            waypoints.length = 0;
            waypoints.push({ lat: origenCoords.lat, lng: origenCoords.lng, nombre: ruta.barrios[0] });
            waypoints.push({ lat: destinoCoords.lat, lng: destinoCoords.lng, nombre: ruta.barrios[ruta.barrios.length - 1] });
        }
    }

    if (waypoints.length < 2) {
        alert('⚠️ Esta ruta no tiene coordenadas suficientes para trazar el recorrido');
        return;
    }

    waypoints.forEach((point, index) => crearMarkerParada(point, index + 1, origen, destino));

    const coordStr = waypoints.map(point => `${point.lng},${point.lat}`).join(';');
    const osrmUrl = `https://router.project-osrm.org/route/v1/driving/${coordStr}?overview=full&geometries=geojson&steps=false`;
    const textoOriginal = infoBarriosEl ? infoBarriosEl.textContent : '';

    try {
        const response = await fetch(osrmUrl);
        if (!response.ok) throw new Error('OSRM respondió con error ' + response.status);
        const data = await response.json();
        if (!data.routes || data.routes.length === 0) throw new Error('Sin ruta disponible');

        const routeGeoJSON = data.routes[0].geometry;
        const distanciaKm = (data.routes[0].distance / 1000).toFixed(1);
        const tiempoMin = Math.round(data.routes[0].duration / 60);

        if (rutaGlow) { map.removeLayer(rutaGlow); rutaGlow = null; }
        rutaGlow = L.geoJSON(routeGeoJSON, {
            style: { color: '#60a5fa', weight: 20, opacity: 0.18, lineJoin: 'round', lineCap: 'round' }
        }).addTo(map);
        rutaLine = L.geoJSON(routeGeoJSON, {
            style: { color: '#1e3a5f', weight: 8, opacity: 0.95, lineJoin: 'round', lineCap: 'round' }
        }).addTo(map);
        map.fitBounds(rutaLine.getBounds(), { padding: [60, 60] });

        if (infoBarriosEl) infoBarriosEl.textContent = textoOriginal;
        let statsEl = document.getElementById('rutaStats');
        if (!statsEl) {
            statsEl = document.createElement('p');
            statsEl.id = 'rutaStats';
            statsEl.style.marginTop = '10px';
            statsEl.style.color = '#25304a';
            document.getElementById('rutaInfo').appendChild(statsEl);
        }
        statsEl.innerHTML = `<strong>📏 Distancia aproximada:</strong> ${distanciaKm} km &nbsp; <strong>⏱️ Tiempo estimado:</strong> ~${tiempoMin} min &nbsp; <strong>Paradas:</strong> ${waypoints.length}`;
    } catch (err) {
        console.error('❌ Error OSRM:', err);
        const latlngs = waypoints.map(point => [point.lat, point.lng]);
        if (rutaGlow) { map.removeLayer(rutaGlow); rutaGlow = null; }
        rutaGlow = L.polyline(latlngs, { color: '#fcd34d', weight: 18, opacity: 0.12, lineJoin: 'round', lineCap: 'round' }).addTo(map);
        rutaLine = L.polyline(latlngs, { color: '#e67e22', weight: 6, dashArray: '12, 8', opacity: 0.96, lineJoin: 'round', lineCap: 'round' }).addTo(map);
        map.fitBounds(rutaLine.getBounds(), { padding: [40, 40] });

        if (infoBarriosEl) infoBarriosEl.textContent = textoOriginal;
        let statsEl = document.getElementById('rutaStats');
        if (!statsEl) {
            statsEl = document.createElement('p');
            statsEl.id = 'rutaStats';
            statsEl.style.marginTop = '10px';
            statsEl.style.color = '#25304a';
            document.getElementById('rutaInfo').appendChild(statsEl);
        }
        statsEl.innerHTML = '<strong>⚠️ Ruta aproximada</strong> — no se pudo cargar el trazado real en este momento.';
    }
}

function usarUbicacionActual() {
    if (!navigator.geolocation) {
        alert('Tu navegador no soporta geolocalización');
        return;
    }

    navigator.geolocation.getCurrentPosition(position => {
        const lat = position.coords.latitude;
        const lng = position.coords.longitude;
        const barrioCercano = obtenerBarrioMasCercano(lat, lng);
        if (barrioCercano) {
            const origenSelect = document.getElementById('origen');
            origenSelect.value = barrioCercano.nombre;
            actualizarDestinosDisponibles();
            if (origenMarker) {
                map.removeLayer(origenMarker);
            }
            origenMarker = L.marker([lat, lng], { icon: L.icon({ iconUrl: 'https://unpkg.com/leaflet@1.9.4/dist/images/marker-icon.png', iconSize: [25, 41], iconAnchor: [12, 41] }) }).addTo(map);
            origenMarker.bindPopup(`Tu ubicación estimada: <strong>${barrioCercano.nombre}</strong>`).openPopup();
        } else {
            alert('No fue posible determinar tu barrio más cercano.');
        }
    }, error => {
        alert('Error al acceder a la ubicación: ' + error.message);
    });
}

document.addEventListener('DOMContentLoaded', function() {
    initMap();
    cargarBarrios();
    cargarBusesYRutas();
    // Attach UI event listeners (removed inline HTML handlers)
    document.getElementById('origen')?.addEventListener('change', actualizarDestinosDisponibles);
    document.getElementById('destino')?.addEventListener('change', buscarBusesDisponibles);
    document.getElementById('mostrarParadas')?.addEventListener('change', function(e) { toggleMostrarParadas(e.target.checked); });
    document.querySelectorAll('.js-detect-location').forEach(btn => btn.addEventListener('click', function(e) { e.preventDefault(); usarUbicacionActual(); }));
});
