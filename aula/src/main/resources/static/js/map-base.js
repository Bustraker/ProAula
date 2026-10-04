/*
 * Bustraker · Núcleo común de mapas (Leaflet)
 *
 * Lo usan viajar.js (privado) y viajar_public.js (público). Centraliza:
 *   - El proveedor de mapas base (un SOLO lugar para cambiarlo).
 *   - Controles del mapa (zoom, centrar, capas, escala).
 *   - Marcadores, avisos, indicador de carga y estadísticas de ruta.
 *
 * ──────────────────────────────────────────────────────────────────────
 *  MOSAICOS Y CLAVE DEL PROVEEDOR
 *  Los mosaicos de CARTO se solicitan al backend para no exponer su clave.
 *  Si el backend no está disponible, el mapa cambia automáticamente a OSM.
 * ──────────────────────────────────────────────────────────────────────
 */
(function (global) {
    'use strict';

    /* ===================== CONFIGURACIÓN ===================== */
    var CENTRO = [10.4236, -75.5478];             // Cartagena de Indias
    var ZOOM_INICIAL = 13;
    var MAPA_BASE_PREDETERMINADO = 'Calles';
    var CLAVE_PREFERENCIA = 'bustraker.mapaBase';

    var ATRIB_OSM = '&copy; <a href="https://www.openstreetmap.org/copyright" target="_blank" rel="noopener">OpenStreetMap</a>';
    var ATRIB_CARTO = ATRIB_OSM + ' &copy; <a href="https://carto.com/attributions" target="_blank" rel="noopener">CARTO</a>';
    var ATRIB_ESRI = 'Tiles &copy; <a href="https://www.esri.com" target="_blank" rel="noopener">Esri</a>, HERE, Garmin, OpenStreetMap';
    var ESRI = 'https://server.arcgisonline.com/ArcGIS/rest/services/';

    /* ===================== UTILIDADES ===================== */
    function escapeHtml(valor) {
        return String(valor == null ? '' : valor)
            .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;').replace(/'/g, '&#39;');
    }

    function leerPreferencia() {
        try { return global.localStorage.getItem(CLAVE_PREFERENCIA); } catch (e) { return null; }
    }

    function guardarPreferencia(nombre) {
        try { global.localStorage.setItem(CLAVE_PREFERENCIA, nombre); } catch (e) { /* modo privado */ }
    }

    async function fetchConTimeout(url, ms) {
        var controlador = new AbortController();
        var temporizador = setTimeout(function () { controlador.abort(); }, ms || 15000);
        try {
            return await fetch(url, { signal: controlador.signal });
        } finally {
            clearTimeout(temporizador);
        }
    }

    async function fetchJSON(url, ms) {
        var respuesta = await fetchConTimeout(url, ms || 15000);
        if (!respuesta.ok) throw new Error('HTTP ' + respuesta.status);
        return respuesta.json();
    }

    /* ===================== MAPAS BASE ===================== */
    function gris(variante) {
        // variante: 'Dark' | 'Light'. Base + capa de etiquetas encima.
        var opc = { maxNativeZoom: 16, maxZoom: 18 };
        return L.layerGroup([
            L.tileLayer(ESRI + 'Canvas/World_' + variante + '_Gray_Base/MapServer/tile/{z}/{y}/{x}',
                Object.assign({ zIndex: 1, attribution: ATRIB_ESRI }, opc)),
            L.tileLayer(ESRI + 'Canvas/World_' + variante + '_Gray_Reference/MapServer/tile/{z}/{y}/{x}',
                Object.assign({ zIndex: 2 }, opc))
        ]);
    }

    function crearMapasBase() {
        var bases = {
            Oscuro: gris('Dark'),
            Claro: gris('Light'),
            Calles: L.tileLayer('/api/map/tiles?z={z}&x={x}&y={y}', {
                maxZoom: 20, attribution: ATRIB_CARTO
            }),
            OpenStreetMap: L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png', {
                maxZoom: 19, attribution: ATRIB_OSM
            })
        };
        bases['Satélite'] = L.tileLayer(ESRI + 'World_Imagery/MapServer/tile/{z}/{y}/{x}', {
            maxNativeZoom: 18, maxZoom: 19, attribution: ATRIB_ESRI
        });
        return bases;
    }

    function capaPrincipal(capa) {
        return capa.getLayers ? capa.getLayers()[0] : capa;
    }

    /* ===================== CONTROLES ===================== */
    var ControlVista = L.Control.extend({
        options: { position: 'topleft', obtenerLimites: null },
        onAdd: function (mapa) {
            var self = this;
            var contenedor = L.DomUtil.create('div', 'leaflet-bar leaflet-control mu-control');
            var enlace = L.DomUtil.create('a', '', contenedor);
            enlace.href = '#';
            enlace.title = 'Centrar el mapa';
            enlace.setAttribute('role', 'button');
            enlace.setAttribute('aria-label', 'Centrar el mapa');
            enlace.innerHTML = '<i class="fas fa-expand" aria-hidden="true"></i>';
            L.DomEvent.disableClickPropagation(contenedor);
            L.DomEvent.on(enlace, 'click', function (e) {
                L.DomEvent.preventDefault(e);
                var limites = self.options.obtenerLimites ? self.options.obtenerLimites() : null;
                if (limites && limites.isValid()) {
                    ajustarVista(mapa, limites);
                } else {
                    mapa.flyTo(CENTRO, ZOOM_INICIAL, { duration: 0.8 });
                }
            });
            return contenedor;
        }
    });

    /**
     * Crea el mapa con todos los controles.
     * opciones.obtenerLimites: función que devuelve L.LatLngBounds de lo que se está
     * mostrando (ruta/trazado) o null; la usa el botón "Centrar el mapa".
     */
    function crearMapa(idContenedor, opciones) {
        opciones = opciones || {};
        var mapa = L.map(idContenedor, {
            center: CENTRO,
            zoom: ZOOM_INICIAL,
            minZoom: 10,
            zoomControl: false,
            zoomSnap: 0.5,
            zoomDelta: 1
        });
        mapa.attributionControl.setPrefix('<a href="https://leafletjs.com" target="_blank" rel="noopener">Leaflet</a>');

        L.control.zoom({ position: 'topleft', zoomInTitle: 'Acercar', zoomOutTitle: 'Alejar' }).addTo(mapa);
        new ControlVista({ obtenerLimites: opciones.obtenerLimites }).addTo(mapa);

        var bases = crearMapasBase();
        var nombre = leerPreferencia();
        if (!bases[nombre]) nombre = MAPA_BASE_PREDETERMINADO;
        var activa = bases[nombre];
        activa.addTo(mapa);

        L.control.layers(bases, null, { position: 'topleft', collapsed: true }).addTo(mapa);
        L.control.scale({ imperial: false, position: 'bottomleft', maxWidth: 110 }).addTo(mapa);

        mapa.on('baselayerchange', function (e) {
            activa = e.layer;
            guardarPreferencia(e.name);
        });

        // Si el proveedor activo no responde (sin internet, bloqueo, caída), pasamos a OpenStreetMap.
        Object.keys(bases).forEach(function (clave) {
            if (clave === 'OpenStreetMap') return;
            var capa = bases[clave];
            var cargados = 0, errores = 0, avisado = false;
            var tiles = capaPrincipal(capa);
            tiles.on('tileload', function () { cargados++; });
            tiles.on('tileerror', function () {
                errores++;
                if (avisado || cargados > 0 || errores < 4 || activa !== capa) return;
                avisado = true;
                mapa.removeLayer(capa);
                bases.OpenStreetMap.addTo(mapa);
                activa = bases.OpenStreetMap;
                guardarPreferencia('OpenStreetMap');
                mostrarAviso('El mapa «' + clave + '» no respondió. Se cambió a OpenStreetMap.', 'warn', 7000);
            });
        });

        if (global.ResizeObserver) {
            new ResizeObserver(function () { mapa.invalidateSize({ pan: false }); }).observe(mapa.getContainer());
        } else {
            global.addEventListener('resize', function () { mapa.invalidateSize({ pan: false }); });
        }
        return mapa;
    }

    /**
     * fitBounds que evita que la ruta quede tapada por el panel de opciones.
     */
    function ajustarVista(mapa, limites) {
        if (!limites || !limites.isValid()) return;
        var margen = 56;
        var opciones = { paddingTopLeft: [margen, margen], paddingBottomRight: [margen, margen], maxZoom: 17 };
        var panel = document.querySelector('.options-panel');
        if (panel) {
            var caja = panel.getBoundingClientRect();
            var ancho = global.innerWidth, alto = global.innerHeight;
            if (ancho > 700) {
                opciones.paddingBottomRight = [margen + Math.max(0, ancho - caja.left), margen];
            } else if (caja.top > alto / 2) {
                opciones.paddingBottomRight = [margen, margen + Math.max(0, alto - caja.top)];
            } else {
                opciones.paddingTopLeft = [margen, Math.min(caja.bottom, alto * 0.6) + 16];
            }
        }
        mapa.fitBounds(limites, opciones);
    }

    /* ===================== MARCADORES ===================== */
    function contenidoPopup(titulo, nombre) {
        return '<div class="mu-popup"><span class="mu-popup__titulo">' + escapeHtml(titulo) +
            '</span><strong>' + escapeHtml(nombre) + '</strong></div>';
    }

    /** Pin con letra: tipo 'origen' (A, verde) o 'destino' (B, rojo). */
    function crearPin(lat, lng, tipo, nombre) {
        var esOrigen = tipo === 'origen';
        var titulo = esOrigen ? 'Origen' : 'Destino';
        var icono = L.divIcon({
            className: 'mu-pin-contenedor',
            html: '<span class="mu-pin mu-pin--' + tipo + '"><b>' + (esOrigen ? 'A' : 'B') + '</b></span>',
            iconSize: [34, 34],
            iconAnchor: [17, 41],
            tooltipAnchor: [0, -48],
            popupAnchor: [0, -48]
        });
        return L.marker([lat, lng], { icon: icono, riseOnHover: true, keyboard: true, title: titulo + ': ' + nombre })
            .bindTooltip('<b>' + titulo + '</b> · ' + escapeHtml(nombre), {
                permanent: true, direction: 'top', className: 'mu-tooltip mu-tooltip--' + tipo
            })
            .bindPopup(contenidoPopup(titulo + ' del tramo', nombre));
    }

    /** Punto pequeño para paradas / barrios intermedios. */
    function crearPuntoIntermedio(lat, lng, titulo, nombre) {
        var punto = L.circleMarker([lat, lng], {
            radius: 6, weight: 2, color: '#ffffff', fillColor: '#38bdf8', fillOpacity: 1
        })
            .bindTooltip(escapeHtml(nombre), { direction: 'top', offset: [0, -6], className: 'mu-tooltip' })
            .bindPopup(contenidoPopup(titulo, nombre));
        punto.on('mouseover', function () { punto.setRadius(8); });
        punto.on('mouseout', function () { punto.setRadius(6); });
        return punto;
    }

    function crearMarcadorParada(lat, lng, numero, datos) {
        datos = datos || {};
        var esParada = datos.tipo !== 'barrio';
        var icono = L.divIcon({
            className: 'mu-parada-contenedor',
            html: '<span class="mu-parada' + (esParada ? '' : ' mu-parada--barrio') + '">' +
                escapeHtml(numero) + '</span>',
            iconSize: [26, 26],
            iconAnchor: [13, 13],
            popupAnchor: [0, -14],
            tooltipAnchor: [0, -14]
        });
        var detalle = [];
        if (datos.barrio) detalle.push('<span>Barrio: ' + escapeHtml(datos.barrio) + '</span>');
        if (datos.ubicacion) detalle.push('<span>' + escapeHtml(datos.ubicacion) + '</span>');
        if (datos.referencia) detalle.push('<span>Ref.: ' + escapeHtml(datos.referencia) + '</span>');
        if (!esParada) detalle.push('<span class="mu-popup__nota">Referencia al centro del barrio; no hay parada física registrada.</span>');
        var titulo = (esParada ? 'Parada ' : 'Barrio ') + numero;
        return L.marker([lat, lng], { icon: icono, keyboard: true, title: titulo + ': ' + (datos.nombre || '') })
            .bindTooltip(escapeHtml(datos.nombre || titulo), { direction: 'top', className: 'mu-tooltip' })
            .bindPopup('<div class="mu-popup"><span class="mu-popup__titulo">' + titulo + '</span><strong>' +
                escapeHtml(datos.nombre || '') + '</strong>' + detalle.join('') + '</div>');
    }

    /** Decide qué punto es el origen y cuál el destino (por nombre; si no hay coincidencia, primero y último). */
    function ubicarExtremos(puntos, origen, destino, normalizar) {
        var o = -1, d = -1;
        puntos.forEach(function (p, i) {
            var n = normalizar(p.nombre || '');
            if (o < 0 && n === normalizar(origen || '')) o = i;
            if (n === normalizar(destino || '')) d = i;
        });
        if (o < 0) o = 0;
        if (d < 0 || d === o) d = puntos.length - 1;
        return { origen: o, destino: d };
    }

    /* ===================== RUTA POR CALLES (OSRM) ===================== */
    async function obtenerRutaOSRM(waypoints) {
        var coords = waypoints.map(function (p) { return p.lng + ',' + p.lat; }).join(';');
        var url = 'https://router.project-osrm.org/route/v1/driving/' + coords +
            '?overview=full&geometries=geojson&steps=false';
        var respuesta = await fetchConTimeout(url, 15000);
        if (!respuesta.ok) throw new Error('OSRM respondió con error ' + respuesta.status);
        var datos = await respuesta.json();
        if (!datos.routes || datos.routes.length === 0) throw new Error('Sin ruta disponible');
        var ruta = datos.routes[0];
        return { geometry: ruta.geometry, distanciaKm: ruta.distance / 1000, tiempoMin: Math.round(ruta.duration / 60) };
    }

    /** Devuelve { base, linea }: borde+resplandor y línea principal de la ruta. */
    function crearCapasRuta(geometry) {
        var redondo = { lineJoin: 'round', lineCap: 'round' };
        var borde = L.geoJSON(geometry, { style: Object.assign({ color: '#04161a', weight: 12, opacity: 0.8 }, redondo), interactive: false });
        var brillo = L.geoJSON(geometry, { style: Object.assign({ color: '#20c6cf', weight: 22, opacity: 0.16 }, redondo), interactive: false });
        var linea = L.geoJSON(geometry, { style: Object.assign({ color: '#35d1d5', weight: 6, opacity: 0.98 }, redondo) });
        return { base: L.layerGroup([brillo, borde]), linea: linea };
    }

    /* ===================== INTERFAZ: AVISOS, CARGA, ESTADÍSTICAS ===================== */
    function mostrarAviso(mensaje, tipo, duracion) {
        var region = document.getElementById('mu-avisos');
        if (!region) {
            region = document.createElement('div');
            region.id = 'mu-avisos';
            region.className = 'mu-avisos';
            region.setAttribute('role', 'status');
            region.setAttribute('aria-live', 'polite');
            document.body.appendChild(region);
        }
        while (region.children.length >= 3) region.removeChild(region.firstChild);

        var iconos = { error: 'fa-circle-exclamation', warn: 'fa-triangle-exclamation', ok: 'fa-circle-check' };
        var aviso = document.createElement('div');
        aviso.className = 'mu-aviso mu-aviso--' + (tipo || 'info');
        var icono = document.createElement('i');
        icono.className = 'fas ' + (iconos[tipo] || 'fa-circle-info');
        icono.setAttribute('aria-hidden', 'true');
        var texto = document.createElement('span');
        texto.textContent = mensaje;
        aviso.appendChild(icono);
        aviso.appendChild(texto);
        region.appendChild(aviso);

        var cerrar = function () {
            aviso.classList.add('mu-aviso--salida');
            setTimeout(function () { aviso.remove(); }, 220);
        };
        aviso.addEventListener('click', cerrar);
        setTimeout(cerrar, duracion || 5500);
    }

    var cargasActivas = 0;
    var indicador = null;

    function mostrarCargando(texto) {
        cargasActivas++;
        if (!indicador) {
            indicador = document.createElement('div');
            indicador.className = 'mu-cargando';
            indicador.setAttribute('role', 'status');
            indicador.innerHTML = '<span class="mu-spinner" aria-hidden="true"></span><span class="mu-cargando__texto"></span>';
            document.body.appendChild(indicador);
        }
        indicador.querySelector('.mu-cargando__texto').textContent = texto || 'Cargando…';
        indicador.hidden = false;
    }

    function ocultarCargando() {
        cargasActivas = Math.max(0, cargasActivas - 1);
        if (cargasActivas === 0 && indicador) indicador.hidden = true;
    }

    /**
     * Muestra (o actualiza) las estadísticas dentro de #idContenedor, en un elemento #rutaStats.
     * datos: { distanciaKm, tiempoMin, puntos } o { error: true }
     */
    function mostrarEstadisticas(idContenedor, datos) {
        var contenedor = document.getElementById(idContenedor);
        if (!contenedor) return;
        var el = document.getElementById('rutaStats');
        if (!el) {
            el = document.createElement('div');
            el.id = 'rutaStats';
            el.setAttribute('role', 'status');
            contenedor.appendChild(el);
        }
        if (datos.error) {
            el.className = 'mu-stats mu-stats--error';
            el.textContent = 'No se pudo calcular el trazado por calles. No se muestra una línea aproximada para evitar confundirla con el recorrido del bus.';
            return;
        }
        el.className = 'mu-stats';
        el.innerHTML =
            '<div class="mu-stat"><b>' + datos.distanciaKm.toFixed(1) + ' km</b><small>Distancia</small></div>' +
            '<div class="mu-stat"><b>~' + datos.tiempoMin + ' min</b><small>En auto</small></div>' +
            '<div class="mu-stat"><b>' + datos.puntos + '</b><small>Puntos</small></div>';
    }

    global.BustrakerMap = {
        CENTRO: CENTRO,
        escapeHtml: escapeHtml,
        fetchJSON: fetchJSON,
        crearMapa: crearMapa,
        ajustarVista: ajustarVista,
        crearPin: crearPin,
        crearPuntoIntermedio: crearPuntoIntermedio,
        crearMarcadorParada: crearMarcadorParada,
        ubicarExtremos: ubicarExtremos,
        obtenerRutaOSRM: obtenerRutaOSRM,
        crearCapasRuta: crearCapasRuta,
        mostrarAviso: mostrarAviso,
        mostrarCargando: mostrarCargando,
        ocultarCargando: ocultarCargando,
        mostrarEstadisticas: mostrarEstadisticas
    };
})(window);
