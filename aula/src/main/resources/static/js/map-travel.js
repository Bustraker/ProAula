/*
 * Bustraker · Lógica compartida de "Viajar" (Leaflet)
 *
 * Lo usan viajar.js (usuario con sesión) y viajar_public.js (público). Las dos páginas
 * tienen exactamente la misma estructura; solo cambia la configuración:
 *     BustrakerTravel.iniciar({ privado: true | false });
 *
 * Qué resuelve:
 *   - Origen / destino por barrio y búsqueda explícita con validación.
 *   - Itinerarios con transbordos, ordenados primero por cantidad de transbordos.
 *   - Detalle del tramo: paradas en orden (línea de tiempo), buses asignados e información.
 *   - Mapa: pines A/B, paradas numeradas, trazado por calles (OSRM) y recorridos TransCaribe.
 *   - Ubicación en vivo: barrio, parada más cercana a pie y siguiente barrio del trazado.
 *   - Estado en la URL (?o=…&d=…&r=…) para compartir una consulta.
 *
 * Honestidad de datos: no se inventan horarios ni posiciones de buses. El trazado y el tiempo son
 * estimaciones en auto por calles, y así se rotula en la interfaz.
 */
(function (global) {
    'use strict';

    var M = global.BustrakerMap;
    var URL_TRANSCARIBE = 'https://services7.arcgis.com/t784NacZjQPpWVsA/arcgis/rest/services/Cobertura_Transcaribe_2024_WFL1/FeatureServer/1/query';
    var METROS_POR_MINUTO_A_PIE = 80;
    var MAX_PUNTOS_OSRM = 40;
    var COLORES_BUS = {
        azul: '#2563eb', rojo: '#dc2626', verde: '#16a34a', amarillo: '#eab308', blanco: '#f1f5f9',
        negro: '#111827', gris: '#6b7280', naranja: '#f97316', morado: '#7c3aed', rosado: '#ec4899',
        naranjado: '#f97316', beige: '#d6c7a1', plateado: '#cbd5e1'
    };

    /* ===================== UTILIDADES ===================== */
    function $(id) { return document.getElementById(id); }

    function normalizar(texto) {
        return String(texto == null ? '' : texto).trim().toLowerCase()
            .normalize('NFD').replace(/\p{Diacritic}/gu, '').replace(/\s+/g, ' ');
    }

    function mayuscula(texto) {
        texto = String(texto == null ? '' : texto);
        return texto.charAt(0).toUpperCase() + texto.slice(1);
    }

    function h(etiqueta, clase, texto) {
        var e = document.createElement(etiqueta);
        if (clase) e.className = clase;
        if (texto != null) e.textContent = texto;
        return e;
    }

    function icono(clases) {
        var i = document.createElement('i');
        i.className = clases;
        i.setAttribute('aria-hidden', 'true');
        return i;
    }

    function vaciar(nodo) { while (nodo && nodo.firstChild) nodo.removeChild(nodo.firstChild); }

    function haversineKm(lat1, lng1, lat2, lng2) {
        var R = 6371, rad = Math.PI / 180;
        var dLat = (lat2 - lat1) * rad, dLng = (lng2 - lng1) * rad;
        var a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
            Math.cos(lat1 * rad) * Math.cos(lat2 * rad) * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    /** "06:30[:00]" → "6:30 a. m." */
    function formatearHora(valor) {
        if (!valor) return null;
        var m = /^(\d{1,2}):(\d{2})/.exec(String(valor));
        if (!m) return String(valor);
        var hh = Number(m[1]), sufijo = hh >= 12 ? 'p. m.' : 'a. m.';
        hh = hh % 12 === 0 ? 12 : hh % 12;
        return hh + ':' + m[2] + ' ' + sufijo;
    }

    function formatearDistancia(metros) {
        return metros < 1000 ? Math.round(metros / 10) * 10 + ' m' : (metros / 1000).toFixed(1) + ' km';
    }

    /** Proyecta un punto sobre una línea GeoJSON (LineString / MultiLineString). */
    function proyectarEnTrayecto(lat, lng, geometria) {
        if (!geometria || !geometria.coordinates) return null;
        var lineas = geometria.type === 'MultiLineString' ? geometria.coordinates : [geometria.coordinates];
        var acumulado = 0, mejor = null;
        lineas.forEach(function (linea) {
            for (var i = 1; i < linea.length; i++) {
                var a = linea[i - 1], b = linea[i];
                var escalaX = 111.32 * Math.cos(((lat + a[1] + b[1]) / 3) * Math.PI / 180);
                var dx = (b[0] - a[0]) * escalaX, dy = (b[1] - a[1]) * 110.574;
                var px = (lng - a[0]) * escalaX, py = (lat - a[1]) * 110.574;
                var largo2 = dx * dx + dy * dy;
                var f = largo2 === 0 ? 0 : Math.max(0, Math.min(1, (px * dx + py * dy) / largo2));
                var distSegmento = Math.hypot(px - f * dx, py - f * dy);
                var largoKm = haversineKm(a[1], a[0], b[1], b[0]);
                if (!mejor || distSegmento < mejor.distanciaAlTrayectoKm) {
                    mejor = { distanciaAlTrayectoKm: distSegmento, distanciaDesdeInicioKm: acumulado + f * largoKm };
                }
                acumulado += largoKm;
            }
        });
        return mejor;
    }

    function muestrear(puntos, maximo) {
        if (puntos.length <= maximo) return puntos;
        var resultado = [], paso = (puntos.length - 1) / (maximo - 1);
        for (var i = 0; i < maximo; i++) resultado.push(puntos[Math.round(i * paso)]);
        return resultado;
    }

    /* ===================== INICIO ===================== */
    function iniciar(config) {
        config = config || {};
        var privado = !!config.privado;

        var estado = {
            barrios: [],            // [{nombre, nombreN, lat, lng}]
            coordenadas: {},        // nombreN → {lat, lng}
            rutas: [],              // rutas normalizadas
            servidos: new Map(),    // nombreN → nombre visible (barrios con ruta verificada)
            origen: '', destino: '',
            tramos: [], activo: null, pestana: 'paradas',
            items: [],              // paradas/puntos del tramo activo
            solicitud: 0,
            controlador: null,
            trayecto: null, barriosTrayecto: [],
            ubicacion: null, seguimiento: null,
            gpsError: '',
            intentadoSeguimientoAutomatico: false,
            reportandoLlegadas: null,
            tarjetasLlegadas: [],
            temporizadorLlegadas: null,
            movimientoRuta: { ultimaMuestra: null, avances: 0, detectado: false },
            llegadaCandidata: 0,
            llegadaInicioTimestamp: null,
            llegadaAvisada: false,
            resumenEstimado: null,
            resumenConfirmado: false,
            dialogoTrayecto: null,
            transcaribe: [],
            favoritosHabilitados: privado,
            favoritos: [],
            trayectosFavoritos: [],
            barriosFavoritos: new Set()
        };
        var capas = {
            paradas: L.layerGroup(), pinOrigen: null, pinDestino: null,
            base: null, linea: null, flujo: null, caminatas: null,
            transcaribe: null, usuario: null, precision: null
        };
        var el = {};
        var panelEstado = {};

        var mapa = M.crearMapa('map', {
            obtenerLimites: function () {
                if (capas.linea) return capas.linea.getBounds();
                if (estado.items.length) return L.latLngBounds(estado.items.map(function (p) { return [p.lat, p.lng]; }));
                if (capas.transcaribe) return capas.transcaribe.getBounds();
                return null;
            }
        });
        capas.paradas.addTo(mapa);
        agregarLeyenda();
        agregarPanelSeguimiento();

        var arrancado = false;
        document.addEventListener('DOMContentLoaded', arrancar);
        if (document.readyState !== 'loading') arrancar();

        function arrancar() {
            if (arrancado) return;
            arrancado = true;
            if (privado) crearDialogoTrayecto();
            ['origen', 'destino', 'listaOrigen', 'listaDestino', 'btnInvertir', 'btnPlanificar', 'plannerStatus',
                'btnSeguirUbicacion', 'btnFinalizarTrayecto', 'locationStatus',
                'resultados', 'resumenBusqueda', 'rutaDetalle', 'detalleTitulo', 'detalleTramo', 'rutaStats', 'detalleHora',
                'panelParadas', 'panelBuses', 'panelInfo', 'favoriteActions', 'llegadasRutaPanel',
                'mostrarParadas', 'trazadoTranscaribe', 'barriosTrayecto',
                'btnPanel', 'btnCompartir', 'btnLimpiar', 'contadorParadas', 'contadorBuses'
            ].forEach(function (id) { el[id] = $(id); });

            el.origen.addEventListener('input', alCambiarOrigen);
            el.origen.addEventListener('change', alCambiarOrigen);
            el.destino.addEventListener('input', alCambiarDestino);
            el.destino.addEventListener('change', alCambiarDestino);
            el.btnInvertir.addEventListener('click', invertir);
            el.btnPlanificar.addEventListener('click', planificar);
            el.btnSeguirUbicacion.addEventListener('click', alternarSeguimiento);
            if (privado) {
                el.btnFinalizarTrayecto.addEventListener('click', function () { mostrarDialogoLlegada(false); });
            }
            el.btnLimpiar.addEventListener('click', reiniciarConsulta);
            el.btnCompartir.addEventListener('click', compartirEnlace);
            el.btnPanel.addEventListener('click', function () { minimizarPanel(); });
            document.addEventListener('visibilitychange', function () {
                if (!document.hidden) actualizarLlegadasSeleccionadas();
            });
            window.addEventListener('pagehide', detenerCompartirLlegadas);
            el.mostrarParadas.addEventListener('change', function () {
                if (el.mostrarParadas.checked) capas.paradas.addTo(mapa); else mapa.removeLayer(capas.paradas);
            });
            el.trazadoTranscaribe.addEventListener('change', mostrarTranscaribe);
            document.querySelectorAll('[role="tab"][data-pestana]').forEach(function (boton) {
                boton.addEventListener('click', function () { cambiarPestana(boton.getAttribute('data-pestana')); });
                boton.addEventListener('keydown', navegarPestanas);
            });

            pintarResultados();
            cargarDatos();
            cargarTranscaribe();
        }

        /* ===================== DATOS ===================== */
        async function cargarDatos() {
            mostrarEsqueleto();
            try {
                var resultados = await Promise.all([
                    M.fetchJSON(M.url('/api/barrios/todos'), 20000),
                    M.fetchJSON(M.url('/api/rutas-con-coordenadas'), 20000)
                ]);
                procesarBarrios(resultados[0] || []);
                procesarRutas(resultados[1] || []);
                if (estado.favoritosHabilitados) await cargarFavoritos();
                poblarOrigen();
                restaurarDesdeURL();
                pintarResultados();
                el.btnSeguirUbicacion.disabled = false;
                if (!estado.intentadoSeguimientoAutomatico) {
                    estado.intentadoSeguimientoAutomatico = true;
                    iniciarSeguimiento();
                }
            } catch (error) {
                console.error('Error cargando datos de viaje:', error);
                M.mostrarAviso('No se pudieron cargar las rutas. Revisa tu conexión e intenta de nuevo.', 'error', 8000);
                pintarErrorCarga();
            }
        }

        function evaluarLlegada() {
            if (!privado || !estado.activo || !estado.ubicacion || estado.resumenConfirmado
                || !Number.isFinite(estado.ubicacion.precision) || estado.ubicacion.precision > 65) {
                estado.llegadaCandidata = 0;
                estado.llegadaInicioTimestamp = null;
                return;
            }
            var destino = estado.barrios.find(function (barrio) {
                return barrio.nombreN === estado.destino
                    && Number.isFinite(barrio.lat) && Number.isFinite(barrio.lng);
            });
            if (!destino) return;

            var radioMetros = destino.coordenadaEstimada ? 650 : 300;
            var distanciaMetros = haversineKm(
                estado.ubicacion.lat, estado.ubicacion.lng, destino.lat, destino.lng) * 1000;
            if (distanciaMetros > radioMetros + estado.ubicacion.precision) {
                estado.llegadaCandidata = 0;
                estado.llegadaInicioTimestamp = null;
                estado.llegadaAvisada = false;
                return;
            }
            if (!estado.movimientoRuta.detectado || estado.llegadaAvisada) {
                estado.llegadaCandidata = 0;
                estado.llegadaInicioTimestamp = null;
                return;
            }

            var timestamp = estado.ubicacion.timestamp;
            if (estado.llegadaInicioTimestamp === null) {
                estado.llegadaInicioTimestamp = timestamp;
                estado.llegadaCandidata = 1;
            } else if (timestamp - estado.llegadaInicioTimestamp >= 3000) {
                estado.llegadaCandidata = 2;
                if (estado.llegadaCandidata >= 2) {
                    estado.llegadaAvisada = true;
                    expandirPanelSeguimiento(true);
                    mostrarDialogoLlegada(true);
                }
            }
        }

        function procesarBarrios(lista) {
            estado.barrios = [];
            estado.coordenadas = {};
            lista.forEach(function (b) {
                var lat = Number.parseFloat(b.latitud), lng = Number.parseFloat(b.longitud);
                var n = normalizar(b.nombre);
                if (!n) return;
                estado.barrios.push({
                    nombre: b.nombre, nombreN: n, lat: lat, lng: lng,
                    coordenadaEstimada: b.coordenadaEstimada === true
                });
                if (Number.isFinite(lat) && Number.isFinite(lng)) estado.coordenadas[n] = { lat: lat, lng: lng };
            });
        }

        function procesarRutas(lista) {
            estado.servidos = new Map();
            estado.rutas = lista.filter(function (r) {
                return (r.estado == null || r.estado === 'ACTIVA')
                    && r.ordenBarriosConfirmado === true
                    && Array.isArray(r.barriosOrdenados)
                    && r.barriosOrdenados.length >= 2
                    && r.barriosOrdenados.every(function (barrio) {
                        return typeof barrio === 'string' && normalizar(barrio);
                    });
            }).map(function (r) {
                var barrios = r.barriosOrdenados.slice();
                var barriosN = barrios.map(normalizar);
                barrios.forEach(function (nombre, i) { if (!estado.servidos.has(barriosN[i])) estado.servidos.set(barriosN[i], nombre); });
                var legacyStops = normalizarParadas(r.paradas || []);
                var stopsByDirection = r.paradasPorSentido || {};
                var idaStops = normalizarParadas(stopsByDirection.IDA || []);
                var vueltaStops = normalizarParadas(stopsByDirection.VUELTA || []);
                if (!idaStops.length && !Object.keys(stopsByDirection).length) idaStops = legacyStops;
                var ruta = {
                    id: r.id, nombre: r.nombre, hora: r.horaAproximada,
                    barrios: barrios, barriosN: barriosN, paradas: legacyStops,
                    buses: r.buses || [], coordenadas: r.coordenadasBarrios || {},
                    coordenadasEstimadas: new Set((r.coordenadasEstimadas || []).map(normalizar))
                };
                var sentidos = [Object.assign({}, ruta, {
                    sentido: 'IDA',
                    barrios: barrios,
                    barriosN: barriosN,
                    paradas: indexarParadas(idaStops, barriosN)
                })];
                if (vueltaStops.length) {
                    var barriosVuelta = barrios.slice().reverse();
                    var barriosN_vuelta = barriosN.slice().reverse();
                    sentidos.push(Object.assign({}, ruta, {
                        sentido: 'VUELTA',
                        barrios: barriosVuelta,
                        barriosN: barriosN_vuelta,
                        paradas: indexarParadas(vueltaStops, barriosN_vuelta)
                    }));
                }
                ruta.sentidosDisponibles = sentidos.map(function (sentido) { return sentido.sentido; });
                sentidos.forEach(function (sentido) {
                    sentido.sentidosDisponibles = ruta.sentidosDisponibles;
                });
                ruta.sentidos = sentidos;
                return ruta;
            });
        }

        function normalizarParadas(paradas) {
            return (paradas || [])
                .filter(function (parada) {
                    return parada && parada.nombre && parada.estado !== 'INACTIVA';
                })
                .map(function (parada, indice) {
                    var tieneCoordenadas = parada.latitud != null && parada.longitud != null
                        && Number.isFinite(Number(parada.latitud)) && Number.isFinite(Number(parada.longitud));
                    var barrioN = normalizar(parada.barrio || '');
                    return {
                        id: parada.id, nombre: parada.nombre, barrio: parada.barrio, barrioN: barrioN,
                        referencia: parada.referencia, ubicacion: parada.ubicacion,
                        orden: parada.orden == null ? indice + 1 : Number(parada.orden),
                        lat: tieneCoordenadas ? Number(parada.latitud) : null,
                        lng: tieneCoordenadas ? Number(parada.longitud) : null,
                        indiceBarrio: null
                    };
                })
                .sort(function (a, b) { return a.orden - b.orden; });
        }

        function indexarParadas(paradas, barriosN) {
            var cursor = 0;
            return paradas.map(function (parada) {
                if (!parada.barrioN) return parada;
                for (var indice = cursor; indice < barriosN.length; indice++) {
                    if (barriosN[indice] === parada.barrioN) {
                        parada.indiceBarrio = indice;
                        cursor = indice;
                        break;
                    }
                }
                return parada;
            });
        }

        function nombreRutaEnSentido(ruta) {
            return ruta.nombre + ' · ' + (ruta.sentido === 'VUELTA' ? 'Vuelta' : 'Ida');
        }

        async function cargarFavoritos() {
            try {
                var favoritos = await M.fetchJSON(M.url('/api/favoritos'), 15000);
                if (!Array.isArray(favoritos)) {
                    throw new Error('La respuesta de favoritos no tiene el formato esperado.');
                }
                estado.favoritos = favoritos;
                actualizarBarriosFavoritos();
            } catch (error) {
                console.error('Error cargando favoritos:', error);
                estado.favoritos = [];
                actualizarBarriosFavoritos();
                M.mostrarAviso('No se pudieron cargar tus favoritos. El planificador seguirá funcionando.', 'warn', 7000);
            }
            try {
                var trayectos = await M.fetchJSON(M.url('/api/favoritos/trayectos'), 15000);
                if (!Array.isArray(trayectos)) {
                    throw new Error('La respuesta de trayectos favoritos no tiene el formato esperado.');
                }
                estado.trayectosFavoritos = trayectos;
                actualizarBarriosFavoritos();
            } catch (error) {
                console.error('Error cargando trayectos favoritos:', error);
                estado.trayectosFavoritos = [];
                actualizarBarriosFavoritos();
                M.mostrarAviso('No se pudieron cargar los trayectos guardados. Las demás funciones siguen disponibles.', 'warn', 7000);
            }
        }

        function actualizarBarriosFavoritos() {
            estado.barriosFavoritos = new Set();
            estado.favoritos.forEach(function (favorito) {
                (Array.isArray(favorito.barrios) ? favorito.barrios : []).forEach(function (barrio) {
                    var nombre = normalizar(barrio);
                    if (nombre) estado.barriosFavoritos.add(nombre);
                });
            });
            estado.trayectosFavoritos.forEach(function (trayecto) {
                (Array.isArray(trayecto.barrios) ? trayecto.barrios : []).forEach(function (barrio) {
                    var nombre = normalizar(barrio);
                    if (nombre) estado.barriosFavoritos.add(nombre);
                });
            });
        }

        /* ===================== ORIGEN / DESTINO ===================== */
        function nombreVisible(n) {
            if (estado.servidos.has(n)) return estado.servidos.get(n);
            var b = estado.barrios.find(function (x) { return x.nombreN === n; });
            return b ? b.nombre : n;
        }

        function llenarLista(datalist, nombres) {
            var fragmento = document.createDocumentFragment();
            nombres.forEach(function (nombre) {
                var o = document.createElement('option');
                o.value = mayuscula(nombre);
                fragmento.appendChild(o);
            });
            vaciar(datalist);
            datalist.appendChild(fragmento);
        }

        function ordenarNombres(nombres) {
            return nombres.slice().sort(function (a, b) {
                var favoritoA = estado.barriosFavoritos.has(normalizar(a));
                var favoritoB = estado.barriosFavoritos.has(normalizar(b));
                if (favoritoA !== favoritoB) return favoritoA ? -1 : 1;
                return a.localeCompare(b, 'es');
            });
        }

        function favoritoDe(tipo, objetivoId) {
            return estado.favoritos.find(function (favorito) {
                var id = tipo === 'RUTA' ? favorito.rutaId : favorito.paradaId;
                return favorito.tipo === tipo && String(id) === String(objetivoId);
            });
        }

        function sincronizarBotonesFavoritos() {
            document.querySelectorAll('[data-favorito-tipo][data-favorito-objetivo]').forEach(function (boton) {
                pintarBotonFavorito(
                    boton,
                    boton.getAttribute('data-favorito-tipo'),
                    boton.getAttribute('data-favorito-objetivo'),
                    boton.getAttribute('data-favorito-nombre'));
            });
        }

        function pintarBotonFavorito(boton, tipo, objetivoId, nombre) {
            var guardado = favoritoDe(tipo, objetivoId);
            vaciar(boton);
            boton.className = 'favorite-button' + (guardado ? ' favorite-button--active' : '');
            boton.type = 'button';
            boton.disabled = !estado.favoritosHabilitados;
            boton.setAttribute('data-favorito-tipo', tipo);
            boton.setAttribute('data-favorito-objetivo', String(objetivoId));
            boton.setAttribute('data-favorito-nombre', nombre);
            boton.setAttribute('aria-pressed', String(Boolean(guardado)));
            boton.setAttribute('aria-label', (guardado ? 'Quitar ' + nombre + ' de' : 'Guardar ' + nombre + ' en') +
                ' favoritos');
            boton.title = guardado ? 'Quitar de favoritos' : 'Guardar en favoritos';
            boton.appendChild(icono(guardado ? 'fas fa-star' : 'far fa-star'));
            boton.appendChild(document.createTextNode(guardado ? ' Guardada' : ' Guardar'));
        }

        function crearBotonFavorito(tipo, objetivoId, nombre) {
            var boton = h('button', 'favorite-button');
            pintarBotonFavorito(boton, tipo, objetivoId, nombre);
            boton.addEventListener('click', function () {
                alternarFavorito(tipo, objetivoId, boton);
            });
            return boton;
        }

        async function alternarFavorito(tipo, objetivoId, boton) {
            if (!estado.favoritosHabilitados || boton.disabled) return;
            var existente = favoritoDe(tipo, objetivoId);
            var nombre = nombreObjetivo(tipo, objetivoId);
            boton.disabled = true;
            try {
                var csrfToken = document.querySelector('meta[name="_csrf"]');
                var csrfHeader = document.querySelector('meta[name="_csrf_header"]');
                if (!csrfToken || !csrfHeader || !csrfToken.content || !csrfHeader.content) {
                    throw new Error('No se pudo validar la sesión. Recarga la página e inténtalo de nuevo.');
                }

                var headers = { Accept: 'application/json' };
                headers[csrfHeader.content] = csrfToken.content;
                var opciones = { method: existente ? 'DELETE' : 'POST', credentials: 'same-origin', headers: headers };
                var endpoint = '/api/favoritos';
                if (existente) {
                    endpoint += '/' + encodeURIComponent(existente.id);
                } else {
                    headers['Content-Type'] = 'application/json';
                    opciones.body = JSON.stringify({
                        tipo: tipo,
                        rutaId: tipo === 'RUTA' ? Number(objetivoId) : null,
                        paradaId: tipo === 'PARADA' ? Number(objetivoId) : null
                    });
                }

                var respuesta = await fetch(M.url(endpoint), opciones);
                if (!respuesta.ok) {
                    var error = await respuesta.json().catch(function () { return {}; });
                    throw new Error(error.message || 'No se pudo actualizar el favorito.');
                }
                if (existente) {
                    estado.favoritos = estado.favoritos.filter(function (favorito) {
                        return String(favorito.id) !== String(existente.id);
                    });
                    M.mostrarAviso('Se quitó ' + nombreObjetivo(tipo, objetivoId) + ' de tus favoritos.', 'ok');
                } else {
                    var agregado = await respuesta.json();
                    if (!agregado || !agregado.id) {
                        throw new Error('El servidor no confirmó el favorito guardado.');
                    }
                    if (!favoritoDe(tipo, objetivoId)) estado.favoritos.push(agregado);
                    M.mostrarAviso('Se guardó ' + agregado.nombre + ' en tus favoritos.', 'ok');
                }
                actualizarBarriosFavoritos();
                poblarOrigen();
                poblarDestino();
                sincronizarBotonesFavoritos();
            } catch (error) {
                console.error('Error actualizando favorito:', error);
                M.mostrarAviso(error.message || 'No se pudo actualizar el favorito. Inténtalo nuevamente.', 'error', 8000);
            } finally {
                boton.disabled = false;
                sincronizarBotonesFavoritos();
            }
        }

        function nombreObjetivo(tipo, objetivoId) {
            var favorito = favoritoDe(tipo, objetivoId);
            if (favorito) return favorito.nombre;
            var ruta = estado.rutas.find(function (item) { return String(item.id) === String(objetivoId); });
            if (ruta && tipo === 'RUTA') return ruta.nombre;
            var parada = estado.rutas.reduce(function (resultado, item) {
                return resultado || item.paradas.find(function (punto) {
                    return String(punto.id) === String(objetivoId);
                });
            }, null);
            return parada ? parada.nombre : 'el elemento';
        }

        function mapaBarriosDisponibles() {
            var disponibles = new Map();
            estado.barrios.forEach(function (barrio) {
                if (barrio.nombreN && !disponibles.has(barrio.nombreN)) {
                    disponibles.set(barrio.nombreN, barrio.nombre);
                }
            });
            return disponibles;
        }

        function poblarOrigen() {
            llenarLista(el.listaOrigen, ordenarNombres(Array.from(mapaBarriosDisponibles().values())));
        }

        function destinosPermitidos(origenN) {
            var destinos = mapaBarriosDisponibles();
            destinos.delete(origenN);
            return destinos;
        }

        function poblarDestino() {
            if (!estado.origen) {
                el.destino.disabled = true;
                el.destino.placeholder = 'Primero elige el origen';
                llenarLista(el.listaDestino, []);
                return;
            }
            var destinos = Array.from(destinosPermitidos(estado.origen).values());
            el.destino.disabled = false;
            el.destino.placeholder = 'Escribe o elige el barrio de destino';
            llenarLista(el.listaDestino, ordenarNombres(destinos));
        }

        /** Devuelve el nombre normalizado si el texto coincide exactamente con un barrio válido, o ''. */
        function resolver(valor, permitidos) {
            var n = normalizar(valor);
            return n && permitidos.has(n) ? n : '';
        }

        function alCambiarOrigen() {
            var permitidos = mapaBarriosDisponibles();
            var n = resolver(el.origen.value, permitidos);
            if (n) el.origen.value = permitidos.get(n);
            if (n === estado.origen) return;
            estado.origen = n;
            estado.destino = '';
            el.destino.value = '';
            poblarDestino();
            limpiarMensajePlanificador();
            limpiarRuta();
            estado.tramos = [];
            estado.activo = null;
            estado.llegadaCandidata = 0;
            estado.llegadaInicioTimestamp = null;
            estado.llegadaAvisada = false;
            estado.resumenEstimado = null;
            estado.resumenConfirmado = false;
            actualizarPanelSeguimiento();
            ocultarDetalle();
            pintarResultados();
            actualizarURL();
        }

        function alCambiarDestino() {
            if (!estado.origen) return;
            var permitidos = destinosPermitidos(estado.origen);
            var n = resolver(el.destino.value, permitidos);
            if (n) el.destino.value = permitidos.get(n);
            if (n === estado.destino) return;
            estado.destino = n;
            limpiarMensajePlanificador();
            limpiarRuta();
            estado.tramos = [];
            estado.activo = null;
            estado.llegadaCandidata = 0;
            estado.llegadaInicioTimestamp = null;
            estado.llegadaAvisada = false;
            estado.resumenEstimado = null;
            estado.resumenConfirmado = false;
            actualizarPanelSeguimiento();
            ocultarDetalle();
            pintarResultados();
            actualizarURL();
        }

        function limpiarMensajePlanificador() {
            el.plannerStatus.textContent = '';
            el.plannerStatus.classList.remove('estado--error');
            el.origen.removeAttribute('aria-invalid');
            el.destino.removeAttribute('aria-invalid');
        }

        function mostrarErrorPlanificador(mensaje, campo) {
            el.plannerStatus.textContent = mensaje;
            el.plannerStatus.classList.add('estado--error');
            if (campo) {
                campo.setAttribute('aria-invalid', 'true');
                campo.focus();
            }
        }

        function planificar() {
            limpiarMensajePlanificador();
            var barrios = mapaBarriosDisponibles();
            var origen = resolver(el.origen.value, barrios);
            if (!el.origen.value.trim()) {
                mostrarErrorPlanificador('Completa el barrio de origen para planificar el viaje.', el.origen);
                return;
            }
            if (!origen) {
                mostrarErrorPlanificador('Elige un barrio de origen de la lista.', el.origen);
                return;
            }
            var destinos = destinosPermitidos(origen);
            var destino = resolver(el.destino.value, destinos);
            if (!el.destino.value.trim()) {
                mostrarErrorPlanificador('Completa el barrio de destino para planificar el viaje.', el.destino);
                return;
            }
            if (!destino) {
                mostrarErrorPlanificador('Elige un barrio de destino distinto del origen.', el.destino);
                return;
            }
            estado.origen = origen;
            estado.destino = destino;
            el.origen.value = barrios.get(origen);
            el.destino.value = destinos.get(destino);
            buscar();
        }

        function invertir() {
            var o = el.origen.value, d = el.destino.value;
            if (!o && !d) return;
            el.origen.value = d;
            estado.origen = '';
            alCambiarOrigen();
            if (o) {
                el.destino.value = o;
                alCambiarDestino();
                if (estado.origen && estado.destino) {
                    planificar();
                } else if (!estado.destino && estado.origen) {
                    M.mostrarAviso('No hay rutas verificadas en el sentido contrario. Las rutas tienen un solo sentido de recorrido.', 'warn', 6500);
                }
            }
        }

        function reiniciarConsulta() {
            el.origen.value = '';
            alCambiarOrigen();
            limpiarRuta();
            limpiarTranscaribe();
            mapa.flyTo(M.CENTRO, 13, { duration: 0.8 });
        }

        /* ===================== BÚSQUEDA DE TRAMOS ===================== */
        function paradasDelTramo(ruta, inicio, fin) {
            var ps = ruta.paradas;
            var primeraParada = ps.findIndex(function (parada) { return parada.indiceBarrio === inicio; });
            var ultimaParada = -1;
            for (var indice = ps.length - 1; indice >= 0; indice--) {
                if (ps[indice].indiceBarrio === fin) {
                    ultimaParada = indice;
                    break;
                }
            }
            return ps.filter(function (parada, indice) {
                if (parada.indiceBarrio != null) {
                    return parada.indiceBarrio >= inicio && parada.indiceBarrio <= fin;
                }
                if (parada.barrioN) return false;
                return primeraParada >= 0 && ultimaParada >= primeraParada
                    && indice >= primeraParada && indice <= ultimaParada;
            });
        }

        function crearTramo(ruta, inicio, fin) {
            return {
                ruta: ruta,
                sentido: ruta.sentido,
                indiceInicio: inicio,
                indiceFin: fin,
                barrios: ruta.barrios.slice(inicio, fin + 1),
                barriosN: ruta.barriosN.slice(inicio, fin + 1),
                paradas: paradasDelTramo(ruta, inicio, fin)
            };
        }

        function crearItinerario(tramos) {
            var barrios = [], barriosN = [];
            tramos.forEach(function (tramo) {
                tramo.barrios.forEach(function (barrio, indice) {
                    if (barriosN.length && indice === 0 && barriosN[barriosN.length - 1] === tramo.barriosN[indice]) return;
                    barrios.push(barrio);
                    barriosN.push(tramo.barriosN[indice]);
                });
            });
            var puntosTransbordo = tramos.slice(0, -1).map(function (tramo, indice) {
                var siguiente = tramos[indice + 1];
                var barrioN = tramo.barriosN[tramo.barriosN.length - 1];
                var indiceBajada = tramo.indiceFin;
                var indiceSubida = siguiente.indiceInicio;
                var bajar = tramo.paradas.filter(function (parada) {
                    return parada.indiceBarrio === indiceBajada;
                }).pop();
                var subir = siguiente.paradas.find(function (parada) {
                    return parada.indiceBarrio === indiceSubida;
                });
                var barrio = tramo.barrios[tramo.barrios.length - 1];
                var ubicacionesConocidas = bajar && subir
                    && Number.isFinite(bajar.lat) && Number.isFinite(bajar.lng)
                    && Number.isFinite(subir.lat) && Number.isFinite(subir.lng);
                var distanciaKm = ubicacionesConocidas
                    ? haversineKm(bajar.lat, bajar.lng, subir.lat, subir.lng)
                    : null;
                var mismoPunto = ubicacionesConocidas && distanciaKm <= 0.02;
                var mismaParada = ubicacionesConocidas
                    && normalizar(bajar.nombre) === normalizar(subir.nombre)
                    && mismoPunto;
                var detalle;
                if (mismaParada) {
                    detalle = 'Baja en ' + bajar.nombre + ' y aborda ' + nombreRutaEnSentido(siguiente.ruta) +
                        ' en esa misma parada (' + mayuscula(barrio) + ').';
                } else if (mismoPunto) {
                    detalle = 'Las paradas registradas para bajar (' + bajar.nombre + ') y abordar (' +
                        subir.nombre + ') están a menos de 20 m según sus coordenadas. Confirma en el lugar ' +
                        'que sea el punto correcto para tomar ' + nombreRutaEnSentido(siguiente.ruta) + '.';
                } else if (ubicacionesConocidas) {
                    detalle = 'Baja en ' + bajar.nombre + ' y camina hasta ' + subir.nombre +
                        ' para abordar ' + nombreRutaEnSentido(siguiente.ruta) + ' (' + mayuscula(barrio) +
                        '). Distancia directa estimada: ' + formatearDistancia(distanciaKm * 1000) +
                        '; no es una ruta peatonal.';
                } else if (bajar || subir) {
                    detalle = 'Transbordo en ' + mayuscula(barrio) + ': ' +
                        (bajar ? 'baja en ' + bajar.nombre : 'no hay parada de bajada registrada') + '; ' +
                        (subir ? 'la parada de abordaje es ' + subir.nombre : 'no hay parada de abordaje registrada') +
                        '. No se puede confirmar la caminata porque faltan coordenadas de paradas.';
                } else {
                    detalle = 'No hay paradas registradas para confirmar el transbordo en ' +
                        mayuscula(barrio) + ' hacia ' + nombreRutaEnSentido(siguiente.ruta) + '.';
                }
                return {
                    barrio: barrio, barrioN: barrioN, bajar: bajar, subir: subir,
                    distanciaKm: distanciaKm, mismaParada: mismaParada, mismoPunto: mismoPunto,
                    ubicacionesConocidas: !!ubicacionesConocidas,
                    caminataLarga: ubicacionesConocidas && distanciaKm >= 0.5,
                    detalle: detalle
                };
            });
            var transbordosSinCoordenadas = puntosTransbordo.filter(function (punto) {
                return !punto.ubicacionesConocidas;
            }).length;
            var distanciaTransbordosKm = puntosTransbordo.reduce(function (total, punto) {
                return total + (punto.ubicacionesConocidas ? punto.distanciaKm : 0);
            }, 0);
            return {
                ruta: tramos[0].ruta,
                tramos: tramos,
                barrios: barrios,
                barriosN: barriosN,
                transbordos: Math.max(0, tramos.length - 1),
                puntosTransbordo: puntosTransbordo,
                transbordosSinCoordenadas: transbordosSinCoordenadas,
                distanciaTransbordosKm: distanciaTransbordosKm,
                paradas: tramos.reduce(function (total, tramo) { return total + tramo.paradas.length; }, 0)
            };
        }

        function buscarTramos(origenN, destinoN) {
            var MAX_TRAMOS = estado.rutas.length, MAX_ESTADOS = 10000;
            var pendientes = [{ ubicacion: origenN, tramos: [], rutasUsadas: [], visitados: [origenN] }];
            var resultados = [], itinerariosVistos = new Set(), estadosVistos = new Set(), procesados = 0;

            while (pendientes.length && procesados < MAX_ESTADOS) {
                var estadoBusqueda = pendientes.shift();
                procesados++;
                if (estadoBusqueda.tramos.length >= MAX_TRAMOS) continue;

                estado.rutas.forEach(function (ruta) {
                    if (estadoBusqueda.rutasUsadas.indexOf(String(ruta.id)) >= 0) return;
                    ruta.sentidos.forEach(function (sentido) {
                        var indicesInicio = [];
                        sentido.barriosN.forEach(function (barrioN, indice) {
                            if (barrioN === estadoBusqueda.ubicacion) indicesInicio.push(indice);
                        });
                        indicesInicio.forEach(function (inicio) {
                            for (var fin = inicio + 1; fin < sentido.barriosN.length; fin++) {
                                var siguiente = sentido.barriosN[fin];
                                if (estadoBusqueda.visitados.indexOf(siguiente) >= 0 && siguiente !== destinoN) continue;
                                var tramos = estadoBusqueda.tramos.concat(crearTramo(sentido, inicio, fin));
                                if (siguiente === destinoN) {
                                    var clave = tramos.map(function (tramo) {
                                        return [tramo.ruta.id, tramo.sentido, tramo.indiceInicio, tramo.indiceFin].join(':');
                                    }).join(',');
                                    if (!itinerariosVistos.has(clave)) {
                                        itinerariosVistos.add(clave);
                                        resultados.push(crearItinerario(tramos));
                                    }
                                    continue;
                                }
                                if (tramos.length >= MAX_TRAMOS) continue;
                                var hayContinuacion = estado.rutas.some(function (otraRuta) {
                                    return String(otraRuta.id) !== String(ruta.id)
                                        && estadoBusqueda.rutasUsadas.indexOf(String(otraRuta.id)) < 0
                                        && otraRuta.sentidos.some(function (otroSentido) {
                                            return otroSentido.barriosN.some(function (barrioN, indice) {
                                                return barrioN === siguiente && indice < otroSentido.barriosN.length - 1;
                                            });
                                        });
                                });
                                if (!hayContinuacion) continue;
                                var nuevosUsados = estadoBusqueda.rutasUsadas.concat(String(ruta.id));
                                var claveEstado = nuevosUsados.join(',') + ':' + siguiente + ':'
                                    + tramos.map(function (tramo) {
                                        return [tramo.ruta.id, tramo.sentido, tramo.indiceInicio, tramo.indiceFin].join(':');
                                    }).join(',');
                                if (estadosVistos.has(claveEstado)) continue;
                                estadosVistos.add(claveEstado);
                                pendientes.push({
                                    ubicacion: siguiente,
                                    tramos: tramos,
                                    rutasUsadas: nuevosUsados,
                                    visitados: estadoBusqueda.visitados.concat(siguiente)
                                });
                            }
                        });
                    });
                });
            }

            resultados.sort(function (a, b) {
                var rutasA = a.tramos.map(function (tramo) { return nombreRutaEnSentido(tramo.ruta); }).join(' → ');
                var rutasB = b.tramos.map(function (tramo) { return nombreRutaEnSentido(tramo.ruta); }).join(' → ');
                return a.transbordos - b.transbordos
                    || a.transbordosSinCoordenadas - b.transbordosSinCoordenadas
                    || a.distanciaTransbordosKm - b.distanciaTransbordosKm
                    || a.barrios.length - b.barrios.length
                    || rutasA.localeCompare(rutasB, 'es');
            });
            return resultados;
        }

        function buscar() {
            limpiarRuta();
            ocultarDetalle();
            estado.activo = null;
            estado.llegadaCandidata = 0;
            estado.llegadaInicioTimestamp = null;
            estado.llegadaAvisada = false;
            estado.resumenEstimado = null;
            estado.resumenConfirmado = false;
            actualizarPanelSeguimiento();
            estado.tramos = estado.origen && estado.destino ? buscarTramos(estado.origen, estado.destino) : [];
            pintarResultados();
            actualizarURL();
            if (estado.tramos.length) seleccionarTramo(estado.tramos[0]);
        }

        /* ===================== RESULTADOS ===================== */
        function mostrarEsqueleto() {
            vaciar(el.resultados);
            for (var i = 0; i < 2; i++) el.resultados.appendChild(h('div', 'esqueleto'));
        }

        function estadoVacio(iconoClase, titulo, texto) {
            var caja = h('div', 'vacio');
            caja.appendChild(icono(iconoClase));
            caja.appendChild(h('strong', null, titulo));
            if (texto) caja.appendChild(h('p', null, texto));
            return caja;
        }

        function pintarErrorCarga() {
            vaciar(el.resultados);
            var caja = estadoVacio('fas fa-plug-circle-exclamation', 'No se pudo cargar la información',
                'Verifica tu conexión e inténtalo nuevamente.');
            var boton = h('button', 'btn btn--secundario', 'Reintentar');
            boton.type = 'button';
            boton.addEventListener('click', cargarDatos);
            caja.appendChild(boton);
            el.resultados.appendChild(caja);
        }

        function pintarResultados() {
            vaciar(el.resultados);
            el.resumenBusqueda.textContent = '';

            if (!estado.origen) {
                el.resultados.appendChild(estado.rutas.length
                    ? estadoVacio('fas fa-map-location-dot', 'Planifica tu viaje',
                        'Elige tu barrio de origen y destino para ver las rutas verificadas, sus paradas y los buses asignados.')
                    : estadoVacio('fas fa-route', 'No hay recorridos con sentido confirmado',
                        'Un administrador debe revisar y guardar el orden real de los barrios antes de que la ruta aparezca en esta consulta.'));
                return;
            }
            if (!estado.destino) {
                el.resultados.appendChild(estadoVacio('fas fa-location-dot', 'Ahora elige el destino',
                    'Te mostraremos las rutas disponibles ordenadas por transbordos y caminatas de conexión. La primera opción será la recomendada; selecciona otra para ver su recorrido en el mapa.'));
                return;
            }
            if (!estado.tramos.length) {
                var vacio = estadoVacio('fas fa-circle-xmark', 'No hay combinación de rutas',
                    'No existe una combinación de rutas verificadas desde ' + mayuscula(nombreVisible(estado.origen)) +
                    ' hasta ' + mayuscula(nombreVisible(estado.destino)) + '. Prueba con otros barrios o revisa el sentido del recorrido.');
                if (buscarTramos(estado.destino, estado.origen).length) {
                    var inverso = h('button', 'btn btn--secundario', 'Ver el sentido contrario');
                    inverso.type = 'button';
                    inverso.addEventListener('click', invertir);
                    vacio.appendChild(inverso);
                }
                el.resultados.appendChild(vacio);
                return;
            }

            var recomendada = estado.tramos[0];
            var barriosIntermedios = Math.max(0, recomendada.barrios.length - 2);
            var nombreRutas = recomendada.tramos.map(function (parte) {
                return nombreRutaEnSentido(parte.ruta);
            }).join(' → ');
            el.resumenBusqueda.textContent = estado.tramos.length + (estado.tramos.length === 1
                ? ' opción disponible. ' : ' opciones disponibles. ') +
                'Recomendada: ' + nombreRutas + ' · ' +
                barriosIntermedios + (barriosIntermedios === 1 ? ' barrio intermedio' : ' barrios intermedios') +
                (recomendada.transbordos
                    ? ' · ' + recomendada.transbordos +
                        (recomendada.transbordos === 1 ? ' transbordo' : ' transbordos')
                    : ' · directa') +
                '. Se priorizan menos transbordos, conexiones con paradas ubicables y menor distancia en línea recta entre paradas; luego, menos barrios intermedios. Esa distancia no representa una ruta peatonal. El mapa muestra la opción seleccionada; su tiempo es una estimación en auto, no el tiempo real del bus.';
            el.resultados.appendChild(h('p', 'resultados__resumen', el.resumenBusqueda.textContent));

            estado.tramos.forEach(function (tramo, indice) {
                el.resultados.appendChild(crearTarjetaTramo(tramo, indice));
            });
        }

        function crearTarjetaTramo(tramo, indice) {
            var activa = estado.activo === tramo;
            var tarjeta = h('button', 'tramo' + (activa ? ' tramo--activo' : ''));
            tarjeta.type = 'button';
            tarjeta.setAttribute('aria-pressed', String(activa));
            tarjeta.addEventListener('click', function () { seleccionarTramo(tramo); });

            var cabecera = h('div', 'tramo__cabecera');
            cabecera.appendChild(h('span', 'tramo__nombre',
                tramo.transbordos ? tramo.transbordos + (tramo.transbordos === 1 ? ' transbordo' : ' transbordos') : 'Directa'));
            if (indice === 0) cabecera.appendChild(h('span', 'chip chip--ok', 'Recomendada'));
            tarjeta.appendChild(cabecera);

            tarjeta.appendChild(h('span', 'tramo__rutas', tramo.tramos.map(function (parte) {
                return nombreRutaEnSentido(parte.ruta);
            }).join(' → ')));
            var cadena = h('div', 'tramo__cadena');
            cadena.appendChild(h('span', null, mayuscula(tramo.barrios[0])));
            tramo.tramos.forEach(function (parte, indiceParte) {
                cadena.appendChild(icono('fas fa-arrow-right'));
                cadena.appendChild(h('span', null, mayuscula(parte.barrios[parte.barrios.length - 1])));
                if (indiceParte < tramo.tramos.length - 1) {
                    cadena.appendChild(h('span', 'chip chip--transbordo', tramo.puntosTransbordo[indiceParte].detalle));
                }
            });
            tarjeta.appendChild(cadena);

            var meta = h('div', 'tramo__meta');
            var buses = tramo.tramos.reduce(function (totalBuses, parte) { return totalBuses + parte.ruta.buses.length; }, 0);
            var datos = [
                ['fas fa-right-left', tramo.transbordos ? tramo.transbordos + (tramo.transbordos === 1 ? ' transbordo' : ' transbordos') : 'Sin transbordos'],
                ['fas fa-location-dot', tramo.paradas ? tramo.paradas + ' paradas' : 'Sin paradas registradas'],
                ['fas fa-bus', buses ? buses + (buses === 1 ? ' bus asignado' : ' buses asignados') : 'Sin buses asignados']
            ];
            datos.forEach(function (d) {
                var item = h('span', 'tramo__dato');
                item.appendChild(icono(d[0]));
                item.appendChild(document.createTextNode(' ' + d[1]));
                meta.appendChild(item);
            });
            tarjeta.appendChild(meta);
            return tarjeta;
        }

        /* ===================== SELECCIÓN Y DIBUJO ===================== */
        function construirItemsDeTramo(tramo) {
            var items = [];
            var paradasConCoordenadas = tramo.paradas.filter(function (parada) {
                return Number.isFinite(parada.lat) && Number.isFinite(parada.lng);
            });
            if (paradasConCoordenadas.length >= 2) {
                paradasConCoordenadas.forEach(function (parada) {
                    items.push({ paradaId: parada.id, nombre: parada.nombre, barrio: parada.barrio, referencia: parada.referencia,
                        ubicacion: parada.ubicacion, lat: parada.lat, lng: parada.lng, tipo: 'parada' });
                });
                return items;
            }
            tramo.barrios.forEach(function (barrio, indice) {
                var coordenadas = tramo.ruta.coordenadas[barrio];
                var latitud, longitud;
                if (Array.isArray(coordenadas) && coordenadas.length === 2) {
                    latitud = Number(coordenadas[0]);
                    longitud = Number(coordenadas[1]);
                } else {
                    var respaldo = estado.coordenadas[tramo.barriosN[indice]];
                    if (respaldo) { latitud = respaldo.lat; longitud = respaldo.lng; }
                }
                if (Number.isFinite(latitud) && Number.isFinite(longitud)) {
                    items.push({
                        nombre: mayuscula(barrio), barrio: barrio, lat: latitud, lng: longitud, tipo: 'barrio',
                        coordenadaEstimada: tramo.ruta.coordenadasEstimadas.has(tramo.barriosN[indice])
                    });
                }
            });
            return items;
        }

        function construirItems(itinerario) {
            var items = [];
            itinerario.tramos.forEach(function (tramo, indiceTramo) {
                var puntos = construirItemsDeTramo(tramo);
                if (!puntos.length) return;
                puntos.forEach(function (punto) { punto.rutaNombre = nombreRutaEnSentido(tramo.ruta); });
                if (indiceTramo > 0 && items.length) {
                    var anterior = items[items.length - 1];
                    var primero = puntos[0];
                    if (anterior.tipo === 'parada') {
                        anterior.transbordo = true;
                        anterior.rutaSiguiente = nombreRutaEnSentido(tramo.ruta);
                    }
                    if (primero.tipo === 'parada') {
                        primero.transbordo = true;
                        primero.rutaSiguiente = nombreRutaEnSentido(tramo.ruta);
                    }
                    if (anterior.barrio && primero.barrio
                        && normalizar(anterior.barrio) === normalizar(primero.barrio)) {
                        primero.nombre = anterior.nombre;
                    }
                    if (anterior.tipo === 'parada' && primero.tipo === 'parada'
                        && anterior.lat === primero.lat && anterior.lng === primero.lng
                        && normalizar(anterior.nombre) === normalizar(primero.nombre)) {
                        items[items.length - 1] = Object.assign(anterior, primero);
                        puntos.shift();
                    }
                }
                Array.prototype.push.apply(items, puntos);
            });
            items.forEach(function (item, indice) { item.numero = indice + 1; });
            return items;
        }

        function seleccionarTramo(tramo) {
            detenerCompartirLlegadas();
            limpiarTemporizadorLlegadas();
            limpiarTranscaribe();
            limpiarRuta();
            estado.activo = tramo;
            estado.movimientoRuta = { ultimaMuestra: null, avances: 0, detectado: false };
            estado.llegadaCandidata = 0;
            estado.llegadaAvisada = false;
            estado.resumenEstimado = null;
            estado.resumenConfirmado = false;
            estado.pestana = 'paradas';
            pintarResultados();
            actualizarURL();
            actualizarPanelSeguimiento();
            dibujarTramo(tramo);
        }

        async function dibujarTramo(tramo) {
            var items = construirItems(tramo);
            estado.items = items;
            pintarDetalle(tramo);
            if (items.some(function (item) { return item.coordenadaEstimada; })) {
                M.mostrarAviso(
                    'Algunos puntos usan ubicaciones aproximadas de barrio. Verifica el recorrido antes de viajar.',
                    'warn',
                    8000
                );
            }

            if (items.length < 2) {
                pintarStats({ sinCoordenadas: true });
                estado.resumenEstimado = null;
                M.mostrarAviso('Esta ruta no tiene coordenadas suficientes para trazar el recorrido.', 'warn');
                return;
            }

            var ultimo = items.length - 1;
            items.forEach(function (it, i) {
                if (i === 0 || i === ultimo) {
                    var tipo = i === 0 ? 'origen' : 'destino';
                    var nota = it.coordenadaEstimada ? 'Ubicación aproximada; verifica el recorrido antes de viajar.' : null;
                    var pin = M.crearPin(it.lat, it.lng, tipo, it.nombre, nota).addTo(mapa);
                    it.marcador = pin;
                    if (i === 0) capas.pinOrigen = pin; else capas.pinDestino = pin;
                } else {
                    it.marcador = M.crearMarcadorParada(it.lat, it.lng, it.numero, it);
                    capas.paradas.addLayer(it.marcador);
                }
            });
            dibujarCaminatasTransbordo(tramo);
            if (!el.mostrarParadas.checked) mapa.removeLayer(capas.paradas);

            var limites = L.latLngBounds(items.map(function (p) { return [p.lat, p.lng]; }));
            if (capas.usuario) limites.extend(capas.usuario.getLatLng());
            M.ajustarVista(mapa, limites);
            pintarStats({ cargando: true, paradas: items.length });

            var solicitud = ++estado.solicitud;
            if (estado.controlador) estado.controlador.abort();
            var controlador = new AbortController();
            estado.controlador = controlador;
            M.mostrarCargando('Calculando ruta por calles…');
            try {
                var resultado = await M.obtenerRutaOSRM(muestrear(items, MAX_PUNTOS_OSRM), controlador.signal);
                if (solicitud !== estado.solicitud) return;
                var c = M.crearCapasRuta(resultado.geometry);
                capas.base = c.base.addTo(mapa);
                capas.linea = c.linea.addTo(mapa);
                dibujarFlujo(resultado.geometry);
                establecerTrayecto(resultado.geometry);
                var b = capas.linea.getBounds();
                if (capas.usuario) b.extend(capas.usuario.getLatLng());
                M.ajustarVista(mapa, b);
                estado.resumenEstimado = {
                    distanciaKm: resultado.distanciaKm,
                    tiempoMin: resultado.tiempoMin
                };
                pintarStats({ distanciaKm: resultado.distanciaKm, tiempoMin: resultado.tiempoMin, paradas: items.length });
            } catch (err) {
                if (solicitud !== estado.solicitud) return;
                console.error('Error OSRM:', err);
                estado.resumenEstimado = null;
                pintarStats({ error: true, paradas: items.length });
                M.mostrarAviso(err.name === 'AbortError'
                    ? 'El servicio de rutas tardó demasiado. Las paradas siguen disponibles en la lista.'
                    : 'No se pudo calcular el trazado por calles. Las paradas siguen disponibles en la lista.', 'error');
            } finally {
                if (estado.controlador === controlador) estado.controlador = null;
                M.ocultarCargando();
            }
        }

        function dibujarFlujo(geometria, color) {
            if (capas.flujo) mapa.removeLayer(capas.flujo);
            capas.flujo = L.geoJSON(geometria, {
                style: { color: color || '#c9ffff', weight: 3, opacity: 0.95, dashArray: '8 18', className: 'route-flow', lineCap: 'round' },
                interactive: false
            }).addTo(mapa);
        }

        function dibujarCaminatasTransbordo(itinerario) {
            if (capas.caminatas) mapa.removeLayer(capas.caminatas);
            capas.caminatas = L.layerGroup();
            itinerario.puntosTransbordo.forEach(function (punto) {
                if (!punto.ubicacionesConocidas || punto.mismoPunto) return;
                var linea = L.polyline(
                    [[punto.bajar.lat, punto.bajar.lng], [punto.subir.lat, punto.subir.lng]],
                    { color: '#f59e0b', weight: 4, opacity: 0.95, dashArray: '6 8', lineCap: 'round' }
                );
                linea.bindTooltip('Conexión a pie: ' + formatearDistancia(punto.distanciaKm * 1000) +
                    ' en línea recta (estimación, no es una ruta peatonal).');
                capas.caminatas.addLayer(linea);
            });
            if (capas.caminatas.getLayers().length) capas.caminatas.addTo(mapa);
            else capas.caminatas = null;
        }

        function limpiarRuta() {
            estado.solicitud++;
            if (estado.controlador) { estado.controlador.abort(); estado.controlador = null; }
            [capas.pinOrigen, capas.pinDestino, capas.base, capas.linea, capas.flujo, capas.caminatas].forEach(function (capa) {
                if (capa) mapa.removeLayer(capa);
            });
            capas.pinOrigen = capas.pinDestino = capas.base = capas.linea = capas.flujo = capas.caminatas = null;
            capas.paradas.clearLayers();
            estado.items = [];
            establecerTrayecto(null);
        }

        /* ===================== DETALLE ===================== */
        function ocultarDetalle() {
            detenerCompartirLlegadas();
            limpiarTemporizadorLlegadas();
            estado.tarjetasLlegadas = [];
            if (el.llegadasRutaPanel) {
                el.llegadasRutaPanel.hidden = true;
                vaciar(el.llegadasRutaPanel);
            }
            el.rutaDetalle.hidden = true;
        }

        function pintarDetalle(tramo) {
            var rutas = tramo.tramos.map(function (parte) { return parte.ruta; });
            el.rutaDetalle.hidden = false;
            el.detalleTitulo.textContent = tramo.tramos.map(function (parte) {
                return nombreRutaEnSentido(parte.ruta);
            }).join(' → ');
            el.detalleTramo.textContent = mayuscula(tramo.barrios[0]) + ' → ' +
                mayuscula(tramo.barrios[tramo.barrios.length - 1]) +
                (tramo.puntosTransbordo.length
                    ? ' · ' + tramo.puntosTransbordo.map(function (punto) { return punto.detalle; }).join(' · ')
                    : ' · Viaje directo');
            var horas = rutas.map(function (ruta) { return formatearHora(ruta.hora); }).filter(Boolean);
            el.detalleHora.textContent = horas.length
                ? 'Horas aproximadas por ruta: ' + horas.join(' · ')
                : 'Horarios no disponibles';
            el.contadorParadas.textContent = String(estado.items.length);
            el.contadorBuses.textContent = String(rutas.reduce(function (total, ruta) { return total + ruta.buses.length; }, 0));
            if (estado.favoritosHabilitados) {
                vaciar(el.favoriteActions);
                rutas.forEach(function (ruta) {
                    el.favoriteActions.appendChild(
                        crearBotonFavorito('RUTA', ruta.id, ruta.nombre));
                });
            }
            prepararLlegadas(tramo);
            pintarParadas();
            pintarBuses(tramo);
            pintarInfo(tramo);
            cambiarPestana(estado.pestana);
        }

        function prepararLlegadas(tramo) {
            var panel = el.llegadasRutaPanel;
            vaciar(panel);
            panel.hidden = !tramo.tramos.length;
            estado.tarjetasLlegadas = [];
            if (panel.hidden) return;

            panel.appendChild(h('h3', 'arrival-panel__title', 'Llegadas estimadas'));
            panel.appendChild(h('p', 'arrival-panel__note',
                'Rangos orientativos calculados con ubicaciones recientes de pasajeros que aceptaron compartir. ' +
                'El tráfico, los desvíos y las paradas pueden cambiar el resultado; no es una hora garantizada.'));

            tramo.tramos.forEach(function (parte) {
                var tarjeta = h('article', 'arrival-card');
                var cabecera = h('div', 'arrival-card__header');
                cabecera.appendChild(h('h4', 'arrival-card__title', nombreRutaEnSentido(parte.ruta)));
                var selector = document.createElement('select');
                selector.className = 'arrival-card__direction';
                selector.setAttribute('aria-label', 'Sentido para ' + parte.ruta.nombre);
                var sentidosDisponibles = parte.ruta.sentidosDisponibles || [parte.sentido || 'IDA'];
                sentidosDisponibles.forEach(function (sentido) {
                    var elemento = document.createElement('option');
                    elemento.value = sentido;
                    elemento.textContent = sentido === 'VUELTA' ? 'Vuelta' : 'Ida';
                    selector.appendChild(elemento);
                });
                selector.value = parte.sentido || 'IDA';
                selector.hidden = sentidosDisponibles.length < 2;
                cabecera.appendChild(selector);
                tarjeta.appendChild(cabecera);

                var estadoLlegada = h('p', 'arrival-card__status', 'Consultando reportes recientes…');
                estadoLlegada.setAttribute('role', 'status');
                estadoLlegada.setAttribute('aria-live', 'polite');
                tarjeta.appendChild(estadoLlegada);
                var lista = h('ol', 'arrival-card__list');
                tarjeta.appendChild(lista);

                var control = {
                    rutaId: parte.ruta.id,
                    rutaNombre: nombreRutaEnSentido(parte.ruta),
                    sentido: parte.sentido || 'IDA',
                    selector: selector,
                    estado: estadoLlegada,
                    lista: lista,
                    boton: null,
                    estadoCompartir: null,
                    cargando: false,
                    solicitud: 0
                };
                selector.addEventListener('change', function () {
                    control.sentido = selector.value;
                    control.solicitud++;
                    control.cargando = false;
                    actualizarBotonesCompartir();
                    cargarLlegadasTarjeta(control);
                });

                if (privado) {
                    var acciones = h('div', 'arrival-card__actions');
                    var boton = h('button', 'btn btn--secundario arrival-card__share');
                    boton.type = 'button';
                    boton.addEventListener('click', function () { alternarCompartirLlegadas(control); });
                    control.boton = boton;
                    acciones.appendChild(boton);
                    var estadoCompartir = h('p', 'arrival-card__sharing');
                    estadoCompartir.setAttribute('role', 'status');
                    estadoCompartir.setAttribute('aria-live', 'polite');
                    acciones.appendChild(estadoCompartir);
                    tarjeta.appendChild(acciones);
                    control.estadoCompartir = estadoCompartir;
                }

                panel.appendChild(tarjeta);
                estado.tarjetasLlegadas.push(control);
                cargarLlegadasTarjeta(control);
            });

            if (privado) {
                panel.appendChild(h('p', 'arrival-panel__consent',
                    'Compartir es opcional: solo se envía tu GPS después de pulsar el botón, con precisión suficiente. ' +
                    'Tu identidad y coordenadas exactas no se muestran; puedes detenerlo cuando quieras. ' +
                    'Los reportes caducan automáticamente tras 90 segundos.'));
            } else {
                panel.appendChild(h('p', 'arrival-panel__consent',
                    'Los reportes solo pueden enviarlos usuarios con sesión iniciada que confirmen voluntariamente que van a bordo.'));
            }

            limpiarTemporizadorLlegadas();
            estado.temporizadorLlegadas = window.setInterval(actualizarLlegadasSeleccionadas, 20000);
        }

        function limpiarTemporizadorLlegadas() {
            if (estado.temporizadorLlegadas !== null) {
                window.clearInterval(estado.temporizadorLlegadas);
                estado.temporizadorLlegadas = null;
            }
        }

        function actualizarLlegadasSeleccionadas() {
            if (document.hidden) return;
            estado.tarjetasLlegadas.forEach(cargarLlegadasTarjeta);
        }

        async function cargarLlegadasTarjeta(tarjeta) {
            if (!tarjeta || tarjeta.cargando || !estado.tarjetasLlegadas.includes(tarjeta)) return;
            tarjeta.cargando = true;
            var solicitud = ++tarjeta.solicitud;
            tarjeta.estado.setAttribute('aria-busy', 'true');
            try {
                var respuesta = await fetch(M.url('/api/llegadas/' + encodeURIComponent(tarjeta.rutaId) +
                    '?sentido=' + encodeURIComponent(tarjeta.sentido)), {
                    headers: { Accept: 'application/json' },
                    credentials: 'same-origin',
                    cache: 'no-store'
                });
                if (!respuesta.ok) throw new Error('HTTP ' + respuesta.status);
                var datos = await respuesta.json();
                if (solicitud !== tarjeta.solicitud || !estado.tarjetasLlegadas.includes(tarjeta)) return;
                tarjeta.estado.textContent = datos.mensaje || 'No hay información de llegada disponible.';
                tarjeta.estado.className = 'arrival-card__status' +
                    (datos.estado === 'ESTIMADO' ? ' arrival-card__status--ready' : '');
                var detalles = [];
                if (Number.isFinite(datos.reportantesActivos)) {
                    detalles.push(datos.reportantesActivos + (datos.reportantesActivos === 1
                        ? ' pasajero compartiendo' : ' pasajeros compartiendo'));
                }
                if (datos.actualizadoEn) {
                    var actualizado = Date.parse(datos.actualizadoEn);
                    if (Number.isFinite(actualizado)) {
                        var segundos = Math.max(0, Math.floor((Date.now() - actualizado) / 1000));
                        detalles.push('última actualización hace ' +
                            (segundos < 60 ? segundos + ' s' : Math.floor(segundos / 60) + ' min'));
                    }
                }
                if (detalles.length) tarjeta.estado.appendChild(document.createTextNode(' ' + detalles.join(' · ') + '.'));

                vaciar(tarjeta.lista);
                (Array.isArray(datos.llegadas) ? datos.llegadas : []).forEach(function (llegada) {
                    var fila = h('li', 'arrival-card__item');
                    var lugar = h('span', 'arrival-card__stop');
                    lugar.appendChild(h('strong', null, llegada.parada || 'Parada'));
                    if (llegada.barrio) lugar.appendChild(h('small', null, llegada.barrio));
                    fila.appendChild(lugar);
                    var tiempo = h('span', 'arrival-card__time',
                        llegada.minutosMinimos + '–' + llegada.minutosMaximos + ' min');
                    tiempo.appendChild(h('small', null, 'confianza ' + String(llegada.confianza || 'baja').toLowerCase()));
                    fila.appendChild(tiempo);
                    tarjeta.lista.appendChild(fila);
                });
            } catch (error) {
                if (solicitud !== tarjeta.solicitud || !estado.tarjetasLlegadas.includes(tarjeta)) return;
                console.error('Error consultando llegadas estimadas:', error);
                tarjeta.estado.className = 'arrival-card__status arrival-card__status--error';
                tarjeta.estado.textContent = 'No se pudieron consultar las llegadas. Revisa tu conexión e inténtalo de nuevo.';
                vaciar(tarjeta.lista);
            } finally {
                if (solicitud === tarjeta.solicitud) {
                    tarjeta.cargando = false;
                    tarjeta.estado.removeAttribute('aria-busy');
                }
            }
        }

        function actualizarBotonesCompartir() {
            estado.tarjetasLlegadas.forEach(function (tarjeta) {
                if (!tarjeta.boton) return;
                var actual = estado.reportandoLlegadas;
                var activo = actual && actual.rutaId === tarjeta.rutaId && actual.sentido === tarjeta.sentido;
                tarjeta.boton.replaceChildren(
                    icono(activo ? 'fas fa-stop' : 'fas fa-location-dot'),
                    document.createTextNode(activo ? ' Detener ubicación compartida' : ' Estoy a bordo · compartir GPS'));
                tarjeta.boton.setAttribute('aria-pressed', String(Boolean(activo)));
                tarjeta.selector.disabled = Boolean(actual);
                if (activo) {
                    tarjeta.estadoCompartir.textContent =
                        'Compartir activo por solicitud tuya. La última ubicación recibida caduca en 90 segundos.';
                } else if (actual) {
                    tarjeta.estadoCompartir.textContent = 'Hay otro tramo compartiéndose en esta sesión.';
                } else {
                    tarjeta.estadoCompartir.textContent = '';
                }
            });
        }

        async function alternarCompartirLlegadas(tarjeta) {
            var actual = estado.reportandoLlegadas;
            if (actual && actual.rutaId === tarjeta.rutaId && actual.sentido === tarjeta.sentido) {
                await detenerCompartirLlegadas();
                return;
            }
            if (!privado || !navigator.geolocation) {
                tarjeta.estadoCompartir.textContent = 'Este navegador no permite compartir ubicación GPS.';
                return;
            }
            if (actual) await detenerCompartirLlegadas();

            var csrf = encabezadosCSRF();
            if (!csrf) {
                tarjeta.estadoCompartir.textContent =
                    'No se pudo validar la sesión. Recarga la página e inténtalo de nuevo.';
                return;
            }
            estado.reportandoLlegadas = {
                rutaId: tarjeta.rutaId,
                sentido: tarjeta.sentido,
                tarjeta: tarjeta,
                ultimaSolicitud: 0,
                peticion: null,
                publicado: false
            };
            actualizarBotonesCompartir();
            actualizarEstadoUbicacion();
            tarjeta.estadoCompartir.textContent =
                'Esperando una ubicación GPS reciente y precisa. Puedes detener el envío en cualquier momento.';
            if (estado.seguimiento === null) iniciarSeguimiento();
            if (estado.ubicacion && Date.now() - estado.ubicacion.timestamp <= 30000) {
                enviarUbicacionCompartida();
            }
        }

        function encabezadosCSRF() {
            var token = document.querySelector('meta[name="_csrf"]');
            var nombre = document.querySelector('meta[name="_csrf_header"]');
            if (!token || !nombre || !token.content || !nombre.content) return null;
            var headers = { Accept: 'application/json' };
            headers[nombre.content] = token.content;
            return headers;
        }

        function enviarUbicacionCompartida() {
            var actual = estado.reportandoLlegadas;
            var ubicacion = estado.ubicacion;
            if (!actual || actual.peticion || !ubicacion) return;
            if (Date.now() - ubicacion.timestamp > 30000) {
                actual.tarjeta.estadoCompartir.textContent = 'Esperando una ubicación GPS actualizada…';
                return;
            }
            if (!Number.isFinite(ubicacion.precision) || ubicacion.precision > 65) {
                actual.tarjeta.estadoCompartir.textContent =
                    'No se comparte todavía: el GPS necesita una precisión de 65 m o mejor.';
                return;
            }
            if (Date.now() - actual.ultimaSolicitud < 8000) return;

            actual.ultimaSolicitud = Date.now();
            var csrf = encabezadosCSRF();
            if (!csrf) {
                actual.tarjeta.estadoCompartir.textContent =
                    'No se pudo validar la sesión. Recarga la página e inténtalo de nuevo.';
                return;
            }
            var opciones = {
                method: 'POST',
                credentials: 'same-origin',
                headers: Object.assign({ 'Content-Type': 'application/json' }, csrf),
                body: JSON.stringify({
                    latitud: ubicacion.lat,
                    longitud: ubicacion.lng,
                    precisionMetros: ubicacion.precision
                })
            };
            var ruta = M.url('/api/llegadas/' + encodeURIComponent(actual.rutaId) +
                '/reportes?sentido=' + encodeURIComponent(actual.sentido));
            actual.peticion = fetch(ruta, opciones).then(async function (respuesta) {
                if (!respuesta.ok) {
                    var cuerpo = {};
                    try { cuerpo = await respuesta.json(); } catch (ignorado) { /* respuesta sin JSON */ }
                    var error = new Error(cuerpo.detail || cuerpo.message || 'No se pudo compartir la ubicación.');
                    error.status = respuesta.status;
                    throw error;
                }
                actual.publicado = true;
                if (estado.reportandoLlegadas === actual) {
                    actual.tarjeta.estadoCompartir.textContent =
                        'Ubicación compartida de forma temporal; se renovará mientras continúes a bordo y con GPS activo.';
                    estado.tarjetasLlegadas.forEach(cargarLlegadasTarjeta);
                }
            }).catch(function (error) {
                console.error('Error compartiendo ubicación de pasajero:', error);
                if (estado.reportandoLlegadas !== actual) return;
                actual.tarjeta.estadoCompartir.textContent = error.message ||
                    'No se pudo compartir la ubicación. Inténtalo de nuevo.';
                if (error.status !== 429) {
                    estado.reportandoLlegadas = null;
                    actualizarBotonesCompartir();
                    actualizarEstadoUbicacion();
                    if (actual.publicado) {
                        eliminarReportesCompartidos(actual.tarjeta, document.hidden).then(function (eliminado) {
                            actual.tarjeta.estadoCompartir.textContent = eliminado
                                ? 'El envío se detuvo y los reportes anteriores se eliminaron.'
                                : 'El envío se detuvo; el último reporte caducará automáticamente en 90 segundos.';
                        });
                    }
                }
            }).finally(function () {
                actual.peticion = null;
            });
        }

        async function detenerCompartirLlegadas() {
            var actual = estado.reportandoLlegadas;
            if (!actual) return;
            estado.reportandoLlegadas = null;
            actualizarBotonesCompartir();
            actualizarEstadoUbicacion();
            actual.tarjeta.estadoCompartir.textContent = 'Deteniendo el envío de ubicación…';
            if (actual.peticion) await actual.peticion;
            if (!actual.publicado) {
                actual.tarjeta.estadoCompartir.textContent = 'El envío de ubicación está detenido.';
                return;
            }

            var eliminado = await eliminarReportesCompartidos(
                actual.tarjeta, document.visibilityState === 'hidden');
            actual.tarjeta.estadoCompartir.textContent = eliminado
                ? 'Ubicación compartida detenida y reporte eliminado.'
                : 'El envío del dispositivo se detuvo; el reporte caducará automáticamente en 90 segundos.';
        }

        async function eliminarReportesCompartidos(tarjeta, keepalive) {
            var csrf = encabezadosCSRF();
            if (!csrf) return false;
            try {
                var respuesta = await fetch(M.url('/api/llegadas/reportes/actual'), {
                    method: 'DELETE',
                    credentials: 'same-origin',
                    headers: csrf,
                    keepalive: keepalive
                });
                if (!respuesta.ok) throw new Error('HTTP ' + respuesta.status);
                return true;
            } catch (error) {
                console.error('Error eliminando reportes anteriores de ubicación:', error);
                if (tarjeta && tarjeta.estadoCompartir) {
                    tarjeta.estadoCompartir.textContent =
                        'El envío del dispositivo se detuvo; el reporte caducará automáticamente en 90 segundos.';
                }
                return false;
            }
        }

        function cambiarPestana(nombre) {
            estado.pestana = nombre;
            document.querySelectorAll('[role="tab"][data-pestana]').forEach(function (boton) {
                var activa = boton.getAttribute('data-pestana') === nombre;
                boton.setAttribute('aria-selected', String(activa));
                boton.tabIndex = activa ? 0 : -1;
                boton.classList.toggle('pestana--activa', activa);
            });
            el.panelParadas.hidden = nombre !== 'paradas';
            el.panelBuses.hidden = nombre !== 'buses';
            el.panelInfo.hidden = nombre !== 'info';
        }

        function navegarPestanas(evento) {
            var botones = Array.from(document.querySelectorAll('[role="tab"][data-pestana]'));
            var i = botones.indexOf(evento.currentTarget), d = 0;
            if (evento.key === 'ArrowRight') d = 1; else if (evento.key === 'ArrowLeft') d = -1; else return;
            evento.preventDefault();
            var destino = botones[(i + d + botones.length) % botones.length];
            destino.focus();
            cambiarPestana(destino.getAttribute('data-pestana'));
        }

        function pintarStats(datos) {
            var cont = el.rutaStats;
            vaciar(cont);
            cont.className = 'mu-stats';
            if (datos.error || datos.sinCoordenadas) {
                cont.className = 'mu-stats mu-stats--error';
                cont.textContent = datos.sinCoordenadas
                    ? 'Esta ruta no tiene coordenadas suficientes para dibujarse en el mapa.'
                    : 'No se pudo calcular el trazado por calles. No se dibuja una línea aproximada para no confundirla con el recorrido real del bus.';
                return;
            }
            function dato(valor, etiqueta) {
                var d = h('div', 'mu-stat');
                d.appendChild(h('b', null, valor));
                d.appendChild(h('small', null, etiqueta));
                return d;
            }
            cont.appendChild(dato(datos.cargando ? '…' : datos.distanciaKm.toFixed(1) + ' km', 'Distancia'));
            cont.appendChild(dato(datos.cargando ? '…' : '~' + datos.tiempoMin + ' min', 'En auto'));
            cont.appendChild(dato(String(datos.paradas), 'Puntos'));
        }

        function pintarParadas() {
            var panel = el.panelParadas;
            vaciar(panel);
            var items = estado.items;
            if (!items.length) {
                panel.appendChild(estadoVacio('fas fa-location-dot', 'Sin paradas para mostrar',
                    'Esta ruta aún no tiene paradas con coordenadas registradas.'));
                pintarParadasSinCoordenadas(panel, items);
                return;
            }
            if (items[0].tipo === 'barrio') {
                panel.appendChild(h('p', 'nota', 'Esta ruta no tiene paradas físicas registradas. Los puntos corresponden al centro de cada barrio del tramo.'));
            }
            var lista = h('ol', 'linea-tiempo');
            items.forEach(function (it, i) {
                var rol = i === 0 ? 'origen' : (i === items.length - 1 ? 'destino' : 'medio');
                var li = h('li', 'parada parada--' + rol);
                var boton = h('button', 'parada__boton');
                boton.type = 'button';
                boton.setAttribute('aria-label', 'Ver ' + it.nombre + ' en el mapa');
                var punto = h('span', 'parada__punto', rol === 'origen' ? 'A' : rol === 'destino' ? 'B' : String(it.numero));
                var texto = h('span', 'parada__texto');
                texto.appendChild(h('strong', null, it.nombre));
                var detalle = [it.barrio, it.referencia, it.ubicacion].filter(Boolean).filter(function (v, k, a) { return a.indexOf(v) === k && v !== it.nombre; });
                if (detalle.length) texto.appendChild(h('small', null, detalle.join(' · ')));
                if (rol === 'origen') texto.appendChild(h('span', 'chip chip--origen', 'Origen'));
                if (rol === 'destino') texto.appendChild(h('span', 'chip chip--destino', 'Destino'));
                if (it.transbordo) {
                    texto.appendChild(h('span', 'chip chip--transbordo',
                        'Transbordo · cambiar a ' + it.rutaSiguiente));
                    var paso = estado.activo && estado.activo.puntosTransbordo.find(function (punto) {
                        return punto.barrioN === it.barrioN;
                    });
                    if (paso && paso.ubicacionesConocidas && paso.mismoPunto && !paso.mismaParada) {
                        texto.appendChild(h('small', 'paso-caminata',
                            'Las coordenadas ubican ambas paradas a menos de 20 m; confirma en el lugar cuál punto corresponde al bus.'));
                    } else if (paso && paso.ubicacionesConocidas && !paso.mismoPunto) {
                        texto.appendChild(h('small', 'paso-caminata',
                            'Camina hasta ' + paso.subir.nombre + ' (' +
                            formatearDistancia(paso.distanciaKm * 1000) +
                            ' en línea recta; distancia estimada, no ruta peatonal).'));
                        if (paso.caminataLarga) {
                            texto.appendChild(h('span', 'chip chip--transbordo-larga',
                                'Caminata larga: revisa el mapa antes de continuar.'));
                        }
                    } else if (paso && !paso.ubicacionesConocidas) {
                        texto.appendChild(h('span', 'chip chip--transbordo-incompleto',
                            'No se puede confirmar la caminata: faltan coordenadas verificadas de las paradas.'));
                    }
                }
                boton.appendChild(punto);
                boton.appendChild(texto);
                boton.addEventListener('click', function () { enfocarPunto(it); });
                li.appendChild(boton);
                if (estado.favoritosHabilitados && it.tipo === 'parada' && it.paradaId) {
                    li.appendChild(crearBotonFavorito('PARADA', it.paradaId, it.nombre));
                }
                lista.appendChild(li);
            });
            panel.appendChild(lista);
            pintarParadasSinCoordenadas(panel, items);
        }

        function pintarParadasSinCoordenadas(panel, items) {
            if (!estado.favoritosHabilitados || !estado.activo || !estado.activo.tramos) return;
            var visibles = new Set(items.map(function (item) { return String(item.paradaId || ''); }));
            var faltantes = [];
            var ids = new Set();
            estado.activo.tramos.forEach(function (tramo) {
                (tramo.paradas || []).forEach(function (parada) {
                    if (!parada.id || visibles.has(String(parada.id)) || ids.has(String(parada.id))) return;
                    ids.add(String(parada.id));
                    faltantes.push(parada);
                });
            });
            if (!faltantes.length) return;

            var contenedor = h('section', 'paradas-sin-mapa');
            contenedor.appendChild(h('h3', null, 'Paradas sin ubicación en el mapa'));
            contenedor.appendChild(h('p', 'nota',
                'Estas paradas están registradas para la ruta, pero no tienen coordenadas suficientes para mostrarse en el mapa.'));
            var lista = h('ul', 'paradas-sin-mapa__lista');
            faltantes.forEach(function (parada) {
                var fila = h('li', 'paradas-sin-mapa__item');
                var datos = h('span', 'paradas-sin-mapa__datos');
                datos.appendChild(h('strong', null, parada.nombre));
                var detalle = [parada.barrio, parada.referencia, parada.ubicacion].filter(Boolean);
                if (detalle.length) datos.appendChild(h('small', null, detalle.join(' · ')));
                fila.appendChild(datos);
                fila.appendChild(crearBotonFavorito('PARADA', parada.id, parada.nombre));
                lista.appendChild(fila);
            });
            contenedor.appendChild(lista);
            panel.appendChild(contenedor);
        }

        function enfocarPunto(it) {
            if (global.innerWidth <= 700) minimizarPanel(true);
            mapa.flyTo([it.lat, it.lng], Math.max(mapa.getZoom(), 16), { duration: 0.6 });
            if (it.marcador) setTimeout(function () { it.marcador.openPopup(); }, 650);
        }

        function pintarBuses(itinerario) {
            var panel = el.panelBuses;
            vaciar(panel);
            var rutas = itinerario.tramos.map(function (tramo) { return tramo.ruta; });
            var buses = [];
            rutas.forEach(function (ruta) {
                ruta.buses.forEach(function (bus) { buses.push({ ruta: ruta, bus: bus }); });
            });
            if (!buses.length) {
                panel.appendChild(estadoVacio('fas fa-bus-simple', 'Sin buses asignados',
                    'Las rutas de este viaje todavía no tienen buses registrados.'));
            } else {
                var lista = h('ul', 'buses');
                buses.forEach(function (asignacion) {
                    var bus = asignacion.bus;
                    var li = h('li', 'bus');
                    var color = COLORES_BUS[normalizar(bus.color)];
                    var avatar = h('span', 'bus__icono');
                    avatar.appendChild(icono('fas fa-bus'));
                    if (color) avatar.style.setProperty('--bus-color', color);
                    li.appendChild(avatar);
                    var cuerpo = h('div', 'bus__cuerpo');
                    cuerpo.appendChild(h('strong', 'bus__placa', String(bus.placa || 'Sin placa').toUpperCase()));
                    var detalle = [bus.modelo, bus.color].filter(Boolean).join(' · ');
                    if (detalle) cuerpo.appendChild(h('small', null, detalle));
                    if (privado && bus.conductor) {
                        var c = h('small', 'bus__conductor');
                        c.appendChild(icono('fas fa-user'));
                        c.appendChild(document.createTextNode(' ' + bus.conductor));
                        cuerpo.appendChild(c);
                    }
                    li.appendChild(cuerpo);
                    cuerpo.insertBefore(h('small', null, nombreRutaEnSentido(asignacion.ruta)), cuerpo.firstChild);
                    lista.appendChild(li);
                });
                panel.appendChild(lista);
            }
            panel.appendChild(h('p', 'nota', 'Se muestran los buses asignados a cada ruta del viaje. Bustraker no rastrea su posición en tiempo real.'));
        }

        function pintarInfo(tramo) {
            var panel = el.panelInfo;
            vaciar(panel);
            panel.appendChild(h('h5', 'subtitulo', 'Rutas y transbordos'));
            var pasos = h('ol', 'itinerario-pasos');
            tramo.tramos.forEach(function (parte, indice) {
                var li = h('li');
                var texto = h('span', 'parada__texto');
                if (indice > 0) {
                    var transbordo = tramo.puntosTransbordo[indice - 1];
                    texto.appendChild(h('span', 'chip chip--transbordo', 'Transbordo en ' + mayuscula(transbordo.barrio)));
                    texto.appendChild(h('strong', null, transbordo.detalle));
                    if (transbordo.caminataLarga) {
                        texto.appendChild(h('span', 'chip chip--transbordo-larga',
                            'La distancia supera 500 m; verifica el recorrido antes de iniciar.'));
                    }
                }
                texto.appendChild(h('strong', null, 'Toma ' + nombreRutaEnSentido(parte.ruta)));
                texto.appendChild(h('small', null, 'Tramo ' + (indice + 1) + ' de ' + tramo.tramos.length +
                    ': ' + mayuscula(parte.barrios[0]) + ' → ' +
                    mayuscula(parte.barrios[parte.barrios.length - 1]) + '.'));
                    var paradaInicial = parte.paradas.find(function (parada) {
                        return parada.indiceBarrio === parte.indiceInicio;
                });
                var paradaFinal = parte.paradas.slice().reverse().find(function (parada) {
                        return parada.indiceBarrio === parte.indiceFin;
                });
                texto.appendChild(h('small', null, paradaInicial
                    ? 'Aborda en ' + paradaInicial.nombre + ' (' + mayuscula(parte.barrios[0]) + ').'
                    : 'No hay parada de abordaje registrada en ' + mayuscula(parte.barrios[0]) + '.'));
                texto.appendChild(h('small', null, paradaFinal
                    ? 'Baja en ' + paradaFinal.nombre + ' (' +
                        mayuscula(parte.barrios[parte.barrios.length - 1]) + ').'
                    : 'No hay parada de bajada registrada en ' +
                        mayuscula(parte.barrios[parte.barrios.length - 1]) + '.'));
                if (parte.paradas.length) {
                    texto.appendChild(h('small', null, 'Paradas registradas, en orden:'));
                    var listaParadas = h('ol', 'itinerario-paradas');
                    parte.paradas.forEach(function (parada, indiceParada) {
                        var descripcion = parada.barrio
                            ? parada.nombre + ' (' + mayuscula(parada.barrio) + ')'
                            : parada.nombre;
                        if (parada.ubicacion && parada.ubicacion !== parada.nombre) {
                            descripcion += ' · ' + parada.ubicacion;
                        }
                        if (parada.referencia && parada.referencia !== parada.nombre) {
                            descripcion += ' · ' + parada.referencia;
                        }
                        if (!Number.isFinite(parada.lat) || !Number.isFinite(parada.lng)) {
                            descripcion += ' · ubicación exacta no disponible';
                        }
                        if (parada === paradaInicial) descripcion = 'Abordaje: ' + descripcion;
                        else if (parada === paradaFinal) descripcion = 'Bajada: ' + descripcion;
                        listaParadas.appendChild(h('li', null, descripcion));
                    });
                    texto.appendChild(listaParadas);
                } else {
                    texto.appendChild(h('small', 'paso-caminata',
                        'No hay paradas registradas para detallar este tramo; confirma el recorrido antes de viajar.'));
                }
                li.appendChild(texto);
                pasos.appendChild(li);
            });
            panel.appendChild(pasos);
            panel.appendChild(h('h5', 'subtitulo', 'Barrios del viaje'));
            var chips = h('div', 'chips');
            tramo.barrios.forEach(function (b) { chips.appendChild(h('span', 'chip', mayuscula(b))); });
            panel.appendChild(chips);
            panel.appendChild(h('p', 'nota', 'La línea de cada ruta y su tiempo son estimaciones en auto, no el recorrido ni el tiempo real de la buseta. Las conexiones a pie solo se estiman entre paradas registradas con coordenadas: la distancia mostrada es en línea recta y no representa una ruta peatonal. Verifica el trayecto antes de iniciar.'));
            var enlace = h('a', 'enlace', 'Consultar mapas oficiales de TransCaribe');
            enlace.href = 'https://transcaribe.gov.co/index.php/rutas-sitm/';
            enlace.target = '_blank';
            enlace.rel = 'noopener noreferrer';
            panel.appendChild(enlace);
        }

        /* ===================== TRAYECTO Y BARRIOS CERCANOS ===================== */
        function barriosEnTrayecto(geometria, radioKm) {
            var encontrados = new Map();
            estado.barrios.forEach(function (b) {
                if (!Number.isFinite(b.lat) || !Number.isFinite(b.lng)) return;
                var p = proyectarEnTrayecto(b.lat, b.lng, geometria);
                if (p && p.distanciaAlTrayectoKm <= radioKm && !encontrados.has(b.nombreN)) {
                    encontrados.set(b.nombreN, { nombre: b.nombre, desde: p.distanciaDesdeInicioKm });
                }
            });
            return Array.from(encontrados.values()).sort(function (a, b) { return a.desde - b.desde; });
        }

        function establecerTrayecto(geometria) {
            estado.trayecto = geometria;
            estado.barriosTrayecto = geometria ? barriosEnTrayecto(geometria, 0.7) : [];
            if (el.barriosTrayecto) {
                el.barriosTrayecto.textContent = estado.barriosTrayecto.length
                    ? 'Barrios cercanos al trayecto, en orden aproximado: ' + estado.barriosTrayecto.map(function (b) { return b.nombre; }).join(' → ')
                    : (geometria ? 'No se identificaron barrios cercanos a este trazado.' : '');
                el.barriosTrayecto.hidden = !el.barriosTrayecto.textContent;
            }
            actualizarEstadoUbicacion();
            actualizarPanelSeguimiento();
        }

        /* ===================== TRANSCARIBE ===================== */
        async function cargarTranscaribe() {
            var select = el.trazadoTranscaribe;
            var params = new URLSearchParams({
                where: '1=1', outFields: 'name,cod_ruta,ruta,tipo,h_lu_vi,h_sa,h_do_fe,f_act',
                returnGeometry: 'true', outSR: '4326', f: 'geojson'
            });
            try {
                var datos = await M.fetchJSON(URL_TRANSCARIBE + '?' + params, 25000);
                estado.transcaribe = datos.features || [];
                select.replaceChildren(new Option('Selecciona un recorrido', ''));
                estado.transcaribe.forEach(function (f, i) {
                    var p = f.properties || {};
                    select.add(new Option((p.cod_ruta || '') + ' · ' + (p.name || p.ruta || 'Ruta'), i));
                });
                select.disabled = estado.transcaribe.length === 0;
            } catch (error) {
                console.error('No se pudieron cargar los trazados de TransCaribe:', error);
                select.replaceChildren(new Option('Trazados no disponibles', ''));
                select.disabled = true;
            }
        }

        function popupTranscaribe(p) {
            var popup = h('div', 'mu-popup');
            popup.appendChild(h('span', 'mu-popup__titulo', 'TransCaribe'));
            popup.appendChild(h('strong', null, (p.cod_ruta || '') + ' · ' + (p.ruta || p.name || 'TransCaribe')));
            if (p.tipo) popup.appendChild(h('span', null, p.tipo));
            [['Lun-Vie', p.h_lu_vi], ['Sábado', p.h_sa], ['Domingos y festivos', p.h_do_fe]].forEach(function (par) {
                if (par[1]) popup.appendChild(h('span', null, par[0] + ': ' + par[1]));
            });
            popup.appendChild(h('span', 'mu-popup__nota', 'Trazado de referencia ' + (p.f_act ? String(p.f_act).slice(0, 4) : '2024')));
            return popup;
        }

        function mostrarTranscaribe() {
            if (capas.transcaribe) { mapa.removeLayer(capas.transcaribe); capas.transcaribe = null; }
            var valor = el.trazadoTranscaribe.value;
            var feature = valor === '' ? null : estado.transcaribe[Number(valor)];
            if (!feature) {
                if (!capas.linea) {
                    if (capas.flujo) { mapa.removeLayer(capas.flujo); capas.flujo = null; }
                    establecerTrayecto(null);
                }
                return;
            }
            estado.activo = null;
            ocultarDetalle();
            limpiarRuta();
            pintarResultados();
            capas.transcaribe = L.geoJSON(feature, {
                style: { color: '#1cb9bf', weight: 6, opacity: 0.95, lineCap: 'round', lineJoin: 'round' },
                onEachFeature: function (f, capa) { capa.bindPopup(popupTranscaribe(f.properties || {})); }
            }).addTo(mapa);
            dibujarFlujo(feature.geometry, '#d6ffff');
            var b = capas.transcaribe.getBounds();
            if (capas.usuario) b.extend(capas.usuario.getLatLng());
            if (b.isValid()) M.ajustarVista(mapa, b);
            establecerTrayecto(feature.geometry);
        }

        function limpiarTranscaribe() {
            if (capas.transcaribe) { mapa.removeLayer(capas.transcaribe); capas.transcaribe = null; }
            if (el.trazadoTranscaribe) el.trazadoTranscaribe.value = '';
        }

        /* ===================== UBICACIÓN EN VIVO ===================== */
        function barrioMasCercano(lat, lng, soloServidos) {
            var mejor = null, minimo = Infinity;
            estado.barrios.forEach(function (b) {
                if (!Number.isFinite(b.lat) || !Number.isFinite(b.lng)) return;
                if (soloServidos && estado.servidos.size && !estado.servidos.has(b.nombreN)) return;
                var d = haversineKm(lat, lng, b.lat, b.lng);
                if (d < minimo) { minimo = d; mejor = { barrio: b, km: d }; }
            });
            return mejor;
        }

        function actualizarEstadoUbicacion() {
            var u = estado.ubicacion;
            if (!el.locationStatus || !u) return;
            var partes = ['Ubicación en vivo' + (Number.isFinite(u.precision) ? ' ±' + Math.round(u.precision) + ' m' : '') + '.'];
            var cerca = barrioMasCercano(u.lat, u.lng, false);
            if (cerca) partes.push('Barrio de referencia: ' + cerca.barrio.nombre + '.');

            var paradas = estado.items.filter(function (p) { return p.tipo === 'parada'; });
            if (paradas.length) {
                var mejor = null;
                paradas.forEach(function (p) {
                    var m = haversineKm(u.lat, u.lng, p.lat, p.lng) * 1000;
                    if (!mejor || m < mejor.m) mejor = { p: p, m: m };
                });
                partes.push('Parada más cercana: ' + mejor.p.nombre + ', a ' + formatearDistancia(mejor.m) +
                    ' (~' + Math.max(1, Math.round(mejor.m / METROS_POR_MINUTO_A_PIE)) + ' min a pie).');
            }
            var proy = proyectarEnTrayecto(u.lat, u.lng, estado.trayecto);
            if (proy && estado.barriosTrayecto.length && proy.distanciaAlTrayectoKm <= 1.2) {
                var siguiente = estado.barriosTrayecto.find(function (b) { return b.desde >= proy.distanciaDesdeInicioKm - 0.15; });
                if (siguiente) partes.push('Siguiente barrio aproximado: ' + siguiente.nombre + '.');
            } else if (estado.trayecto && proy) {
                partes.push('Estás a ' + proy.distanciaAlTrayectoKm.toFixed(1) + ' km del trazado seleccionado.');
            }
            partes.push(estado.reportandoLlegadas
                ? 'El envío temporal de GPS está activado para estimar llegadas en la ruta elegida.'
                : 'Tu GPS no se comparte con el servicio de llegadas; solo se envía si activas “Estoy a bordo · compartir GPS”.');
            el.locationStatus.textContent = partes.join(' ');
        }

        function alRecibirUbicacion(posicion) {
            var lat = posicion.coords.latitude, lng = posicion.coords.longitude;
            var primera = estado.ubicacion === null;
            estado.ubicacion = {
                lat: lat, lng: lng, precision: posicion.coords.accuracy,
                timestamp: posicion.timestamp || Date.now()
            };
            estado.gpsError = '';

            if (!capas.usuario) {
                capas.usuario = L.marker([lat, lng], {
                    icon: L.divIcon({ className: 'user-location-icon', html: '<span></span>', iconSize: [28, 28], iconAnchor: [14, 14] }),
                    keyboard: false
                }).bindPopup('Tu ubicación actual (GPS)').addTo(mapa);
            } else {
                capas.usuario.setLatLng([lat, lng]);
            }
            if (Number.isFinite(posicion.coords.accuracy)) {
                if (!capas.precision) {
                    capas.precision = L.circle([lat, lng], { radius: posicion.coords.accuracy, color: '#1877f2', weight: 1,
                        fillColor: '#1877f2', fillOpacity: 0.08, interactive: false }).addTo(mapa);
                } else {
                    capas.precision.setLatLng([lat, lng]);
                    capas.precision.setRadius(posicion.coords.accuracy);
                }
            }

            if (primera) {
                if (!estado.origen) sugerirOrigen(lat, lng);
                mapa.setView([lat, lng], Math.max(mapa.getZoom(), 15));
            }
            actualizarEstadoUbicacion();
            enviarUbicacionCompartida();
            evaluarCoincidenciaDeRuta();
            evaluarLlegada();
            actualizarPanelSeguimiento();
        }

        /** Propone el barrio con servicio más cercano, o el barrio registrado más cercano si no hay rutas. */
        function sugerirOrigen(lat, lng) {
            var general = barrioMasCercano(lat, lng, false);
            var servido = barrioMasCercano(lat, lng, true);
            var sugerido = servido || general;
            if (!sugerido) return;
            el.origen.value = mayuscula(nombreVisible(sugerido.barrio.nombreN));
            alCambiarOrigen();
            if (servido && general && general.barrio.nombreN !== servido.barrio.nombreN) {
                M.mostrarAviso('No hay rutas verificadas en ' + general.barrio.nombre + '. Se eligió ' + servido.barrio.nombre +
                    ', el barrio con servicio más cercano (a ' + servido.km.toFixed(1) + ' km).', 'warn', 8000);
            }
        }

        function fijarBotonSeguimiento(activo) {
            el.btnSeguirUbicacion.replaceChildren(
                icono(activo ? 'fas fa-location-crosshairs' : 'fas fa-location-arrow'),
                document.createTextNode(activo ? ' Pausar GPS' : ' Activar GPS'));
            el.btnSeguirUbicacion.setAttribute('aria-pressed', String(activo));
        }

        function alternarSeguimiento() {
            if (estado.seguimiento !== null) {
                navigator.geolocation.clearWatch(estado.seguimiento);
                estado.seguimiento = null;
                fijarBotonSeguimiento(false);
                el.locationStatus.textContent = 'Seguimiento pausado. Se conserva la última posición en el mapa.';
                estado.gpsError = 'GPS pausado. Activa el seguimiento para continuar.';
                actualizarPanelSeguimiento();
                return;
            }
            iniciarSeguimiento();
        }

        function iniciarSeguimiento() {
            if (!navigator.geolocation) {
                el.locationStatus.textContent = 'Este navegador no permite acceder a la ubicación.';
                estado.gpsError = 'Este navegador no permite acceder a la ubicación.';
                expandirPanelSeguimiento(true);
                actualizarPanelSeguimiento();
                return;
            }
            estado.gpsError = '';
            el.locationStatus.textContent = 'Esperando permiso y señal GPS…';
            fijarBotonSeguimiento(true);
            estado.seguimiento = navigator.geolocation.watchPosition(alRecibirUbicacion, function (error) {
                var mensajes = {
                    1: 'Permiso de ubicación denegado. Puedes habilitarlo en el navegador.',
                    2: 'No se pudo determinar la ubicación. Intenta de nuevo al aire libre.',
                    3: 'La ubicación tardó demasiado. Intenta de nuevo.'
                };
                el.locationStatus.textContent = mensajes[error.code] || 'No se pudo obtener la ubicación.';
                estado.gpsError = el.locationStatus.textContent;
                if (estado.seguimiento !== null) navigator.geolocation.clearWatch(estado.seguimiento);
                estado.seguimiento = null;
                fijarBotonSeguimiento(false);
                expandirPanelSeguimiento(true);
                actualizarPanelSeguimiento();
            }, { enableHighAccuracy: true, maximumAge: 5000, timeout: 20000 });
            actualizarPanelSeguimiento();
        }

        function evaluarCoincidenciaDeRuta() {
            var movimiento = estado.movimientoRuta, ubicacion = estado.ubicacion;
            if (!estado.activo || !estado.trayecto || !ubicacion
                || (Number.isFinite(ubicacion.precision) && ubicacion.precision > 65)) {
                movimiento.ultimaMuestra = null;
                movimiento.avances = 0;
                movimiento.detectado = false;
                return;
            }
            var proyeccion = proyectarEnTrayecto(ubicacion.lat, ubicacion.lng, estado.trayecto);
            if (!proyeccion || proyeccion.distanciaAlTrayectoKm > 0.12) {
                movimiento.ultimaMuestra = null;
                movimiento.avances = 0;
                if (proyeccion && proyeccion.distanciaAlTrayectoKm > 0.25) movimiento.detectado = false;
                return;
            }

            var actual = { distancia: proyeccion.distanciaDesdeInicioKm, tiempo: ubicacion.timestamp };
            var anterior = movimiento.ultimaMuestra;
            if (anterior) {
                var segundos = (actual.tiempo - anterior.tiempo) / 1000;
                if (segundos >= 1 && segundos <= 25) {
                    var avanceKm = actual.distancia - anterior.distancia;
                    var velocidadKmh = avanceKm * 3600 / segundos;
                    if (avanceKm >= 0.012 && velocidadKmh >= 7 && velocidadKmh <= 60) {
                        movimiento.avances++;
                    } else if (avanceKm < -0.02 || velocidadKmh > 60 || velocidadKmh < 0) {
                        movimiento.avances = 0;
                    }
                    movimiento.ultimaMuestra = actual;
                } else if (segundos > 25 || segundos <= 0) {
                    movimiento.avances = 0;
                    movimiento.ultimaMuestra = actual;
                }
            } else {
                movimiento.ultimaMuestra = actual;
            }
            if (!movimiento.detectado && movimiento.avances >= 2) {
                movimiento.detectado = true;
                expandirPanelSeguimiento(true);
            }
        }

        function agregarPanelSeguimiento() {
            var ControlSeguimiento = L.Control.extend({
                options: { position: 'topleft' },
                onAdd: function () {
                    var contenedor = L.DomUtil.create('div', 'estado-viaje-control');
                    var boton = L.DomUtil.create('button', 'estado-viaje__boton', contenedor);
                    boton.type = 'button';
                    boton.setAttribute('aria-label', 'Abrir estado del viaje');
                    boton.setAttribute('aria-expanded', 'false');
                    boton.setAttribute('aria-controls', 'panelEstadoViaje');
                    boton.appendChild(icono('fas fa-satellite-dish'));
                    boton.appendChild(h('span', 'estado-viaje__indicador'));

                    var panel = h('section', 'estado-viaje');
                    panel.id = 'panelEstadoViaje';
                    panel.setAttribute('aria-label', 'Estado del viaje en tiempo real');
                    panel.hidden = true;
                    var cabecera = h('header', 'estado-viaje__cabecera');
                    var insignia = h('span', 'estado-viaje__insignia');
                    insignia.appendChild(icono('fas fa-location-crosshairs'));
                    cabecera.appendChild(insignia);
                    var titulos = h('div', 'estado-viaje__titulos');
                    titulos.appendChild(h('span', 'estado-viaje__eyebrow', 'BUSTRAKER · GPS EN VIVO'));
                    titulos.appendChild(h('h2', null, 'Estado del viaje'));
                    cabecera.appendChild(titulos);
                    var cerrar = h('button', 'estado-viaje__cerrar');
                    cerrar.type = 'button';
                    cerrar.setAttribute('aria-label', 'Cerrar estado del viaje');
                    cerrar.appendChild(icono('fas fa-xmark'));
                    cabecera.appendChild(cerrar);
                    panel.appendChild(cabecera);
                    var estadoVisual = h('div', 'estado-viaje__estado');
                    estadoVisual.setAttribute('role', 'status');
                    estadoVisual.setAttribute('aria-live', 'polite');
                    panel.appendChild(estadoVisual);
                    var ruta = h('p', 'estado-viaje__ruta');
                    panel.appendChild(ruta);
                    var tituloBarrios = h('h3', 'estado-viaje__subtitulo', 'Barrios muy cerca');
                    panel.appendChild(tituloBarrios);
                    var descripcion = h('p', 'estado-viaje__nota',
                        'Radio de 300 m desde el GPS. Distancias a puntos de referencia del barrio; no indican sus límites.');
                    panel.appendChild(descripcion);
                    var lista = h('ul', 'estado-viaje__barrios');
                    panel.appendChild(lista);
                    var precision = h('p', 'estado-viaje__pie');
                    precision.appendChild(icono('fas fa-circle-info'));
                    precision.appendChild(document.createTextNode(
                        'La coincidencia con la ruta es aproximada y no confirma el abordaje ni la ubicación del bus.'));
                    panel.appendChild(precision);
                    var reintentar = h('button', 'estado-viaje__reintentar', 'Reintentar GPS');
                    reintentar.type = 'button';
                    reintentar.hidden = true;
                    reintentar.addEventListener('click', iniciarSeguimiento);
                    panel.appendChild(reintentar);
                    contenedor.appendChild(panel);

                    boton.addEventListener('click', function () {
                        expandirPanelSeguimiento(panel.hidden);
                    });
                    cerrar.addEventListener('click', function () { expandirPanelSeguimiento(false); });
                    L.DomEvent.disableClickPropagation(contenedor);
                    L.DomEvent.disableScrollPropagation(contenedor);
                    panelEstado.boton = boton;
                    panelEstado.panel = panel;
                    panelEstado.estado = estadoVisual;
                    panelEstado.ruta = ruta;
                    panelEstado.lista = lista;
                    panelEstado.reintentar = reintentar;
                    panelEstado.firma = '';
                    panelEstado.barriosVisibles = '';
                    return contenedor;
                }
            });
            mapa.addControl(new ControlSeguimiento());
        }

        function expandirPanelSeguimiento(abierto) {
            if (!panelEstado.panel) return;
            panelEstado.panel.hidden = !abierto;
            panelEstado.boton.setAttribute('aria-expanded', String(abierto));
            panelEstado.boton.setAttribute('aria-label', abierto
                ? 'Cerrar estado del viaje' : 'Abrir estado del viaje');
        }

        function actualizarPanelSeguimiento() {
            if (!panelEstado.panel) return;
            if (el.btnFinalizarTrayecto) {
                el.btnFinalizarTrayecto.hidden = !privado || !estado.activo || estado.resumenConfirmado;
            }
            var ubicacion = estado.ubicacion;
            var precisionBaja = ubicacion && Number.isFinite(ubicacion.precision) && ubicacion.precision > 100;
            var barriosCercanos = [];
            if (ubicacion && estado.seguimiento !== null && !estado.gpsError
                && estado.activo && !precisionBaja) {
                var rutaNombres = new Set(estado.activo.barriosN);
                barriosCercanos = estado.barrios.filter(function (barrio) {
                    return rutaNombres.has(barrio.nombreN)
                        && Number.isFinite(barrio.lat) && Number.isFinite(barrio.lng);
                }).map(function (barrio) {
                    return {
                        barrio: barrio,
                        distancia: haversineKm(ubicacion.lat, ubicacion.lng, barrio.lat, barrio.lng) * 1000
                    };
                }).filter(function (item) { return item.distancia <= 300; })
                    .sort(function (a, b) { return a.distancia - b.distancia; })
                    .slice(0, 4);
            }

            var mensaje, clase = '';
            if (estado.gpsError) {
                mensaje = estado.gpsError;
                clase = ' estado-viaje__estado--aviso';
            } else if (!ubicacion) {
                mensaje = 'Solicitando acceso y señal GPS…';
            } else if (!estado.activo) {
                mensaje = 'Planifica y selecciona una ruta verificada para activar el seguimiento del recorrido.';
            } else if (precisionBaja) {
                mensaje = 'La señal GPS tiene poca precisión para mostrar barrios cercanos con fiabilidad.';
                clase = ' estado-viaje__estado--aviso';
            } else if (!estado.trayecto) {
                mensaje = 'Ruta seleccionada. Calculando su trazado de referencia…';
            } else if (estado.movimientoRuta.detectado) {
                mensaje = 'Posible desplazamiento coincidente con el recorrido';
                clase = ' estado-viaje__estado--movimiento';
            } else {
                var proyeccion = proyectarEnTrayecto(ubicacion.lat, ubicacion.lng, estado.trayecto);
                mensaje = proyeccion && proyeccion.distanciaAlTrayectoKm <= 0.12
                    ? 'GPS cerca del recorrido; comprobando movimiento…'
                    : 'Esperando una ubicación próxima al recorrido seleccionado…';
            }
            panelEstado.estado.className = 'estado-viaje__estado' + clase;
            panelEstado.estado.textContent = mensaje;
            panelEstado.reintentar.hidden = !estado.gpsError;
            panelEstado.ruta.textContent = estado.activo
                ? 'Recorrido: ' + estado.activo.tramos.map(function (tramo) {
                    return nombreRutaEnSentido(tramo.ruta);
                }).join(' → ')
                : 'Sin recorrido seleccionado';
            panelEstado.boton.classList.toggle('estado-viaje__boton--activo',
                !!(ubicacion && estado.seguimiento !== null && !estado.gpsError));
            panelEstado.boton.classList.toggle('estado-viaje__boton--movimiento',
                !!(estado.movimientoRuta && estado.movimientoRuta.detectado));

            var firmaBarrios = barriosCercanos.map(function (item) { return item.barrio.nombreN; }).join('|');
            if (firmaBarrios && firmaBarrios !== panelEstado.barriosVisibles) {
                expandirPanelSeguimiento(true);
            }
            panelEstado.barriosVisibles = firmaBarrios;
            var firma = mensaje + '|' + barriosCercanos.map(function (item) {
                return item.barrio.nombreN + ':' + Math.round(item.distancia / 10);
            }).join('|');
            if (firma === panelEstado.firma) return;
            panelEstado.firma = firma;
            vaciar(panelEstado.lista);
            if (!estado.activo) {
                panelEstado.lista.appendChild(h('li', 'estado-viaje__vacio',
                    'Los barrios se mostrarán cuando elijas el recorrido de tu viaje.'));
            } else if (precisionBaja) {
                panelEstado.lista.appendChild(h('li', 'estado-viaje__vacio',
                    'Mejora la señal GPS para identificar barrios próximos.'));
            } else if (!estado.seguimiento || estado.gpsError) {
                panelEstado.lista.appendChild(h('li', 'estado-viaje__vacio',
                    'Activa el GPS para actualizar los barrios cercanos en tiempo real.'));
            } else if (!barriosCercanos.length) {
                panelEstado.lista.appendChild(h('li', 'estado-viaje__vacio',
                    'No hay barrios del recorrido a menos de 300 m de tu ubicación.'));
            } else {
                barriosCercanos.forEach(function (item, indice) {
                    var fila = h('li', 'estado-viaje__barrio');
                    var marcador = h('span', 'estado-viaje__punto', String(indice + 1));
                    fila.appendChild(marcador);
                    var detalle = h('span', 'estado-viaje__detalle');
                    detalle.appendChild(h('strong', null, item.barrio.nombre));
                    detalle.appendChild(h('small', null, 'A ' + formatearDistancia(item.distancia) +
                        ' del punto de referencia'));
                    fila.appendChild(detalle);
                    if (item.barrio.coordenadaEstimada) {
                        fila.appendChild(h('span', 'estado-viaje__estimado', 'Aprox.'));
                    }
                    panelEstado.lista.appendChild(fila);
                });
            }
        }

        function crearDialogoTrayecto() {
            var dialogo = h('dialog', 'trip-dialog');
            dialogo.setAttribute('aria-labelledby', 'tripDialogTitle');
            dialogo.addEventListener('click', function (evento) {
                if (evento.target === dialogo) dialogo.close();
            });
            document.body.appendChild(dialogo);
            estado.dialogoTrayecto = dialogo;
        }

        function mostrarDialogoLlegada(detectadaPorGps) {
            if (!privado || !estado.activo || !estado.dialogoTrayecto || estado.resumenConfirmado) return;
            var dialogo = estado.dialogoTrayecto;
            vaciar(dialogo);

            var contenido = h('div', 'trip-dialog__contenido');
            var cabecera = h('div', 'trip-dialog__cabecera');
            var iconoLlegada = h('span', 'trip-dialog__icono');
            iconoLlegada.appendChild(icono(detectadaPorGps ? 'fas fa-location-check' : 'fas fa-flag-checkered'));
            cabecera.appendChild(iconoLlegada);
            var textos = h('div');
            textos.appendChild(h('span', 'trip-dialog__eyebrow', 'BUSTRAKER · TU TRAYECTO'));
            var tituloLlegada = h('h2', null,
                detectadaPorGps ? '¿Llegaste a tu destino?' : '¿Finalizaste el trayecto?');
            tituloLlegada.id = 'tripDialogTitle';
            textos.appendChild(tituloLlegada);
            cabecera.appendChild(textos);
            contenido.appendChild(cabecera);

            var destinoVisible = mayuscula(nombreVisible(estado.destino));
            contenido.appendChild(h('p', 'trip-dialog__intro', detectadaPorGps
                ? 'El GPS indica que estás cerca de ' + destinoVisible + '. La ubicación del barrio es aproximada; confirma para ver el resumen.'
                : 'Confirma que terminaste el recorrido para revisar sus detalles y decidir si quieres guardarlo.'));
            contenido.appendChild(h('p', 'trip-dialog__ruta',
                mayuscula(nombreVisible(estado.origen)) + ' → ' + destinoVisible));

            var acciones = h('div', 'trip-dialog__acciones');
            var confirmar = h('button', 'trip-dialog__accion trip-dialog__accion--principal',
                detectadaPorGps ? 'Sí, llegué. Ver resumen' : 'Sí, finalizar y ver resumen');
            confirmar.type = 'button';
            confirmar.addEventListener('click', function () {
                estado.resumenConfirmado = true;
                actualizarPanelSeguimiento();
                pintarResumenTrayecto();
            });
            var continuar = h('button', 'trip-dialog__accion trip-dialog__accion--secundaria',
                detectadaPorGps ? 'Aún no, continuar' : 'Volver al mapa');
            continuar.type = 'button';
            continuar.addEventListener('click', function () {
                if (detectadaPorGps) estado.llegadaAvisada = true;
                dialogo.close();
            });
            acciones.appendChild(confirmar);
            acciones.appendChild(continuar);
            contenido.appendChild(acciones);
            dialogo.appendChild(contenido);
            dialogo.showModal();
        }

        function pintarResumenTrayecto() {
            var dialogo = estado.dialogoTrayecto;
            var tramo = estado.activo;
            if (!dialogo || !tramo) return;
            vaciar(dialogo);

            var contenido = h('div', 'trip-dialog__contenido trip-dialog__contenido--resumen');
            var cabecera = h('div', 'trip-dialog__cabecera');
            var insignia = h('span', 'trip-dialog__icono trip-dialog__icono--exito');
            insignia.appendChild(icono('fas fa-circle-check'));
            cabecera.appendChild(insignia);
            var titulos = h('div');
            titulos.appendChild(h('span', 'trip-dialog__eyebrow', 'RESUMEN DEL RECORRIDO'));
            var tituloResumen = h('h2', null, '¡Trayecto finalizado!');
            tituloResumen.id = 'tripDialogTitle';
            titulos.appendChild(tituloResumen);
            cabecera.appendChild(titulos);
            contenido.appendChild(cabecera);
            contenido.appendChild(h('p', 'trip-dialog__ruta',
                mayuscula(nombreVisible(estado.origen)) + ' → ' + mayuscula(nombreVisible(estado.destino))));

            var lista = h('dl', 'trip-dialog__datos');
            agregarDatoResumen(lista, 'Rutas', tramo.tramos.map(function (parte) {
                return nombreRutaEnSentido(parte.ruta);
            }).join(' → '));
            agregarDatoResumen(lista, 'Transbordos', String(tramo.transbordos));
            agregarDatoResumen(lista, 'Paradas y puntos', String(estado.items.length));
            if (estado.resumenEstimado) {
                agregarDatoResumen(lista, 'Distancia estimada', estado.resumenEstimado.distanciaKm.toFixed(1) + ' km');
                agregarDatoResumen(lista, 'Tiempo estimado en auto',
                    '~' + estado.resumenEstimado.tiempoMin + ' min · referencia, no tiempo real del bus');
            } else {
                agregarDatoResumen(lista, 'Estimación', 'Distancia y tiempo no disponibles para este trazado.');
            }
            contenido.appendChild(lista);

            var barrios = h('div', 'trip-dialog__barrios');
            barrios.appendChild(h('strong', null, 'Barrios del itinerario'));
            var chips = h('div', 'trip-dialog__chips');
            tramo.barrios.forEach(function (barrio) {
                chips.appendChild(h('span', 'trip-dialog__chip', mayuscula(barrio)));
            });
            barrios.appendChild(chips);
            contenido.appendChild(barrios);
            contenido.appendChild(h('p', 'trip-dialog__pregunta',
                '¿Quieres guardar este resumen en tus trayectos favoritos?'));

            var feedback = h('p', 'trip-dialog__feedback');
            feedback.setAttribute('role', 'status');
            feedback.setAttribute('aria-live', 'polite');
            contenido.appendChild(feedback);
            var acciones = h('div', 'trip-dialog__acciones');
            var guardar = h('button', 'trip-dialog__accion trip-dialog__accion--principal', 'Guardar trayecto favorito');
            guardar.type = 'button';
            guardar.appendChild(icono('fas fa-star'));
            guardar.addEventListener('click', function () {
                guardarTrayectoFavorito(guardar, feedback);
            });
            var cerrar = h('button', 'trip-dialog__accion trip-dialog__accion--secundaria', 'Ahora no');
            cerrar.type = 'button';
            cerrar.addEventListener('click', function () { dialogo.close(); });
            acciones.appendChild(guardar);
            acciones.appendChild(cerrar);
            contenido.appendChild(acciones);
            var enlaceDashboard = h('a', 'trip-dialog__dashboard', 'Ver mis favoritos');
            enlaceDashboard.href = M.url('/dashboard#favoritos-section');
            enlaceDashboard.appendChild(icono('fas fa-arrow-up-right-from-square'));
            enlaceDashboard.hidden = true;
            contenido.appendChild(enlaceDashboard);
            dialogo.appendChild(contenido);
            dialogo.showModal();
        }

        function agregarDatoResumen(lista, etiqueta, valor) {
            lista.appendChild(h('dt', null, etiqueta));
            lista.appendChild(h('dd', null, valor));
        }

        async function guardarTrayectoFavorito(boton, feedback) {
            var tramo = estado.activo;
            if (!tramo || boton.disabled) return;
            boton.disabled = true;
            feedback.textContent = 'Guardando el resumen…';
            feedback.classList.remove('trip-dialog__feedback--error');
            try {
                var token = document.querySelector('meta[name="_csrf"]');
                var header = document.querySelector('meta[name="_csrf_header"]');
                if (!token || !header || !token.content || !header.content) {
                    throw new Error('No se pudo validar la sesión. Recarga la página e inténtalo de nuevo.');
                }
                var headers = { Accept: 'application/json', 'Content-Type': 'application/json' };
                headers[header.content] = token.content;
                var resumen = estado.resumenEstimado;
                var respuesta = await fetch(M.url('/api/favoritos/trayectos'), {
                    method: 'POST',
                    credentials: 'same-origin',
                    headers: headers,
                    body: JSON.stringify({
                        origen: mayuscula(nombreVisible(estado.origen)),
                        destino: mayuscula(nombreVisible(estado.destino)),
                        rutas: tramo.tramos.map(function (parte) { return nombreRutaEnSentido(parte.ruta); }),
                        barrios: tramo.barrios.map(mayuscula),
                        distanciaEstimadaKm: resumen ? resumen.distanciaKm : null,
                        tiempoEstimadoAutoMin: resumen ? resumen.tiempoMin : null,
                        paradasCount: estado.items.length
                    })
                });
                if (!respuesta.ok) {
                    var error = await respuesta.json().catch(function () { return {}; });
                    throw new Error(error.message || 'No se pudo guardar el trayecto.');
                }
                var guardado = await respuesta.json();
                if (!guardado || !guardado.id) throw new Error('El servidor no confirmó el trayecto guardado.');
                estado.trayectosFavoritos.unshift(guardado);
                actualizarBarriosFavoritos();
                poblarOrigen();
                poblarDestino();
                feedback.textContent = 'Trayecto guardado. Sus barrios se priorizarán en tus próximas búsquedas.';
                var enlace = estado.dialogoTrayecto.querySelector('.trip-dialog__dashboard');
                if (enlace) enlace.hidden = false;
                boton.replaceChildren(icono('fas fa-circle-check'), document.createTextNode(' Guardado en favoritos'));
                M.mostrarAviso('El resumen del trayecto ya está en tus favoritos.', 'ok', 6500);
            } catch (error) {
                console.error('Error guardando trayecto favorito:', error);
                feedback.textContent = error.message || 'No se pudo guardar el trayecto. Inténtalo nuevamente.';
                feedback.classList.add('trip-dialog__feedback--error');
                boton.disabled = false;
            }
        }

        /* ===================== URL, PANEL, LEYENDA ===================== */
        function firmaItinerario(itinerario) {
            return JSON.stringify(itinerario.tramos.map(function (tramo) {
                return [
                    String(tramo.ruta.id),
                    tramo.sentido,
                    tramo.indiceInicio,
                    tramo.indiceFin
                ];
            }));
        }

        function actualizarURL() {
            try {
                var p = new URLSearchParams();
                if (estado.origen) p.set('o', nombreVisible(estado.origen));
                if (estado.destino) p.set('d', nombreVisible(estado.destino));
                if (estado.activo) p.set('r', firmaItinerario(estado.activo));
                var texto = p.toString();
                global.history.replaceState(null, '', global.location.pathname + (texto ? '?' + texto : ''));
            } catch (e) { /* entornos sin History API */ }
        }

        function restaurarDesdeURL() {
            var p = new URLSearchParams(global.location.search);
            var o = p.get('o'), d = p.get('d'), r = p.get('r');
            if (!o) return;
            el.origen.value = o;
            alCambiarOrigen();
            if (!estado.origen || !d) return;
            el.destino.value = d;
            alCambiarDestino();
            if (estado.destino) planificar();
            if (estado.destino && r) {
                var tramo = estado.tramos.find(function (itinerario) {
                    if (firmaItinerario(itinerario) === r) return true;
                    var firmaAnterior = JSON.stringify(itinerario.tramos.map(function (parte) {
                        return [
                            String(parte.ruta.id),
                            parte.barriosN[0],
                            parte.barriosN[parte.barriosN.length - 1]
                        ];
                    }));
                    if (firmaAnterior === r) return true;
                    var ids = itinerario.tramos.map(function (parte) { return parte.ruta.id; }).join('-');
                    return ids === r || (itinerario.tramos.length === 1 && String(itinerario.tramos[0].ruta.id) === r);
                });
                if (tramo) seleccionarTramo(tramo);
            }
        }

        async function compartirEnlace() {
            try {
                await navigator.clipboard.writeText(global.location.href);
                M.mostrarAviso('Enlace copiado. Quien lo abra verá esta misma consulta.', 'ok');
            } catch (e) {
                global.prompt('Copia este enlace:', global.location.href);
            }
        }

        function minimizarPanel(forzar) {
            var panel = document.querySelector('.options-panel');
            var minimizado = typeof forzar === 'boolean' ? forzar : !panel.classList.contains('options-panel--min');
            panel.classList.toggle('options-panel--min', minimizado);
            el.btnPanel.setAttribute('aria-expanded', String(!minimizado));
            el.btnPanel.setAttribute('aria-label', minimizado ? 'Mostrar el panel' : 'Minimizar el panel');
            el.btnPanel.replaceChildren(icono(minimizado ? 'fas fa-chevron-up' : 'fas fa-chevron-down'));
        }

        function agregarLeyenda() {
            var Leyenda = L.Control.extend({
                options: { position: 'bottomleft' },
                onAdd: function () {
                    var c = L.DomUtil.create('div', 'mu-leyenda');
                    c.innerHTML =
                        '<span><i class="mu-leyenda__a">A</i> Origen</span>' +
                        '<span><i class="mu-leyenda__b">B</i> Destino</span>' +
                        '<span><i class="mu-leyenda__n">1</i> Parada</span>' +
                        '<span><i class="mu-leyenda__t">T</i> Transbordo</span>' +
                        '<span><i class="mu-leyenda__caminar"></i> Caminata estimada</span>' +
                        '<span><i class="mu-leyenda__u"></i> Tú</span>';
                    L.DomEvent.disableClickPropagation(c);
                    return c;
                }
            });
            new Leyenda().addTo(mapa);
        }
    }

    global.BustrakerTravel = { iniciar: iniciar };
})(window);
