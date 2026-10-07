/*
 * Bustraker · Lógica compartida de "Viajar" (Leaflet)
 *
 * Lo usan viajar.js (usuario con sesión) y viajar_public.js (público). Las dos páginas
 * tienen exactamente la misma estructura; solo cambia la configuración:
 *     BustrakerTravel.iniciar({ privado: true | false });
 *
 * Qué resuelve:
 *   - Origen / destino con búsqueda por escritura. El origen solo ofrece barrios con servicio y el
 *     destino solo barrios alcanzables desde el origen en el sentido de la ruta (no se ofrecen
 *     combinaciones que luego dan "sin resultados").
 *   - Tramos disponibles ordenados (más corto primero) con hora aproximada, paradas y buses.
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
            .normalize('NFD').replace(/\p{Diacritic}/gu, '');
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
            trayecto: null, barriosTrayecto: [],
            ubicacion: null, seguimiento: null,
            transcaribe: []
        };
        var capas = {
            paradas: L.layerGroup(), pinOrigen: null, pinDestino: null,
            base: null, linea: null, flujo: null, transcaribe: null, usuario: null, precision: null
        };
        var el = {};

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

        var arrancado = false;
        document.addEventListener('DOMContentLoaded', arrancar);
        if (document.readyState !== 'loading') arrancar();

        function arrancar() {
            if (arrancado) return;
            arrancado = true;
            ['origen', 'destino', 'listaOrigen', 'listaDestino', 'btnInvertir', 'btnSeguirUbicacion', 'locationStatus',
                'resultados', 'resumenBusqueda', 'rutaDetalle', 'detalleTitulo', 'detalleTramo', 'rutaStats', 'detalleHora',
                'panelParadas', 'panelBuses', 'panelInfo', 'mostrarParadas', 'trazadoTranscaribe', 'barriosTrayecto',
                'btnPanel', 'btnCompartir', 'btnLimpiar', 'contadorParadas', 'contadorBuses'
            ].forEach(function (id) { el[id] = $(id); });

            el.origen.addEventListener('input', alCambiarOrigen);
            el.origen.addEventListener('change', alCambiarOrigen);
            el.destino.addEventListener('input', alCambiarDestino);
            el.destino.addEventListener('change', alCambiarDestino);
            el.btnInvertir.addEventListener('click', invertir);
            el.btnSeguirUbicacion.addEventListener('click', alternarSeguimiento);
            el.btnLimpiar.addEventListener('click', reiniciarConsulta);
            el.btnCompartir.addEventListener('click', compartirEnlace);
            el.btnPanel.addEventListener('click', function () { minimizarPanel(); });
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
                    M.fetchJSON('/api/barrios/todos', 20000),
                    M.fetchJSON('/api/rutas-con-coordenadas', 20000)
                ]);
                procesarBarrios(resultados[0] || []);
                procesarRutas(resultados[1] || []);
                poblarOrigen();
                restaurarDesdeURL();
                pintarResultados();
                el.btnSeguirUbicacion.disabled = false;
            } catch (error) {
                console.error('Error cargando datos de viaje:', error);
                M.mostrarAviso('No se pudieron cargar las rutas. Revisa tu conexión e intenta de nuevo.', 'error', 8000);
                pintarErrorCarga();
            }
        }

        function procesarBarrios(lista) {
            estado.barrios = [];
            estado.coordenadas = {};
            lista.forEach(function (b) {
                var lat = Number.parseFloat(b.latitud), lng = Number.parseFloat(b.longitud);
                var n = normalizar(b.nombre);
                if (!n) return;
                estado.barrios.push({ nombre: b.nombre, nombreN: n, lat: lat, lng: lng });
                if (Number.isFinite(lat) && Number.isFinite(lng)) estado.coordenadas[n] = { lat: lat, lng: lng };
            });
        }

        function procesarRutas(lista) {
            estado.servidos = new Map();
            estado.rutas = lista.filter(function (r) {
                return r.ordenBarriosConfirmado === true
                    && Array.isArray(r.barriosOrdenados)
                    && r.barriosOrdenados.length >= 2
                    && r.barriosOrdenados.every(function (barrio) {
                        return typeof barrio === 'string' && normalizar(barrio);
                    });
            }).map(function (r) {
                var barrios = r.barriosOrdenados.slice();
                var barriosN = barrios.map(normalizar);
                barrios.forEach(function (nombre, i) { if (!estado.servidos.has(barriosN[i])) estado.servidos.set(barriosN[i], nombre); });
                var paradas = (r.paradas || [])
                    .filter(function (p) { return p.latitud != null && p.longitud != null
                        && Number.isFinite(Number(p.latitud)) && Number.isFinite(Number(p.longitud)); })
                    .map(function (p) {
                        return {
                            nombre: p.nombre, barrio: p.barrio, barrioN: normalizar(p.barrio),
                            referencia: p.referencia, ubicacion: p.ubicacion,
                            orden: p.orden == null ? Number.MAX_SAFE_INTEGER : p.orden,
                            lat: Number(p.latitud), lng: Number(p.longitud)
                        };
                    })
                    .sort(function (a, b) { return a.orden - b.orden; });
                return {
                    id: r.id, nombre: r.nombre, hora: r.horaAproximada,
                    barrios: barrios, barriosN: barriosN, paradas: paradas,
                    buses: r.buses || [], coordenadas: r.coordenadasBarrios || {}
                };
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
            return nombres.slice().sort(function (a, b) { return a.localeCompare(b, 'es'); });
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

        /** Barrios alcanzables desde "origenN" siguiendo el sentido de alguna ruta verificada. */
        function destinosDesde(origenN) {
            var destinos = new Map();
            estado.rutas.forEach(function (ruta) {
                var i = ruta.barriosN.indexOf(origenN);
                if (i < 0) return;
                for (var k = i + 1; k < ruta.barriosN.length; k++) {
                    if (!destinos.has(ruta.barriosN[k])) destinos.set(ruta.barriosN[k], ruta.barrios[k]);
                }
            });
            return destinos;
        }

        function destinosPermitidos(origenN) {
            var destinos = destinosDesde(origenN);
            if (destinos.size) return destinos;

            destinos = mapaBarriosDisponibles();
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
            el.destino.placeholder = destinosDesde(estado.origen).size
                ? 'Escribe o elige el barrio de destino'
                : 'No hay rutas confirmadas; elige un barrio para consultar';
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
            limpiarRuta();
            estado.tramos = [];
            estado.activo = null;
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
                if (!estado.destino && estado.origen) {
                    el.destino.value = '';
                    pintarResultados();
                    M.mostrarAviso('No hay rutas verificadas en el sentido contrario. Las rutas tienen un solo sentido de recorrido.', 'warn', 6500);
                }
            }
        }

        function reiniciarConsulta() {
            el.origen.value = '';
            estado.origen = '';
            alCambiarOrigen();
            limpiarRuta();
            limpiarTranscaribe();
            mapa.flyTo(M.CENTRO, 13, { duration: 0.8 });
        }

        /* ===================== BÚSQUEDA DE TRAMOS ===================== */
        function paradasDelTramo(ruta, origenN, destinoN) {
            var ps = ruta.paradas, i = -1, j = -1, k;
            for (k = 0; k < ps.length; k++) { if (ps[k].barrioN === origenN) { i = k; break; } }
            for (k = ps.length - 1; k >= 0; k--) { if (ps[k].barrioN === destinoN) { j = k; break; } }
            if (i >= 0 && j > i) return ps.slice(i, j + 1);
            var desde = ruta.barriosN.indexOf(origenN);
            var hasta = ruta.barriosN.indexOf(destinoN, desde + 1);
            var tramoN = new Set(ruta.barriosN.slice(desde, hasta + 1));
            return ps.filter(function (p) { return p.barrioN && tramoN.has(p.barrioN); });
        }

        function buscarTramos(origenN, destinoN) {
            var tramos = [];
            estado.rutas.forEach(function (ruta) {
                var inicio = ruta.barriosN.indexOf(origenN);
                if (inicio < 0) return;
                var fin = ruta.barriosN.indexOf(destinoN, inicio + 1);
                if (fin < 0) return;
                tramos.push({
                    ruta: ruta,
                    barrios: ruta.barrios.slice(inicio, fin + 1),
                    barriosN: ruta.barriosN.slice(inicio, fin + 1),
                    paradas: paradasDelTramo(ruta, origenN, destinoN)
                });
            });
            tramos.sort(function (a, b) {
                return a.barrios.length - b.barrios.length || b.paradas.length - a.paradas.length
                    || a.ruta.nombre.localeCompare(b.ruta.nombre, 'es');
            });
            return tramos;
        }

        function buscar() {
            limpiarRuta();
            ocultarDetalle();
            estado.activo = null;
            estado.tramos = estado.origen && estado.destino ? buscarTramos(estado.origen, estado.destino) : [];
            pintarResultados();
            actualizarURL();
            if (estado.tramos.length === 1) seleccionarTramo(estado.tramos[0]);
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
                var cantidad = destinosDesde(estado.origen).size;
                el.resultados.appendChild(estadoVacio('fas fa-location-dot', 'Ahora elige el destino',
                    cantidad ? 'Desde ' + mayuscula(nombreVisible(estado.origen)) + ' hay ' + cantidad + ' barrios con servicio directo.'
                        : 'No hay rutas verificadas que salgan de este barrio.'));
                return;
            }
            if (!estado.tramos.length) {
                var vacio = estadoVacio('fas fa-circle-xmark', 'Sin rutas verificadas en este sentido',
                    'Las rutas tienen un sentido de recorrido. Prueba con otro destino o revisa el trayecto contrario.');
                if (buscarTramos(estado.destino, estado.origen).length) {
                    var inverso = h('button', 'btn btn--secundario', 'Ver el sentido contrario');
                    inverso.type = 'button';
                    inverso.addEventListener('click', invertir);
                    vacio.appendChild(inverso);
                }
                el.resultados.appendChild(vacio);
                return;
            }

            var total = estado.tramos.length;
            el.resumenBusqueda.textContent = total + (total === 1 ? ' ruta disponible' : ' rutas disponibles') +
                ' de ' + mayuscula(nombreVisible(estado.origen)) + ' a ' + mayuscula(nombreVisible(estado.destino));
            el.resultados.appendChild(h('p', 'resultados__resumen', el.resumenBusqueda.textContent));

            estado.tramos.forEach(function (tramo, indice) {
                el.resultados.appendChild(crearTarjetaTramo(tramo, indice));
            });
        }

        function crearTarjetaTramo(tramo, indice) {
            var ruta = tramo.ruta;
            var activa = estado.activo === tramo;
            var tarjeta = h('button', 'tramo' + (activa ? ' tramo--activo' : ''));
            tarjeta.type = 'button';
            tarjeta.setAttribute('aria-pressed', String(activa));
            tarjeta.addEventListener('click', function () { seleccionarTramo(tramo); });

            var cabecera = h('div', 'tramo__cabecera');
            cabecera.appendChild(h('span', 'tramo__nombre', ruta.nombre));
            if (indice === 0 && estado.tramos.length > 1) cabecera.appendChild(h('span', 'chip chip--ok', 'Más corta'));
            tarjeta.appendChild(cabecera);

            var intermedios = Math.max(0, tramo.barrios.length - 2);
            var cadena = h('div', 'tramo__cadena');
            cadena.appendChild(h('span', null, mayuscula(tramo.barrios[0])));
            cadena.appendChild(icono('fas fa-arrow-right'));
            if (intermedios) {
                cadena.appendChild(h('span', 'tramo__mas', intermedios + (intermedios === 1 ? ' barrio' : ' barrios')));
                cadena.appendChild(icono('fas fa-arrow-right'));
            }
            cadena.appendChild(h('span', null, mayuscula(tramo.barrios[tramo.barrios.length - 1])));
            tarjeta.appendChild(cadena);

            var meta = h('div', 'tramo__meta');
            var hora = formatearHora(ruta.hora);
            var datos = [
                ['far fa-clock', hora ? 'Hora aprox. ' + hora : 'Horario no disponible'],
                ['fas fa-location-dot', tramo.paradas.length ? tramo.paradas.length + ' paradas' : 'Sin paradas registradas'],
                ['fas fa-bus', ruta.buses.length ? ruta.buses.length + (ruta.buses.length === 1 ? ' bus' : ' buses') : 'Sin buses asignados']
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
        function construirItems(tramo) {
            var ruta = tramo.ruta, items = [];
            if (tramo.paradas.length >= 2) {
                tramo.paradas.forEach(function (p) {
                    items.push({ nombre: p.nombre, barrio: p.barrio, referencia: p.referencia, ubicacion: p.ubicacion,
                        lat: p.lat, lng: p.lng, tipo: 'parada' });
                });
            } else {
                tramo.barrios.forEach(function (barrio, i) {
                    var c = ruta.coordenadas[barrio];
                    var lat, lng;
                    if (Array.isArray(c) && c.length === 2) { lat = Number(c[0]); lng = Number(c[1]); }
                    else {
                        var respaldo = estado.coordenadas[tramo.barriosN[i]];
                        if (respaldo) { lat = respaldo.lat; lng = respaldo.lng; }
                    }
                    if (Number.isFinite(lat) && Number.isFinite(lng)) {
                        items.push({ nombre: mayuscula(barrio), barrio: barrio, lat: lat, lng: lng, tipo: 'barrio' });
                    }
                });
            }
            items.forEach(function (it, i) { it.numero = i + 1; });
            return items;
        }

        function seleccionarTramo(tramo) {
            limpiarTranscaribe();
            limpiarRuta();
            estado.activo = tramo;
            estado.pestana = 'paradas';
            pintarResultados();
            actualizarURL();
            dibujarTramo(tramo);
        }

        async function dibujarTramo(tramo) {
            var items = construirItems(tramo);
            estado.items = items;
            pintarDetalle(tramo);

            if (items.length < 2) {
                pintarStats({ sinCoordenadas: true });
                M.mostrarAviso('Esta ruta no tiene coordenadas suficientes para trazar el recorrido.', 'warn');
                return;
            }

            var ultimo = items.length - 1;
            items.forEach(function (it, i) {
                if (i === 0 || i === ultimo) {
                    var tipo = i === 0 ? 'origen' : 'destino';
                    var pin = M.crearPin(it.lat, it.lng, tipo, it.nombre).addTo(mapa);
                    it.marcador = pin;
                    if (i === 0) capas.pinOrigen = pin; else capas.pinDestino = pin;
                } else {
                    it.marcador = M.crearMarcadorParada(it.lat, it.lng, it.numero, it);
                    capas.paradas.addLayer(it.marcador);
                }
            });
            if (!el.mostrarParadas.checked) mapa.removeLayer(capas.paradas);

            var limites = L.latLngBounds(items.map(function (p) { return [p.lat, p.lng]; }));
            if (capas.usuario) limites.extend(capas.usuario.getLatLng());
            M.ajustarVista(mapa, limites);
            pintarStats({ cargando: true, paradas: items.length });

            var solicitud = ++estado.solicitud;
            M.mostrarCargando('Calculando ruta por calles…');
            try {
                var resultado = await M.obtenerRutaOSRM(muestrear(items, MAX_PUNTOS_OSRM));
                if (solicitud !== estado.solicitud) return;
                var c = M.crearCapasRuta(resultado.geometry);
                capas.base = c.base.addTo(mapa);
                capas.linea = c.linea.addTo(mapa);
                dibujarFlujo(resultado.geometry);
                establecerTrayecto(resultado.geometry);
                var b = capas.linea.getBounds();
                if (capas.usuario) b.extend(capas.usuario.getLatLng());
                M.ajustarVista(mapa, b);
                pintarStats({ distanciaKm: resultado.distanciaKm, tiempoMin: resultado.tiempoMin, paradas: items.length });
            } catch (err) {
                if (solicitud !== estado.solicitud) return;
                console.error('Error OSRM:', err);
                pintarStats({ error: true, paradas: items.length });
                M.mostrarAviso(err.name === 'AbortError'
                    ? 'El servicio de rutas tardó demasiado. Las paradas siguen disponibles en la lista.'
                    : 'No se pudo calcular el trazado por calles. Las paradas siguen disponibles en la lista.', 'error');
            } finally {
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

        function limpiarRuta() {
            estado.solicitud++;
            [capas.pinOrigen, capas.pinDestino, capas.base, capas.linea, capas.flujo].forEach(function (capa) {
                if (capa) mapa.removeLayer(capa);
            });
            capas.pinOrigen = capas.pinDestino = capas.base = capas.linea = capas.flujo = null;
            capas.paradas.clearLayers();
            estado.items = [];
            establecerTrayecto(null);
        }

        /* ===================== DETALLE ===================== */
        function ocultarDetalle() { el.rutaDetalle.hidden = true; }

        function pintarDetalle(tramo) {
            var ruta = tramo.ruta;
            el.rutaDetalle.hidden = false;
            el.detalleTitulo.textContent = ruta.nombre;
            el.detalleTramo.textContent = mayuscula(tramo.barrios[0]) + ' → ' + mayuscula(tramo.barrios[tramo.barrios.length - 1]);
            var hora = formatearHora(ruta.hora);
            el.detalleHora.textContent = hora ? 'Hora aproximada: ' + hora : 'Horario no disponible';
            el.contadorParadas.textContent = String(estado.items.length);
            el.contadorBuses.textContent = String(ruta.buses.length);
            pintarParadas();
            pintarBuses(ruta);
            pintarInfo(tramo);
            cambiarPestana(estado.pestana);
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
                boton.appendChild(punto);
                boton.appendChild(texto);
                boton.addEventListener('click', function () { enfocarPunto(it); });
                li.appendChild(boton);
                lista.appendChild(li);
            });
            panel.appendChild(lista);
        }

        function enfocarPunto(it) {
            if (global.innerWidth <= 700) minimizarPanel(true);
            mapa.flyTo([it.lat, it.lng], Math.max(mapa.getZoom(), 16), { duration: 0.6 });
            if (it.marcador) setTimeout(function () { it.marcador.openPopup(); }, 650);
        }

        function pintarBuses(ruta) {
            var panel = el.panelBuses;
            vaciar(panel);
            if (!ruta.buses.length) {
                panel.appendChild(estadoVacio('fas fa-bus-simple', 'Sin buses asignados',
                    'Esta ruta todavía no tiene buses registrados.'));
            } else {
                var lista = h('ul', 'buses');
                ruta.buses.forEach(function (bus) {
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
                    lista.appendChild(li);
                });
                panel.appendChild(lista);
            }
            panel.appendChild(h('p', 'nota', 'Se muestran los buses asignados a la ruta. Bustraker no rastrea la posición de los buses en tiempo real.'));
        }

        function pintarInfo(tramo) {
            var panel = el.panelInfo;
            vaciar(panel);
            panel.appendChild(h('h5', 'subtitulo', 'Barrios del tramo'));
            var chips = h('div', 'chips');
            tramo.barrios.forEach(function (b) { chips.appendChild(h('span', 'chip', mayuscula(b))); });
            panel.appendChild(chips);
            panel.appendChild(h('p', 'nota', 'El trazado sobre calles y su tiempo son estimaciones en auto, no el recorrido ni el tiempo real del bus. Solo aparecen rutas verificadas.'));
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
            el.locationStatus.textContent = partes.join(' ');
        }

        function alRecibirUbicacion(posicion) {
            var lat = posicion.coords.latitude, lng = posicion.coords.longitude;
            var primera = estado.ubicacion === null;
            estado.ubicacion = { lat: lat, lng: lng, precision: posicion.coords.accuracy };

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
                document.createTextNode(activo ? ' Dejar de seguir' : ' Seguir mi ubicación'));
            el.btnSeguirUbicacion.setAttribute('aria-pressed', String(activo));
        }

        function alternarSeguimiento() {
            if (estado.seguimiento !== null) {
                navigator.geolocation.clearWatch(estado.seguimiento);
                estado.seguimiento = null;
                fijarBotonSeguimiento(false);
                el.locationStatus.textContent = 'Seguimiento pausado. Se conserva la última posición en el mapa.';
                return;
            }
            if (!navigator.geolocation) {
                el.locationStatus.textContent = 'Este navegador no permite acceder a la ubicación.';
                return;
            }
            el.locationStatus.textContent = 'Esperando permiso y señal GPS…';
            fijarBotonSeguimiento(true);
            estado.seguimiento = navigator.geolocation.watchPosition(alRecibirUbicacion, function (error) {
                var mensajes = {
                    1: 'Permiso de ubicación denegado. Puedes habilitarlo en el navegador.',
                    2: 'No se pudo determinar la ubicación. Intenta de nuevo al aire libre.',
                    3: 'La ubicación tardó demasiado. Intenta de nuevo.'
                };
                el.locationStatus.textContent = mensajes[error.code] || 'No se pudo obtener la ubicación.';
                if (estado.seguimiento !== null) navigator.geolocation.clearWatch(estado.seguimiento);
                estado.seguimiento = null;
                fijarBotonSeguimiento(false);
            }, { enableHighAccuracy: true, maximumAge: 5000, timeout: 20000 });
        }

        /* ===================== URL, PANEL, LEYENDA ===================== */
        function actualizarURL() {
            try {
                var p = new URLSearchParams();
                if (estado.origen) p.set('o', nombreVisible(estado.origen));
                if (estado.destino) p.set('d', nombreVisible(estado.destino));
                if (estado.activo) p.set('r', estado.activo.ruta.id);
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
            if (estado.destino && r) {
                var tramo = estado.tramos.find(function (t) { return String(t.ruta.id) === r; });
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
