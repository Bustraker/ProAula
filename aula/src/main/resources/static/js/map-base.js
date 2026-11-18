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
    var LIMITES_MAPA = [[9.9, -76.0], [10.9, -75.1]];
    var SERVIDORES_RUTA = [
        'https://router.project-osrm.org/route/v1/driving/',
        'https://routing.openstreetmap.de/routed-car/route/v1/driving/'
    ];
    var CACHE_RUTAS_MAX = 40;
    var cacheRutas = new Map();
    var ESTILOS_MAPA = [
        { id: 'Calles', etiqueta: 'Calles', icono: 'fa-road', muestra: '#c7d8df' },
        { id: 'Oscuro', etiqueta: 'Oscuro', icono: 'fa-moon', muestra: '#14252b' },
        { id: 'Claro', etiqueta: 'Claro', icono: 'fa-sun', muestra: '#e8eef0' },
        { id: 'Satélite', etiqueta: 'Satélite', icono: 'fa-earth-americas', muestra: '#254638' },
        { id: 'OpenStreetMap', etiqueta: 'Calles ESRI', icono: 'fa-map', muestra: '#d6e4c4' }
    ];

    var ATRIB_OSM = '&copy; <a href="https://www.openstreetmap.org/copyright" target="_blank" rel="noopener">OpenStreetMap</a>';
    var ATRIB_CARTO = ATRIB_OSM + ' &copy; <a href="https://carto.com/attributions" target="_blank" rel="noopener">CARTO</a>';
    var ATRIB_ESRI = 'Tiles &copy; <a href="https://www.esri.com" target="_blank" rel="noopener">Esri</a>, HERE, Garmin, OpenStreetMap';
    var ESRI = 'https://server.arcgisonline.com/ArcGIS/rest/services/';

    /* ===================== UTILIDADES ===================== */
    function urlAplicacion(ruta) {
        var meta = document.querySelector('meta[name="app-context-path"]');
        var contexto = meta ? meta.content.replace(/\/+$/, '') : '';
        return contexto + (ruta.charAt(0) === '/' ? ruta : '/' + ruta);
    }

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
        var opc = { maxNativeZoom: 16, maxZoom: 18, keepBuffer: 4, updateWhenIdle: false };
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
            Calles: L.tileLayer(urlAplicacion('/api/map/tiles') + '?z={z}&x={x}&y={y}', {
                maxZoom: 20, keepBuffer: 4, updateWhenIdle: false, attribution: ATRIB_CARTO
            }),
            OpenStreetMap: L.tileLayer(ESRI + 'World_Street_Map/MapServer/tile/{z}/{y}/{x}', {
                maxNativeZoom: 19, maxZoom: 19, keepBuffer: 4, attribution: ATRIB_ESRI
            })
        };
        bases['Satélite'] = L.tileLayer(ESRI + 'World_Imagery/MapServer/tile/{z}/{y}/{x}', {
            maxNativeZoom: 18, maxZoom: 19, keepBuffer: 4, attribution: ATRIB_ESRI
        });
        return bases;
    }

    function crearCapasCartografia(mapa) {
        var capas = [
            {
                etiqueta: 'Barrios · límites catastrales',
                ruta: '/data/cartografia/barrios-cartografia.geojson',
                color: '#14b8a6',
                tipo: 'Barrio',
                capa: L.layerGroup(),
                cargada: false,
                cargando: null
            },
            {
                etiqueta: 'Localidades · límites catastrales',
                ruta: '/data/cartografia/localidades-cartografia.geojson',
                color: '#f59e0b',
                tipo: 'Localidad',
                capa: L.layerGroup(),
                cargada: false,
                cargando: null
            }
        ];
        var atribucionAgregada = false;
        var superposiciones = {};

        capas.forEach(function (config) {
            superposiciones[config.etiqueta] = config.capa;
        });

        L.control.layers(null, superposiciones, {
            collapsed: true,
            position: 'topleft'
        }).addTo(mapa);

        mapa.on('overlayadd', function (evento) {
            var config = capas.find(function (item) { return item.capa === evento.layer; });
            if (!config || config.cargada || config.cargando) return;

            config.cargando = fetchJSON(urlAplicacion(config.ruta), 30000)
                .then(function (datos) {
                    if (!datos || datos.type !== 'FeatureCollection' || !Array.isArray(datos.features)) {
                        throw new Error('El archivo cartográfico no contiene una colección GeoJSON válida.');
                    }

                    L.geoJSON(datos, {
                        style: {
                            color: config.color,
                            weight: 1.5,
                            opacity: 0.9,
                            fillColor: config.color,
                            fillOpacity: 0.035
                        },
                        onEachFeature: function (feature, elemento) {
                            var propiedades = feature.properties || {};
                            var nombre = String(propiedades.nombre || '').trim();
                            if (!nombre) return;

                            elemento.bindTooltip(escapeHtml(nombre), {
                                sticky: true,
                                direction: 'auto',
                                className: 'mu-tooltip'
                            });

                            var texto = config.tipo === 'Barrio'
                                ? 'Barrio según cartografía catastral.'
                                : 'Localidad según cartografía catastral.';
                            var html = '<div class="mu-popup mu-popup--pro">' +
                                '<div class="mu-popup__banner mu-popup__banner--barrio">' +
                                '<i class="fas fa-map-location-dot" aria-hidden="true"></i>' +
                                '<span>' + escapeHtml(config.tipo) + '</span></div>' +
                                '<div class="mu-popup__body"><strong class="mu-popup__title">' +
                                escapeHtml(nombre) + '</strong><div class="mu-popup__meta">' +
                                '<span>' + texto + '</span></div></div>';

                            if (config.tipo === 'Barrio') {
                                html += '<div class="mu-popup__actions">' +
                                    '<button type="button" class="mu-popup__btn-action mu-popup__btn--orig" data-set-origin="' +
                                    escapeHtml(nombre) + '"><i class="fas fa-play" aria-hidden="true"></i> Origen (A)</button>' +
                                    '<button type="button" class="mu-popup__btn-action mu-popup__btn--dest" data-set-dest="' +
                                    escapeHtml(nombre) + '"><i class="fas fa-flag" aria-hidden="true"></i> Destino (B)</button>' +
                                    '</div>';
                            }
                            elemento.bindPopup(html + '</div>');
                        }
                    }).addTo(config.capa);

                    config.cargada = true;
                    if (!atribucionAgregada && mapa.attributionControl) {
                        mapa.attributionControl.addAttribution('Cartografía catastral de Cartagena · barrios y localidades');
                        atribucionAgregada = true;
                    }
                })
                .catch(function (error) {
                    mapa.removeLayer(config.capa);
                    mostrarAviso('No se pudo cargar la capa de ' + config.tipo.toLowerCase() +
                        ': ' + (error && error.message ? error.message : 'error desconocido'), 'warn', 7000);
                })
                .finally(function () {
                    config.cargando = null;
                });
        });
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

    var ControlEstilo = L.Control.extend({
        options: { position: 'topleft', bases: null, activa: null, alElegir: null },
        onAdd: function () {
            var self = this;
            var contenedor = L.DomUtil.create('div', 'leaflet-bar leaflet-control mu-control mu-estilos');
            var boton = L.DomUtil.create('a', 'mu-estilos__boton', contenedor);
            boton.href = '#';
            boton.title = 'Estilo del mapa';
            boton.setAttribute('role', 'button');
            boton.setAttribute('aria-label', 'Cambiar el estilo del mapa');
            boton.setAttribute('aria-haspopup', 'true');
            boton.setAttribute('aria-expanded', 'false');
            boton.innerHTML = '<i class="fas fa-layer-group" aria-hidden="true"></i>';

            var menu = L.DomUtil.create('div', 'mu-estilos__menu', contenedor);
            menu.hidden = true;
            menu.setAttribute('role', 'menu');
            menu.setAttribute('aria-label', 'Estilo del mapa');
            menu.setAttribute('aria-visible', 'false');
            menu.setAttribute('aria-hidden', 'true');
            var titulo = L.DomUtil.create('span', 'mu-estilos__titulo', menu);
            titulo.textContent = 'Estilo del mapa';
            var rejilla = L.DomUtil.create('div', 'mu-estilos__rejilla', menu);
            var temporizadorCierre = null;

            ESTILOS_MAPA.forEach(function (estilo) {
                if (!self.options.bases[estilo.id]) return;
                var normalized = String(estilo.id || '')
                    .normalize ? String(estilo.id).normalize('NFD').replace(/\p{Diacritic}/gu, '') : String(estilo.id);
                normalized = normalized.toLowerCase().replace(/[\u0300-\u036f]/g, '').replace(/[^a-z0-9]+/g, '-').replace(/(^-|-$)/g, '');
                var opcion = L.DomUtil.create('button', 'mu-estilo mu-estilo--' + normalized, rejilla);
                opcion.type = 'button';
                opcion.setAttribute('role', 'menuitemradio');
                opcion.setAttribute('data-estilo', estilo.id);
                // Build a compact inline SVG miniature per style for richer, brandable previews
                var svg = '';
                switch (normalized) {
                    case 'calles':
                        svg = '<svg width="120" height="38" viewBox="0 0 120 38" xmlns="http://www.w3.org/2000/svg" aria-hidden="true">'
                            + '<rect width="120" height="38" rx="6" fill="' + (estilo.muestra || '#e8eef2') + '" />'
                            + '<g fill="#d5e0e0" opacity=".72"><path d="M8 3h17v6H8zM34 3h17v6H34zM69 3h18v5H69zM95 4h17v7H95zM4 30h13v5H4zM36 30h13v5H36zM82 29h13v6H82z" /></g>'
                            + '<path d="M56 3h12v8H56zM56 29h17v6H56z" fill="#b8d8c4" opacity=".9" />'
                            + '<g class="mu-mini-calles__cuadricula" fill="none" stroke="#9eafb1" stroke-width=".8" opacity=".42">'
                            + '<path d="M0 8 L120 31 M5 0 L21 38 M55 0 L67 38 M103 0 L88 38" />'
                            + '</g>'
                            + '<g fill="none" stroke="#fff" stroke-linecap="round" opacity=".8"><path d="M0 20 20 11 42 22 62 12 83 21 120 8" stroke-width="3.6"/><path d="M30 0 36 38M91 0 82 38" stroke-width="2.4"/></g>'
                            + '<g class="mu-mini-calles" fill="none" stroke-width="2.4">'
                            + '<path class="mu-mini-calles__borde" d="M6 28 L28 12 L52 26 L76 10 L112 26" />'
                            + '<path class="mu-mini-calles__resplandor" d="M6 28 L28 12 L52 26 L76 10 L112 26" />'
                            + '<path class="mu-mini-calles__ruta" d="M6 28 L28 12 L52 26 L76 10 L112 26" stroke-linecap="round" stroke-linejoin="round" />'
                            + '<circle class="mu-mini-calles__nodo" cx="52" cy="26" r="2.1" />'
                            + '<circle class="mu-mini-calles__vehiculo" cx="6" cy="28" r="2.7" />'
                            + '</g></svg>';
                        break;
                    case 'oscuro':
                        svg = '<svg width="120" height="38" viewBox="0 0 120 38" xmlns="http://www.w3.org/2000/svg" aria-hidden="true">'
                            + '<rect width="120" height="38" rx="6" fill="' + (estilo.muestra || '#0f1b21') + '" />'
                            + '<path d="M0 29 L25 22 L47 27 L71 18 L94 25 L120 15 V38 H0Z" fill="#20373d" />'
                            + '<path d="M7 31 V26 H13 V31 M18 30 V23 H23 V30 M31 32 V27 H36 V32 M52 29 V23 H58 V29 M82 29 V21 H88 V29 M103 26 V20 H109 V26" fill="#29464b" />'
                            + '<g class="mu-mini-oscuro__calles" fill="none" stroke-linecap="round"><path d="M0 24 28 18 49 23 72 13 95 19 120 10M18 38 30 0M63 38 69 0M101 38 92 0" stroke="#43646a" stroke-width="1.5"/><path d="M0 24 28 18 49 23 72 13 95 19 120 10" stroke="#8ae8d2" stroke-width=".65" opacity=".72"/></g>'
                            + '<path class="mu-mini-oscuro__ruta-halo" d="M4 27 27 19 49 24 72 14 96 20 116 11" fill="none" stroke="#35d6bd" stroke-width="4" opacity=".18" stroke-linecap="round"/>'
                            + '<path class="mu-mini-oscuro__ruta" d="M4 27 27 19 49 24 72 14 96 20 116 11" fill="none" stroke="#74f0d2" stroke-width="1.15" stroke-dasharray="1 4" stroke-linecap="round"/>'
                            + '<circle class="mu-mini-oscuro__baliza" cx="72" cy="14" r="2" fill="#d6fff0"/>'
                            + '<g class="mu-mini-oscuro__estrellas" fill="#d4e6df">'
                            + '<circle class="mu-mini-oscuro__estrella-a" cx="20" cy="10" r="1.1" /><circle class="mu-mini-oscuro__estrella-b" cx="43" cy="16" r=".9" /><circle class="mu-mini-oscuro__estrella-c" cx="78" cy="8" r="1.2" />'
                            + '</g>'
                            + '<g class="mu-mini-oscuro__luna" fill="none" stroke="#b9d7ce" stroke-width=".8" opacity=".42"><circle cx="96" cy="10" r="8" /></g>'
                            + '<g class="mu-mini-oscuro"><circle class="mu-mini-oscuro__disco" cx="96" cy="10" r="5" />'
                            + '<circle class="mu-mini-oscuro__sombra" cx="94" cy="8" r="5" fill="' + (estilo.muestra || '#0f1b21') + '" />'
                            + '</g></svg>';
                        break;
                    case 'claro':
                        svg = '<svg width="120" height="38" viewBox="0 0 120 38" xmlns="http://www.w3.org/2000/svg" aria-hidden="true">'
                            + '<rect width="120" height="38" rx="6" fill="' + (estilo.muestra || '#f4f6f7') + '" />'
                            + '<path d="M0 3h29v8H0zM38 2h20v7H38zM79 3h25v7H79zM6 24h17v7H6zM55 27h22v8H55zM98 25h18v8H98z" fill="#dce7df" />'
                            + '<path d="M28 0c11 8 5 15-2 22S17 32 26 38M81 0c-10 7-5 13 2 18s7 12-1 20" fill="none" stroke="#b8d9d3" stroke-width="2.2" opacity=".9" />'
                            + '<path d="M0 30 L24 23 L46 28 L70 17 L95 24 L120 13" fill="none" stroke="#a8b7bb" stroke-width="2" />'
                            + '<path d="M0 34 L25 29 L50 33 L74 25 L100 30 L120 23" fill="none" stroke="#c2ced0" stroke-width="1.5" />'
                            + '<path class="mu-mini-claro__ruta-halo" d="M3 26 24 20 45 25 68 14 94 21 117 10" fill="none" stroke="#fff" stroke-width="4.4" stroke-linecap="round" opacity=".86" />'
                            + '<path class="mu-mini-claro__ruta" d="M3 26 24 20 45 25 68 14 94 21 117 10" fill="none" stroke="#178f8a" stroke-width="1.4" stroke-linecap="round" stroke-dasharray="1 5" />'
                            + '<circle class="mu-mini-claro__pin" cx="68" cy="14" r="2" fill="#fff" stroke="#168b87" stroke-width="1.2" />'
                            + '<g class="mu-mini-claro__rayos" fill="none" stroke="#e8ad42" stroke-width="1.1" stroke-linecap="round">'
                            + '<path d="M12 1 V3 M12 17 V19 M3 10 H5 M19 10 H21 M5.6 3.6 L7 5 M17 15 L18.4 16.4 M18.4 3.6 L17 5 M7 15 L5.6 16.4" />'
                            + '</g>'
                            + '<circle class="mu-mini-claro__halo" cx="12" cy="10" r="7" fill="#f2b84b" opacity=".16" />'
                            + '<g class="mu-mini-claro">'
                            + '<circle class="mu-mini-claro__sol" cx="12" cy="10" r="4.2" />'
                            + '</g></svg>';
                        break;
                    case 'satelite':
                        svg = '<svg width="120" height="38" viewBox="0 0 120 38" xmlns="http://www.w3.org/2000/svg" aria-hidden="true">'
                            + '<rect width="120" height="38" rx="6" fill="' + (estilo.muestra || '#1d3f52') + '" />'
                            + '<path d="M0 0 H45 L57 14 L38 23 L18 20 L0 29Z" fill="#41694d" /><path d="M72 0 H120 V20 L99 18 L87 29 L65 19Z" fill="#58724b" /><path d="M0 31 23 24 39 30 61 24 78 31 105 20 120 23V38H0Z" fill="#365d49" />'
                            + '<path d="M4 4 L25 8 L39 4 M76 5 L92 10 L112 4 M9 30 L27 25 L41 32 M74 30 L91 24 L111 29 M47 3l5 8-9 5 5 6M103 18l-7 5 7 5" fill="none" stroke="#a2bd83" stroke-width=".7" opacity=".72" />'
                            + '<path d="M6 2 21 14 33 12M85 0 79 12 90 19M30 38 44 28 58 35M108 38 101 29 114 23" fill="none" stroke="#718e68" stroke-width=".6" opacity=".68" />'
                            + '<path d="M0 35 L40 17 L83 25 L120 7" fill="none" stroke="#182c28" stroke-width="4" opacity=".55" />'
                            + '<path d="M0 35 L40 17 L83 25 L120 7" fill="none" stroke="#f2dfaa" stroke-width="2.8" opacity=".85" />'
                            + '<path class="mu-mini-sat__carretera" d="M0 35 L40 17 L83 25 L120 7" fill="none" stroke="#e3d19b" stroke-width="1.5" stroke-dasharray="5 3" />'
                            + '<path class="mu-mini-sat__reticula" d="M0 8H120M0 19H120M0 30H120M30 0V38M60 0V38M90 0V38" fill="none" stroke="#d4e7c5" stroke-width=".35" opacity=".28" />'
                            + '<circle class="mu-mini-sat__anillo" cx="73" cy="21" r="7" fill="none" stroke="#fff0bd" stroke-width=".45" stroke-dasharray="1 2" />'
                            + '<circle class="mu-mini-sat__objetivo" cx="73" cy="21" r="4.5" fill="none" stroke="#f3e6b9" stroke-width=".8" />'
                            + '<circle class="mu-mini-sat__punto" cx="73" cy="21" r="1.5" fill="#fff5cb" />'
                            + '<g class="mu-mini-sat">'
                            + '<path class="mu-mini-sat__barrido" d="M-30 0 H-10 L13 38 H-7Z" fill="url(#mu-mini-sat-luz)" />'
                            + '<defs><linearGradient id="mu-mini-sat-luz" x1="0" x2="1"><stop stop-color="#fff" stop-opacity="0" /><stop offset=".5" stop-color="#fff" stop-opacity=".24" /><stop offset="1" stop-color="#fff" stop-opacity="0" /></linearGradient></defs>'
                            + '</g></svg>';
                        break;
                    case 'openstreetmap':
                        svg = '<svg width="120" height="38" viewBox="0 0 120 38" xmlns="http://www.w3.org/2000/svg" aria-hidden="true">'
                            + '<rect width="120" height="38" rx="6" fill="' + (estilo.muestra || '#d9e8c8') + '" />'
                            + '<path d="M4 5 H37 V17 H4Z M83 4 H116 V15 H83Z M47 24 H74 V35 H47Z" fill="#b8d3ad" /><path d="M5 6h30v9H5zM85 5h29v8H85z" fill="#a7c69b" opacity=".7" />'
                            + '<path d="M1 3c18 8 2 14 20 19s2 10 20 16" fill="none" stroke="#9bd5de" stroke-width="3.2" opacity=".86" />'
                            + '<path d="M44 2 V36 M78 1 V37 M1 20 H119 M15 1v37M105 0v38M1 9h118M1 31h118" stroke="#fffdf4" stroke-width="2" opacity=".84" />'
                            + '<path d="M44 2 V36 M78 1 V37 M1 20 H119" stroke="#bdc9af" stroke-width=".55" opacity=".8" />'
                            + '<g class="mu-mini-osm__poi"><circle cx="24" cy="11" r="2.2" fill="#e67e61"/><circle cx="94" cy="24" r="2.1" fill="#edb84e"/><circle cx="54" cy="8" r="1.8" fill="#7796bf"/></g>'
                            + '<g class="mu-mini-osm" fill="none" stroke-width="2">'
                            + '<path class="mu-mini-osm__borde" d="M10 26 L30 12 L50 26 L70 14 L110 26" />'
                            + '<path class="mu-mini-osm__halo" d="M10 26 L30 12 L50 26 L70 14 L110 26" stroke="#fff" stroke-width="3.2" opacity=".85" />'
                            + '<path class="mu-mini-osm__ruta" d="M10 26 L30 12 L50 26 L70 14 L110 26" stroke-linecap="round" stroke-width="1.2" />'
                            + '<circle class="mu-mini-osm__marcador" cx="10" cy="26" r="2.5" />'
                            + '<circle class="mu-mini-osm__parada" cx="70" cy="14" r="2" />'
                            + '</g></svg>';
                        break;
                    default:
                        svg = '<i class="fas ' + estilo.icono + '" aria-hidden="true"></i>';
                }
                opcion.innerHTML = '<span class="mu-estilo__muestra" role="img" aria-label="' + escapeHtml(estilo.etiqueta) + '">' + svg + '</span>' +
                    '<span class="mu-estilo__texto">' + escapeHtml(estilo.etiqueta) + '</span>';
                L.DomEvent.on(opcion, 'click', function () {
                    self.options.alElegir(estilo.id);
                    cerrar();
                    boton.focus();
                });
            });

            function abrir() {
                if (temporizadorCierre) {
                    clearTimeout(temporizadorCierre);
                    temporizadorCierre = null;
                }
                menu.hidden = false;
                menu.setAttribute('aria-hidden', 'false');
                boton.setAttribute('aria-expanded', 'true');
                var mostrar = function () {
                    if (boton.getAttribute('aria-expanded') !== 'true') return;
                    menu.setAttribute('aria-visible', 'true');
                    var activo = menu.querySelector('.mu-estilo[aria-checked="true"]') || menu.querySelector('.mu-estilo');
                    if (activo) activo.focus();
                };
                if (global.requestAnimationFrame) global.requestAnimationFrame(mostrar);
                else setTimeout(mostrar, 0);
            }
            function cerrar() {
                if (boton.getAttribute('aria-expanded') !== 'true') return;
                menu.setAttribute('aria-visible', 'false');
                menu.setAttribute('aria-hidden', 'true');
                boton.setAttribute('aria-expanded', 'false');
                if (temporizadorCierre) clearTimeout(temporizadorCierre);
                temporizadorCierre = setTimeout(function () {
                    menu.hidden = true;
                    temporizadorCierre = null;
                }, 200);
            }

            L.DomEvent.disableClickPropagation(contenedor);
            L.DomEvent.disableScrollPropagation(contenedor);
            L.DomEvent.on(boton, 'click', function (e) {
                L.DomEvent.preventDefault(e);
                if (boton.getAttribute('aria-expanded') !== 'true') abrir(); else cerrar();
            });
            L.DomEvent.on(document, 'click', function (e) {
                if (!contenedor.contains(e.target)) cerrar();
            });
            L.DomEvent.on(document, 'keydown', function (e) {
                if (boton.getAttribute('aria-expanded') !== 'true') return;
                if (e.key === 'Escape') {
                    cerrar();
                    boton.focus();
                    return;
                }
                if (e.key === 'ArrowDown' || e.key === 'ArrowUp' || e.key === 'Home' || e.key === 'End') {
                    var opciones = Array.prototype.slice.call(menu.querySelectorAll('.mu-estilo'));
                    if (!opciones.length) return;
                    var indice = opciones.indexOf(document.activeElement);
                    if (e.key === 'Home') indice = 0;
                    else if (e.key === 'End') indice = opciones.length - 1;
                    else indice = (indice + (e.key === 'ArrowDown' ? 1 : -1) + opciones.length) % opciones.length;
                    e.preventDefault();
                    opciones[indice].focus();
                }
            });
            this._menu = menu;
            this.marcar(this.options.activa);
            return contenedor;
        },
        marcar: function (id) {
            if (!this._menu) return;
            Array.prototype.forEach.call(this._menu.querySelectorAll('.mu-estilo'), function (boton) {
                var activo = boton.getAttribute('data-estilo') === id;
                boton.setAttribute('aria-checked', String(activo));
                boton.classList.toggle('mu-estilo--activo', activo);
            });
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
            zoomDelta: 1,
            maxBounds: LIMITES_MAPA,
            maxBoundsViscosity: 0.7,
            wheelPxPerZoomLevel: 90
        });
        mapa.attributionControl.setPrefix('<a href="https://leafletjs.com" target="_blank" rel="noopener">Leaflet</a>');

        L.control.zoom({ position: 'topleft', zoomInTitle: 'Acercar', zoomOutTitle: 'Alejar' }).addTo(mapa);
        new ControlVista({ obtenerLimites: opciones.obtenerLimites }).addTo(mapa);
        crearCapasCartografia(mapa);

        var bases = crearMapasBase();
        var nombre = leerPreferencia();
        if (!bases[nombre]) nombre = MAPA_BASE_PREDETERMINADO;
        var activa = bases[nombre];
        activa.addTo(mapa);

        var selector = new ControlEstilo({
            bases: bases,
            activa: nombre,
            alElegir: function (id) {
                if (!bases[id] || bases[id] === activa) return;
                mapa.removeLayer(activa);
                bases[id].addTo(mapa);
            }
        }).addTo(mapa);
        L.control.scale({ imperial: false, position: 'bottomleft', maxWidth: 110 }).addTo(mapa);

        mapa.on('layeradd', function (e) {
            Object.keys(bases).forEach(function (clave) {
                if (bases[clave] === e.layer) {
                    activa = e.layer;
                    guardarPreferencia(clave);
                    selector.marcar(clave);
                }
            });
        });

        mapa.on('popupopen', function (e) {
            var popEl = e.popup.getElement();
            if (!popEl) return;
            var btnSpeak = popEl.querySelector('.mu-popup__btn-speak');
            if (btnSpeak) {
                btnSpeak.addEventListener('click', function () {
                    var texto = btnSpeak.getAttribute('data-speak');
                    if (global.BustrakerNotificaciones && global.BustrakerNotificaciones.hablar) {
                        global.BustrakerNotificaciones.chime();
                        global.BustrakerNotificaciones.hablar(texto, true);
                    }
                });
            }
            var btnOrig = popEl.querySelector('[data-set-origin]');
            if (btnOrig) {
                btnOrig.addEventListener('click', function () {
                    var barrio = btnOrig.getAttribute('data-set-origin');
                    global.dispatchEvent(new CustomEvent('bustraker:fijar-origen', { detail: { barrio: barrio } }));
                    mapa.closePopup();
                });
            }
            var btnDest = popEl.querySelector('[data-set-dest]');
            if (btnDest) {
                btnDest.addEventListener('click', function () {
                    var barrio = btnDest.getAttribute('data-set-dest');
                    global.dispatchEvent(new CustomEvent('bustraker:fijar-destino', { detail: { barrio: barrio } }));
                    mapa.closePopup();
                });
            }
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
                selector.marcar('OpenStreetMap');
                mostrarAviso('El mapa «' + clave + '» no respondió. Se cambió a Calles ESRI.', 'warn', 7000);
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
        if (panel && !panel.classList.contains('options-panel--min')) {
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
    function contenidoPopup(titulo, nombre, nota) {
        return '<div class="mu-popup mu-popup--pro">' +
            '<div class="mu-popup__banner"><i class="fas fa-location-dot"></i><span>' + escapeHtml(titulo) + '</span></div>' +
            '<div class="mu-popup__body"><strong>' + escapeHtml(nombre) + '</strong>' +
            (nota ? '<p class="mu-popup__nota">' + escapeHtml(nota) + '</p>' : '') +
            '</div>' +
            (global.BustrakerNotificaciones ? '<div class="mu-popup__actions">' +
            '  <button type="button" class="mu-popup__btn-speak" data-speak="' + escapeHtml(titulo + ': ' + nombre) + '"><i class="fas fa-volume-high"></i> Escuchar por voz</button>' +
            '</div>' : '') + '</div>';
    }

    /** Pin 3D profesional con radar de pulso y diseño de navegación. */
    function crearPin(lat, lng, tipo, nombre, nota) {
        var esOrigen = tipo === 'origen';
        var titulo = esOrigen ? 'Origen (Punto de Salida)' : 'Destino (Llegada)';
        var etiqueta = escapeHtml(nombre) + (nota ? ' · <small>Aproximada</small>' : '');
        var iconoFa = esOrigen ? 'fa-location-arrow' : 'fa-flag-checkered';
        var textoHablar = (esOrigen ? 'Origen del viaje: ' : 'Destino del viaje: ') + nombre + (nota ? '. ' + nota : '');

        var htmlPin =
            '<div class="bp-pin-wrapper bp-pin-wrapper--' + tipo + '">' +
            '  <div class="bp-pin-pulse"></div>' +
            '  <div class="bp-pin">' +
            '    <span class="bp-pin-icon"><i class="fas ' + iconoFa + '"></i></span>' +
            '    <span class="bp-pin-badge">' + (esOrigen ? 'A' : 'B') + '</span>' +
            '  </div>' +
            '  <div class="bp-pin-tip"></div>' +
            '  <div class="bp-pin-shadow"></div>' +
            '</div>';

        var icono = L.divIcon({
            className: 'bp-pin-contenedor',
            html: htmlPin,
            iconSize: [42, 54],
            iconAnchor: [21, 52],
            tooltipAnchor: [0, -56],
            popupAnchor: [0, -52]
        });

        var popupHtml =
            '<div class="mu-popup mu-popup--pro">' +
            '  <div class="mu-popup__banner mu-popup__banner--' + tipo + '">' +
            '    <i class="fas ' + iconoFa + '"></i>' +
            '    <span>' + escapeHtml(titulo) + '</span>' +
            '  </div>' +
            '  <div class="mu-popup__body">' +
            '    <strong class="mu-popup__title">' + escapeHtml(nombre) + '</strong>' +
            '    ' + (nota ? '<p class="mu-popup__nota"><i class="fas fa-circle-info"></i> ' + escapeHtml(nota) + '</p>' : '') +
            '  </div>' +
            (global.BustrakerNotificaciones ? '  <div class="mu-popup__actions">' +
            '    <button type="button" class="mu-popup__btn-speak" data-speak="' + escapeHtml(textoHablar) + '"><i class="fas fa-volume-high"></i> Escuchar por voz</button>' +
            '  </div>' : '') +
            '</div>';

        return L.marker([lat, lng], { icon: icono, riseOnHover: true, keyboard: true, title: titulo + ': ' + nombre })
            .bindTooltip('<b>' + (esOrigen ? 'A' : 'B') + '</b> · ' + etiqueta, {
                permanent: true, direction: 'top', className: 'mu-tooltip mu-tooltip--' + tipo
            })
            .bindPopup(popupHtml);
    }

    /** Punto pequeño para paradas / barrios intermedios. */
    function crearPuntoIntermedio(lat, lng, titulo, nombre) {
        var punto = L.circleMarker([lat, lng], {
            radius: 7, weight: 2.5, color: '#ffffff', fillColor: '#38bdf8', fillOpacity: 0.95
        })
            .bindTooltip(escapeHtml(nombre), { direction: 'top', offset: [0, -7], className: 'mu-tooltip' })
            .bindPopup(contenidoPopup(titulo, nombre));
        punto.on('mouseover', function () { punto.setRadius(9); });
        punto.on('mouseout', function () { punto.setRadius(7); });
        return punto;
    }

    /** Marcador moderno de nodo de transporte para paradas, transbordos y cabeceras. */
    function crearMarcadorParada(lat, lng, numero, datos) {
        datos = datos || {};
        var esParada = datos.tipo !== 'barrio';
        var esTransbordo = datos.transbordo === true;
        var iconoClase = esTransbordo ? 'fa-right-left' : (esParada ? 'fa-bus-simple' : 'fa-location-crosshairs');
        var claseNodo = esTransbordo ? 'bp-stop--transbordo' : (esParada ? 'bp-stop--parada' : 'bp-stop--barrio');

        var htmlMarcador =
            '<div class="bp-stop ' + claseNodo + '">' +
            '  <div class="bp-stop__icon"><i class="fas ' + iconoClase + '"></i></div>' +
            '  <div class="bp-stop__badge">' + escapeHtml(numero) + '</div>' +
            '  <div class="bp-stop__ring"></div>' +
            '</div>';

        var icono = L.divIcon({
            className: 'bp-stop-contenedor',
            html: htmlMarcador,
            iconSize: [32, 32],
            iconAnchor: [16, 16],
            popupAnchor: [0, -20],
            tooltipAnchor: [0, -18]
        });

        var detalle = [];
        if (datos.barrio) detalle.push('<div class="mu-popup__meta-item"><i class="fas fa-map-pin"></i> <span>Barrio: <strong>' + escapeHtml(datos.barrio) + '</strong></span></div>');
        if (datos.ubicacion) detalle.push('<div class="mu-popup__meta-item"><i class="fas fa-road"></i> <span>' + escapeHtml(datos.ubicacion) + '</span></div>');
        if (datos.referencia) detalle.push('<div class="mu-popup__meta-item"><i class="fas fa-landmark"></i> <span>Ref.: ' + escapeHtml(datos.referencia) + '</span></div>');
        if (!esParada) detalle.push('<p class="mu-popup__nota"><i class="fas fa-info-circle"></i> Centro del barrio (sin parada física registrada).</p>');
        if (esTransbordo && datos.rutaSiguiente) {
            detalle.push('<div class="mu-popup__transfer-alert"><i class="fas fa-arrow-right-arrow-left"></i> <strong>Transbordo necesario</strong>: Cambiar a la ruta <em>' + escapeHtml(datos.rutaSiguiente) + '</em> aquí.</div>');
        }

        var titulo = esTransbordo ? 'Transbordo #' + numero : (esParada ? 'Parada #' + numero : 'Punto de Barrio #' + numero);
        var nombreLimpio = datos.nombre || titulo;
        var textoHablar = titulo + ': ' + nombreLimpio + (datos.barrio ? ', en barrio ' + datos.barrio : '') + (esTransbordo && datos.rutaSiguiente ? '. Cambiar a la ruta ' + datos.rutaSiguiente + ' aquí' : '');

        var bannerClase = esTransbordo ? 'mu-popup__banner--transbordo' : (esParada ? 'mu-popup__banner--parada' : 'mu-popup__banner--barrio');

        var popupHtml =
            '<div class="mu-popup mu-popup--pro">' +
            '  <div class="mu-popup__banner ' + bannerClase + '">' +
            '    <i class="fas ' + iconoClase + '"></i>' +
            '    <span>' + escapeHtml(titulo) + '</span>' +
            '  </div>' +
            '  <div class="mu-popup__body">' +
            '    <strong class="mu-popup__title">' + escapeHtml(nombreLimpio) + '</strong>' +
            '    <div class="mu-popup__meta">' + detalle.join('') + '</div>' +
            '  </div>' +
            '  <div class="mu-popup__actions">' +
            '    <button type="button" class="mu-popup__btn-action mu-popup__btn--orig" data-set-origin="' + escapeHtml(datos.barrio || nombreLimpio) + '"><i class="fas fa-play"></i> Origen (A)</button>' +
            '    <button type="button" class="mu-popup__btn-action mu-popup__btn--dest" data-set-dest="' + escapeHtml(datos.barrio || nombreLimpio) + '"><i class="fas fa-flag"></i> Destino (B)</button>' +
            (global.BustrakerNotificaciones ? '    <button type="button" class="mu-popup__btn-speak" data-speak="' + escapeHtml(textoHablar) + '"><i class="fas fa-volume-high"></i> Escuchar</button>' : '') +
            '  </div>' +
            '</div>';

        return L.marker([lat, lng], { icon: icono, keyboard: true, title: titulo + ': ' + nombreLimpio })
            .bindTooltip(escapeHtml(nombreLimpio) + (datos.coordenadaEstimada ? ' · <small>Aproximada</small>' : ''), { direction: 'top', className: 'mu-tooltip' })
            .bindPopup(popupHtml);
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
    function claveRuta(waypoints) {
        return waypoints.map(function (punto) { return punto.lat + ',' + punto.lng; }).join('|');
    }

    async function consultarServidorRuta(base, coords, senal) {
        var controlador = new AbortController();
        var temporizador = setTimeout(function () { controlador.abort(); }, 12000);
        var abortar = function () { controlador.abort(); };
        if (senal) {
            if (senal.aborted) controlador.abort();
            else senal.addEventListener('abort', abortar, { once: true });
        }
        try {
            var respuesta = await fetch(base + coords + '?overview=full&geometries=geojson&steps=false',
                { signal: controlador.signal });
            if (!respuesta.ok) throw new Error('El servidor de rutas respondió ' + respuesta.status);
            var datos = await respuesta.json();
            if (!datos.routes || datos.routes.length === 0) throw new Error('Sin ruta disponible');
            return datos.routes[0];
        } finally {
            clearTimeout(temporizador);
            if (senal) senal.removeEventListener('abort', abortar);
        }
    }

    async function obtenerRutaOSRM(waypoints, senal) {
        if (senal && senal.aborted) {
            var cancelado = new Error('Consulta de ruta cancelada');
            cancelado.name = 'AbortError';
            throw cancelado;
        }
        var clave = claveRuta(waypoints);
        if (cacheRutas.has(clave)) {
            var guardada = cacheRutas.get(clave);
            cacheRutas.delete(clave);
            cacheRutas.set(clave, guardada);
            return guardada;
        }
        var coords = waypoints.map(function (p) { return p.lng + ',' + p.lat; }).join(';');
        var ultimoError = null;
        for (var i = 0; i < SERVIDORES_RUTA.length; i++) {
            if (senal && senal.aborted) {
                var abortError = new Error('Consulta de ruta cancelada');
                abortError.name = 'AbortError';
                throw abortError;
            }
            try {
                var ruta = await consultarServidorRuta(SERVIDORES_RUTA[i], coords, senal);
                if (!ruta.geometry || !Number.isFinite(ruta.distance) || !Number.isFinite(ruta.duration)) {
                    throw new Error('El servidor de rutas devolvió información incompleta');
                }
                var resultado = {
                    geometry: ruta.geometry,
                    distanciaKm: ruta.distance / 1000,
                    tiempoMin: Math.max(1, Math.round(ruta.duration / 60))
                };
                cacheRutas.set(clave, resultado);
                if (cacheRutas.size > CACHE_RUTAS_MAX) cacheRutas.delete(cacheRutas.keys().next().value);
                return resultado;
            } catch (error) {
                if (senal && senal.aborted) throw error;
                ultimoError = error;
            }
        }
        throw ultimoError || new Error('Sin ruta disponible');
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
        url: urlAplicacion,
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
