/**
 * Bustraker · Sistema de Notificaciones en Tiempo Real y Accesibilidad Sonora
 *
 * Funcionalidades:
 * 1. Control de Presencia: Mantiene al usuario activo ("dentro de la aplicación")
 *    mediante heartbeat periódico a /api/notificaciones/pendientes.
 * 2. Entrega In-App: Muestra avisos emergentes inmediatos cuando el usuario está logueado y dentro.
 * 3. Audio & Voz Accesible:
 *    - Campanilla sonora sintetizada (Web Audio API) que no requiere archivos externos.
 *    - Síntesis de voz (Web Speech API) en español para personas ciegas o con discapacidad visual.
 *    - Región aria-live para compatibilidad absoluta con lectores de pantalla (JAWS, NVDA, TalkBack).
 * 4. Desconexión Limpia: Notifica al backend al cerrar o salir de la app (sendBeacon)
 *    para que las siguientes alertas se envíen directamente por correo.
 */
(function (global) {
    'use strict';

    var PREF_AUDIO = 'bustraker.audioNotificaciones';
    var PREF_VOICE = 'bustraker.preferenciasVoz';
    var HEARTBEAT_MS = 25000;
    var notificacionesVistas = new Set();
    var temporizador = null;
    // Audio DESACTIVADO por defecto; el usuario lo activa con el botón de volumen
    var audioHabilitado = false;
    // Valores predeterminados: voz masculina (pitch bajo), velocidad normal, volumen pleno
    var preferenciasVoz = { voiceURI: '', rate: 1.0, pitch: 0.85, volume: 1 };

    // Inicializar preferencia de audio (solo se activa si el usuario lo encendió antes explícitamente)
    try {
        var guardado = global.localStorage.getItem(PREF_AUDIO);
        // Si nunca guardó preferencia (null) → queda desactivado por defecto
        if (guardado !== null) {
            audioHabilitado = guardado === 'true';
        }
    } catch (e) {
        audioHabilitado = false;
    }

    try {
        var vozGuardada = global.localStorage.getItem(PREF_VOICE);
        var opcionesVoz = vozGuardada ? JSON.parse(vozGuardada) : null;
        if (opcionesVoz && typeof opcionesVoz === 'object') {
            preferenciasVoz.voiceURI = typeof opcionesVoz.voiceURI === 'string' ? opcionesVoz.voiceURI : '';
            preferenciasVoz.rate = limitarNumero(opcionesVoz.rate, 0.5, 2, 1.0);
            preferenciasVoz.pitch = limitarNumero(opcionesVoz.pitch, 0, 2, 0.85);
            preferenciasVoz.volume = limitarNumero(opcionesVoz.volume, 0, 1, 1);
        }
    } catch (e) {
        console.warn('No se pudieron cargar las preferencias de voz:', e);
    }

    /* ===================== UTILIDADES ===================== */
    function limitarNumero(valor, minimo, maximo, predeterminado) {
        var numero = Number(valor);
        return Number.isFinite(numero) ? Math.min(maximo, Math.max(minimo, numero)) : predeterminado;
    }

    function urlApp(ruta) {
        var meta = document.querySelector('meta[name="app-context-path"]');
        var ctx = meta ? meta.content.replace(/\/+$/, '') : '';
        return ctx + (ruta.charAt(0) === '/' ? ruta : '/' + ruta);
    }

    function csrfHeaders() {
        var token = document.querySelector('meta[name="_csrf"]');
        var header = document.querySelector('meta[name="_csrf_header"]');
        var headers = { 'Accept': 'application/json', 'Content-Type': 'application/json' };
        if (token && header && token.content && header.content) {
            headers[header.content] = token.content;
        }
        return headers;
    }

    function escapeHtml(str) {
        return String(str == null ? '' : str)
            .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;').replace(/'/g, '&#39;');
    }

    /* ══════════════════════════════════════════════════════════════════════════
     * MOTOR DE VOZ  —  BustrakerSpeechEngine
     *
     * Arquitectura de alto rendimiento para síntesis de voz fluida y profesional
     * con acento colombiano (es-CO), tono masculino y cadencia natural costeña.
     *
     * Capas del motor:
     *   1. Preprocesador NLP: normaliza abreviaciones, números, siglas y símbolos
     *      para que el motor TTS los pronuncie correctamente en español.
     *   2. Segmentador prosódico: divide el texto en cláusulas respiratorias
     *      respetando la entonación natural del español costeño.
     *   3. Cola de reproducción inteligente: encola segmentos con prioridades,
     *      evita solapamientos y gestiona cancelaciones limpias.
     *   4. Keep-alive anti-pausa (bug Chrome/Edge): refresca el engine cada 10 s
     *      mientras habla para evitar cortes invisibles.
     *   5. Selector de voz con puntuación: elige la mejor voz local disponible
     *      priorizando masculina, latinoamericana y de bajo latencia.
     *   6. Pre-calentamiento: dispara una utterance silenciosa al cargar la página
     *      para que el engine esté listo cuando se necesite (elimina el retardo
     *      del primer mensaje).
     * ══════════════════════════════════════════════════════════════════════════ */

    var SpeechEngine = (function () {

        /* ── Constantes ── */
        var MAX_SEGMENTO_CHARS = 160;   // Chrome trunka utterances > ~200 chars
        var KEEP_ALIVE_MS      = 10000; // Intervalo anti-pausa de Chrome
        var WAIT_VOICES_MS     = 100;   // Polling de voces aún no cargadas
        var MAX_WAIT_VOICES    = 25;    // Máximo 2.5 s esperando voces

        /* ── Estado interno ── */
        var _vozCacheada      = null;   // Voz seleccionada (se recalcula si cambian las voces)
        var _keepAliveTimer   = null;
        var _vocesListas      = false;
        var _colaEspera       = [];     // Mensajes encolados antes de que las voces carguen

        /* ════════════════════════════════════════════════
         * 1. PREPROCESADOR DE TEXTO
         * Convierte texto técnico en lenguaje natural fluido
         * que el motor TTS pronunciará correctamente.
         * ════════════════════════════════════════════════ */
        var _ABREVIACIONES = [
            // Unidades y medidas
            [/\b(\d+)\s*km\b/gi,       '$1 kilómetros'],
            [/\b(\d+)\s*m\b/g,         '$1 metros'],
            [/\b(\d+)\s*min\b/gi,      '$1 minutos'],
            [/\b(\d+)\s*seg\b/gi,      '$1 segundos'],
            [/\b(\d+)\s*h\b/gi,        '$1 horas'],
            [/\b(\d+)\s*hrs?\b/gi,     '$1 horas'],
            // Símbolos comunes
            [/&amp;/g,                 'y'],
            [/&/g,                     'y'],
            [/%/g,                     'por ciento'],
            [/N°|No\./gi,              'número'],
            [/#(\d+)/g,                'número $1'],
            // Siglas de TransCaribe y transporte
            [/\bSITM\b/g,              'Sistema Integrado de Transporte Masivo'],
            [/\bTransCaribe\b/g,       'Trans Caribe'],
            // Expresiones de viaje y barrios de Cartagena para pronunciación fluida
            [/\bAv\.\s*/gi,            'Avenida '],
            [/\bDiag\.\s*/gi,          'Diagonal '],
            [/\bCra\.\s*|\bCr\.\s*/gi, 'Carrera '],
            [/\bCl\.\s*|\bCll\.\s*/gi, 'Calle '],
            [/\bTr\.\s*|\bTv\.\s*/gi,  'Transversal '],
            // Puntuación que genera silencios artificiales
            [/\.{2,}/g,                ', '],
            [/\s*·\s*/g,               ', '],
            [/\s*[-–—]\s*/g,           ', '],
            [/([,;])\s*([,;])/g,       '$1'],
            // Números ordinales
            [/\b1er\b/gi,              'primero'],
            [/\b2do\b/gi,              'segundo'],
            [/\b3er\b/gi,              'tercero'],
            // Símbolos de ruta que pueden aparecer en nombres
            [/\bA\b(?=\s+→|\s+a\b)/g, 'origen'],
            [/\bB\b(?=\s+→)/g,        'destino'],
        ];

        function _preprocesar(texto) {
            var t = String(texto || '').trim();
            _ABREVIACIONES.forEach(function (par) {
                t = t.replace(par[0], par[1]);
            });
            // Colapsar espacios múltiples
            t = t.replace(/\s{2,}/g, ' ').trim();
            // Asegurar que termina con puntuación para entonación de cierre
            if (t && !/[.!?]$/.test(t)) t += '.';
            return t;
        }

        /* ════════════════════════════════════════════════
         * 2. SEGMENTADOR PROSÓDICO
         * Divide el texto en cláusulas naturales del tamaño
         * adecuado para el engine TTS, respetando la
         * entonación del español caribeño (pausas en comas,
         * descenso de tono en punto final).
         * ════════════════════════════════════════════════ */
        function _segmentar(texto) {
            if (texto.length <= MAX_SEGMENTO_CHARS) return [texto];

            // Intentar separar por oraciones completas primero
            var oraciones = [];
            // Patrón: punto/exclamación/interrogación seguido de espacio
            var re = /([^.!?]+[.!?]+)\s*/g;
            var m;
            while ((m = re.exec(texto)) !== null) {
                var o = m[1].trim();
                if (o) oraciones.push(o);
            }
            // Fallback si el regex no captura nada (texto sin puntuación)
            if (oraciones.length === 0) oraciones = [texto];

            // Si alguna oración es aún muy larga, dividir por coma
            var segmentos = [];
            oraciones.forEach(function (oracion) {
                if (oracion.length <= MAX_SEGMENTO_CHARS) {
                    segmentos.push(oracion);
                } else {
                    var clausulas = oracion.split(/,\s*/);
                    var acumulado = '';
                    clausulas.forEach(function (c) {
                        if ((acumulado + c).length > MAX_SEGMENTO_CHARS && acumulado) {
                            segmentos.push(acumulado.trim());
                            acumulado = c + ', ';
                        } else {
                            acumulado += c + ', ';
                        }
                    });
                    if (acumulado.trim()) segmentos.push(acumulado.replace(/,\s*$/, '.').trim());
                }
            });

            return segmentos.filter(function (s) { return s.length > 0; });
        }

        /* ════════════════════════════════════════════════
         * 3. SELECTOR DE VOZ CON PUNTUACIÓN
         * Evalúa cada voz disponible según múltiples criterios
         * y elige la que mejor combina:
         *   - Locale es-CO (máxima prioridad para acento costeño)
         *   - Latam (es-MX, es-US, es-AR…) como segundo nivel
         *   - Servicio local (voces del SO > voces de red online)
         *   - Nombre masculino (tono más grave, más cálido)
         * ════════════════════════════════════════════════ */
        var _NOMBRES_MASCULINOS = /\b(male|hombre|diego|jorge|carlos|miguel|pablo|antonio|david|juan|andres|man|masc|hector|google\s+español|salome|gonzalo|luis)\b/i;
        var _LOCALE_PRIORIDAD = {
            'es-co': 110, 'es-419': 90, 'es-ve': 85, 'es-do': 82, 'es-pr': 80,
            'es-pa': 78, 'es-ec': 75, 'es-mx': 70, 'es-us': 68, 'es-ar': 62,
            'es-cl': 60, 'es-pe': 58, 'es-cr': 55, 'es-gt': 52, 'es-hn': 50,
            'es-ni': 48, 'es-sv': 46, 'es-bo': 44, 'es-py': 42, 'es-uy': 40,
            'es-es': 20  // Castellano peninsular (muy baja prioridad)
        };

        function _puntuarVoz(voz) {
            var lang  = (voz.lang || '').toLowerCase().replace(/_/g, '-');
            var name  = (voz.name || '').toLowerCase();
            var score = 0;

            // Puntos por locale
            var localePts = 0;
            Object.keys(_LOCALE_PRIORIDAD).forEach(function (codigo) {
                if (lang === codigo || lang.indexOf(codigo) === 0) {
                    localePts = Math.max(localePts, _LOCALE_PRIORIDAD[codigo]);
                }
            });
            // Si no es español en absoluto, descartamos
            if (localePts === 0 && lang.indexOf('es') !== 0) return -1;
            score += localePts;

            // Bonus destacado para voces de alta calidad natural (Natural, Neural, Online/Studio de Edge/Chrome)
            if (/\b(natural|neural|online \(natural\)|studio|journey|wavenet)\b/i.test(name)) {
                score += 55;
            } else if (voz.localService === true) {
                // Bonus por ser local del sistema si no es neural
                score += 35;
            }

            // Bonus por nombre masculino
            if (_NOMBRES_MASCULINOS.test(name)) score += 25;

            // Penalizar voces excesivamente robóticas o sintéticas antiguas
            if (/espeak|festival|microsoft\s+helena|microsoft\s+sabina/i.test(name)) score -= 40;

            return score;
        }

        function _elegirVoz(voces, voiceURIManual) {
            if (!voces || voces.length === 0) return null;

            // Respetar elección manual del usuario sobre todo lo demás
            if (voiceURIManual) {
                var manual = voces.find(function (v) { return v.voiceURI === voiceURIManual; });
                if (manual) return manual;
            }

            // Si ya tenemos una voz cacheada y sigue disponible, reutilizarla
            if (_vozCacheada) {
                var sigueDisponible = voces.some(function (v) { return v.voiceURI === _vozCacheada.voiceURI; });
                if (sigueDisponible) return _vozCacheada;
            }

            // Evaluar todas las voces y elegir la de mayor puntuación
            var mejor = null;
            var mejorPts = -1;
            voces.forEach(function (voz) {
                var pts = _puntuarVoz(voz);
                if (pts > mejorPts) {
                    mejorPts = pts;
                    mejor = voz;
                }
            });

            _vozCacheada = mejor;
            return mejor;
        }

        /* ════════════════════════════════════════════════
         * 4. KEEP-ALIVE (anti-bug Chrome/Edge)
         * Chrome suspende speechSynthesis al perder foco
         * o tras ~15 s. Este timer hace pause+resume cada
         * 10 s para mantenerla activa sin perder audio.
         * ════════════════════════════════════════════════ */
        function _startKeepAlive() {
            _stopKeepAlive();
            _keepAliveTimer = setInterval(function () {
                var synth = global.speechSynthesis;
                if (!synth) return _stopKeepAlive();
                if (synth.speaking) {
                    synth.pause();
                    synth.resume();
                } else {
                    _stopKeepAlive();
                }
            }, KEEP_ALIVE_MS);
        }

        function _stopKeepAlive() {
            if (_keepAliveTimer) {
                clearInterval(_keepAliveTimer);
                _keepAliveTimer = null;
            }
        }

        /* ════════════════════════════════════════════════
         * 5. PRE-CALENTAMIENTO DEL ENGINE
         * Envía una utterance vacía al arrancar para que
         * Chrome inicialice el engine de TTS en segundo
         * plano. Elimina el retardo perceptible del primer
         * mensaje real (típicamente 400-800 ms).
         * ════════════════════════════════════════════════ */
        function _precalentar() {
            if (!global.speechSynthesis || !global.SpeechSynthesisUtterance) return;
            try {
                var utt = new global.SpeechSynthesisUtterance(' ');
                utt.volume = 0;    // Silencioso
                utt.rate   = 2;    // Rapidísimo, termina en <100 ms
                utt.lang   = 'es-CO';
                global.speechSynthesis.speak(utt);
            } catch (e) { /* ignorar */ }
        }

        /* ════════════════════════════════════════════════
         * 6. INVALIDACIÓN DE CACHÉ AL CAMBIAR VOCES
         * Cuando el sistema carga nuevas voces (ej: voces
         * online de Chrome que llegan tarde), recalculamos
         * la mejor voz disponible.
         * ════════════════════════════════════════════════ */
        function _onVoicesChanged() {
            _vozCacheada = null;  // Forzar re-selección
            _vocesListas = true;
            // Despachar mensajes que esperaban voces
            if (_colaEspera.length > 0) {
                var pendientes = _colaEspera.slice();
                _colaEspera = [];
                pendientes.forEach(function (item) {
                    _despachar(item.texto, item.voz);
                });
            }
        }

        /* ════════════════════════════════════════════════
         * 7. CONSTRUCCIÓN Y DESPACHO DE UTTERANCES
         * Crea cada SpeechSynthesisUtterance con los
         * parámetros correctos y las encola en orden,
         * asignando callbacks para keep-alive y limpieza.
         * ════════════════════════════════════════════════ */
        function _construirUtterance(segmento, vozElegida, prefs, esUltimo, esFirst) {
            var utt    = new global.SpeechSynthesisUtterance(segmento);
            utt.lang   = vozElegida ? (vozElegida.lang || 'es-CO') : 'es-CO';
            utt.rate   = prefs.rate;
            utt.pitch  = prefs.pitch;
            utt.volume = prefs.volume;
            if (vozElegida) utt.voice = vozElegida;

            if (esFirst) {
                utt.onstart = function () { _startKeepAlive(); };
            }
            if (esUltimo) {
                utt.onend   = function () { _stopKeepAlive(); };
                utt.onerror = function (e) {
                    _stopKeepAlive();
                    if (e && e.error && e.error !== 'interrupted') {
                        console.debug('[Bustraker Voice] Error en utterance:', e.error);
                    }
                };
            }
            return utt;
        }

        function _despachar(texto, prefs) {
            var synth = global.speechSynthesis;
            if (!synth) return;

            var voces      = synth.getVoices();
            var vozElegida = _elegirVoz(voces, prefs.voiceURI);
            var segmentos  = _segmentar(_preprocesar(texto));
            var total      = segmentos.length;

            segmentos.forEach(function (seg, idx) {
                var utt = _construirUtterance(seg, vozElegida, prefs, idx === total - 1, idx === 0);
                synth.speak(utt);
            });
        }

        /* ════════════════════════════════════════════════
         * API PÚBLICA del motor
         * ════════════════════════════════════════════════ */
        function hablar(texto, prioridad, prefs) {
            var synth = global.speechSynthesis;
            if (!synth || !global.SpeechSynthesisUtterance) return;

            if (prioridad) {
                synth.cancel();
                _stopKeepAlive();
            }

            var voces = synth.getVoices();
            if (voces && voces.length > 0) {
                _vocesListas = true;
                _despachar(texto, prefs);
            } else {
                // Chrome: voces aún no cargadas → encolar y esperar
                _colaEspera.push({ texto: texto, voz: prefs });
                var intentos = 0;
                var poll = setInterval(function () {
                    intentos++;
                    var v = global.speechSynthesis.getVoices();
                    if ((v && v.length > 0) || intentos >= MAX_WAIT_VOICES) {
                        clearInterval(poll);
                        _onVoicesChanged();
                    }
                }, WAIT_VOICES_MS);
            }
        }

        function cancelar() {
            _stopKeepAlive();
            if (global.speechSynthesis) global.speechSynthesis.cancel();
        }

        function invalidarVoz() {
            _vozCacheada = null;
        }

        // Registrar listener de cambio de voces (Chrome las carga tarde)
        if (global.speechSynthesis) {
            global.speechSynthesis.onvoiceschanged = _onVoicesChanged;
            // Intentar precalentar tras primer gesto de usuario
            document.addEventListener('click', _precalentar, { once: true });
            document.addEventListener('touchstart', _precalentar, { once: true });
        }

        return {
            hablar:        hablar,
            cancelar:      cancelar,
            invalidarVoz:  invalidarVoz,
            elegirVoz:     _elegirVoz,
            puntuarVoz:    _puntuarVoz,
            getVoces:      function () {
                if (!global.speechSynthesis) return [];
                return global.speechSynthesis.getVoices();
            }
        };

    })(); // fin BustrakerSpeechEngine

    /* ════════════════════════════════════════════════════════════════════
     * CAMPANILLA SONORA  —  Web Audio API
     * Campana sintética profesional en dos tonos (D5 → A5).
     * No requiere archivos externos. Envolvente suave ADSR para evitar
     * el "click" metálico de inicio y el corte brusco al final.
     * ════════════════════════════════════════════════════════════════════ */
    function emitirCampanilla() {
        if (!audioHabilitado) return;
        try {
            var AudioContextClass = global.AudioContext || global.webkitAudioContext;
            if (!AudioContextClass) return;
            var ctx = new AudioContextClass();
            if (ctx.state === 'suspended') {
                ctx.resume();
            }

            var now = ctx.currentTime;

            // Tono 1 (D5 ~587.33 Hz)
            var osc1 = ctx.createOscillator();
            var gain1 = ctx.createGain();
            osc1.type = 'sine';
            osc1.frequency.setValueAtTime(587.33, now);
            gain1.gain.setValueAtTime(0.001, now);
            gain1.gain.exponentialRampToValueAtTime(0.25, now + 0.03);
            gain1.gain.exponentialRampToValueAtTime(0.001, now + 0.35);
            osc1.connect(gain1);
            gain1.connect(ctx.destination);
            osc1.start(now);
            osc1.stop(now + 0.35);

            // Tono 2 (A5 ~880 Hz) - Armónico cristalino
            var osc2 = ctx.createOscillator();
            var gain2 = ctx.createGain();
            osc2.type = 'sine';
            osc2.frequency.setValueAtTime(880, now + 0.09);
            gain2.gain.setValueAtTime(0.001, now + 0.09);
            gain2.gain.exponentialRampToValueAtTime(0.3, now + 0.12);
            gain2.gain.exponentialRampToValueAtTime(0.0001, now + 0.55);
            osc2.connect(gain2);
            gain2.connect(ctx.destination);
            osc2.start(now + 0.09);
            osc2.stop(now + 0.55);
        } catch (e) {
            console.debug('Aviso sonoro no disponible:', e);
        }
    }

    /**
     * Fachada pública: delega al SpeechEngine con las preferencias actuales del usuario.
     * @param {string}  texto     - Texto a leer en voz alta.
     * @param {boolean} prioridad - true = interrumpe cualquier lectura en curso.
     */
    function hablarMensaje(texto, prioridad) {
        if (!audioHabilitado) return;
        try {
            SpeechEngine.hablar(texto, !!prioridad, preferenciasVoz);
        } catch (e) {
            console.debug('[Bustraker Voice] Error al hablar:', e);
        }
    }

    /* ── Funciones de compatibilidad para el selector de voces del panel UI ── */

    function normalizarIdioma(voz) {
        return voz.lang ? voz.lang.toLowerCase().replace(/_/g, '-') : '';
    }

    function ordenarVocesEspanol(voces) {
        return voces
            .filter(function (v) { return normalizarIdioma(v).indexOf('es') === 0; })
            .sort(function (a, b) {
                return SpeechEngine.puntuarVoz(b) - SpeechEngine.puntuarVoz(a);
            });
    }

    function guardarPreferenciasVoz() {
        try {
            global.localStorage.setItem(PREF_VOICE, JSON.stringify(preferenciasVoz));
        } catch (e) {
            console.warn('No se pudieron guardar las preferencias de voz:', e);
        }
    }

    function actualizarVoces() {
        var selector = document.getElementById('voiceSelect');
        var estado = document.getElementById('voiceStatus');
        if (!selector || !global.speechSynthesis) return;

        var vocesEspanol = ordenarVocesEspanol(global.speechSynthesis.getVoices());
        selector.replaceChildren();

        var opcionAutomatica = document.createElement('option');
        opcionAutomatica.value = '';
        opcionAutomatica.textContent = '⭐ Automática — mejor voz masculina disponible (recomendado)';
        selector.appendChild(opcionAutomatica);

        // Mostrar puntuación junto al nombre para que el usuario elija con criterio
        vocesEspanol.forEach(function (voz) {
            var opcion = document.createElement('option');
            opcion.value = voz.voiceURI;
            var pts = SpeechEngine.puntuarVoz(voz);
            var etiqueta = voz.name + ' [' + voz.lang + ']';
            if (pts >= 140) etiqueta = '🏆 ' + etiqueta;
            else if (pts >= 100) etiqueta = '✅ ' + etiqueta;
            else if (voz.localService) etiqueta = '📦 ' + etiqueta;
            else etiqueta = '☁️ ' + etiqueta;
            opcion.textContent = etiqueta;
            selector.appendChild(opcion);
        });

        if (vocesEspanol.length > 0) {
            selector.value = vocesEspanol.some(function (voz) {
                return voz.voiceURI === preferenciasVoz.voiceURI;
            }) ? preferenciasVoz.voiceURI : '';
            if (!selector.value) preferenciasVoz.voiceURI = '';
        } else {
            selector.value = '';
        }

        selector.disabled = vocesEspanol.length === 0;

        // Detectar cuál voz elegiría el motor automáticamente
        var vozAuto = SpeechEngine.elegirVoz(SpeechEngine.getVoces(), '');
        if (estado) {
            if (!vocesEspanol.length) {
                estado.textContent = '⚠️ Sin voces en español instaladas. Ve a Configuración del SO → Idioma → Voz → Agregar "Español (Colombia)" para mejor calidad.';
            } else if (vozAuto) {
                estado.textContent = '🎙️ Voz activa: "' + vozAuto.name + '" (' + vozAuto.lang + ')' + (vozAuto.localService ? ' · local' : ' · online') + '. Puedes cambiarla arriba.';
            } else {
                estado.textContent = 'Selecciona una voz en español. Las marcadas con 🏆 o ✅ son las más fluidas y naturales.';
            }
        }
    }

    function actualizarControlesVoz() {
        var controles = [
            { id: 'voiceRate', clave: 'rate', salida: 'voiceRateValue', decimales: 1 },
            { id: 'voicePitch', clave: 'pitch', salida: 'voicePitchValue', decimales: 1 },
            { id: 'voiceVolume', clave: 'volume', salida: 'voiceVolumeValue', decimales: 0 }
        ];
        controles.forEach(function (control) {
            var entrada = document.getElementById(control.id);
            var salida = document.getElementById(control.salida);
            if (!entrada) return;
            // Usar valores guardados o predeterminados (pitch 0.85 para voz masculina)
            entrada.value = preferenciasVoz[control.clave];
            if (salida) {
                salida.value = Number(preferenciasVoz[control.clave]).toFixed(control.decimales);
                salida.textContent = salida.value;
            }
        });
    }

    function configurarVoz() {
        var selector = document.getElementById('voiceSelect');
        if (selector) {
            selector.addEventListener('change', function () {
                preferenciasVoz.voiceURI = selector.value;
                SpeechEngine.invalidarVoz(); // Forzar re-selección con nueva preferencia
                guardarPreferenciasVoz();
                // Actualizar etiqueta de voz activa
                actualizarVoces();
            });
        }

        [
            { id: 'voiceRate', clave: 'rate', salida: 'voiceRateValue', decimales: 1 },
            { id: 'voicePitch', clave: 'pitch', salida: 'voicePitchValue', decimales: 1 },
            { id: 'voiceVolume', clave: 'volume', salida: 'voiceVolumeValue', decimales: 0 }
        ].forEach(function (control) {
            var entrada = document.getElementById(control.id);
            if (!entrada) return;
            entrada.addEventListener('input', function () {
                preferenciasVoz[control.clave] = limitarNumero(entrada.value, Number(entrada.min), Number(entrada.max), 1);
                var salida = document.getElementById(control.salida);
                if (salida) {
                    salida.value = Number(preferenciasVoz[control.clave]).toFixed(control.decimales);
                    salida.textContent = salida.value;
                }
                guardarPreferenciasVoz();
            });
        });

        var botonPrueba = document.getElementById('btnProbarVoz');
        if (botonPrueba) {
            botonPrueba.addEventListener('click', function () {
                var estado = document.getElementById('voiceStatus');
                if (!global.speechSynthesis || !global.SpeechSynthesisUtterance) {
                    if (estado) estado.textContent = 'La síntesis de voz no está disponible en este navegador.';
                    return;
                }
                hablarMensaje('Buenas, aquí Bustraker. Listo para ayudarte con tu viaje por Cartagena. Te indico las rutas, las paradas y los transbordos que necesitas, sin complicaciones.', true);
                if (estado) estado.textContent = 'Reproduciendo la prueba con las preferencias actuales.';
            });
        }

        actualizarControlesVoz();
        if (!global.speechSynthesis || !global.SpeechSynthesisUtterance) {
            var estado = document.getElementById('voiceStatus');
            var boton = document.getElementById('btnProbarVoz');
            if (estado) estado.textContent = 'La síntesis de voz no está disponible en este navegador.';
            if (boton) boton.disabled = true;
            if (selector) selector.disabled = true;
            return;
        }

        actualizarVoces();
        global.speechSynthesis.onvoiceschanged = actualizarVoces;
        setTimeout(actualizarVoces, 250);
    }

    /**
     * Anuncio directo a lector de pantalla (aria-live="assertive").
     */
    function anunciarLectorPantalla(texto) {
        var region = document.getElementById('srAudioAlert');
        if (!region) {
            region = document.createElement('div');
            region.id = 'srAudioAlert';
            region.setAttribute('role', 'alert');
            region.setAttribute('aria-live', 'assertive');
            region.className = 'sr-only';
            document.body.appendChild(region);
        }
        region.textContent = '';
        setTimeout(function () {
            region.textContent = texto;
        }, 50);
    }

    /* ===================== TOASTS / AVISOS EN PANTALLA ===================== */
    function contenedorToasts() {
        var cont = document.getElementById('notifToastContainer');
        if (!cont) {
            cont = document.createElement('div');
            cont.id = 'notifToastContainer';
            cont.className = 'notif-toast-container';
            cont.setAttribute('role', 'region');
            cont.setAttribute('aria-label', 'Notificaciones de Bustraker');
            document.body.appendChild(cont);
        }
        return cont;
    }

    function mostrarToastNotificacion(notif) {
        var cont = contenedorToasts();
        var toast = document.createElement('div');
        toast.className = 'notif-toast';
        toast.setAttribute('role', 'alert');

        var tipoClase = (notif.tipo || '').toLowerCase().includes('incidente') ? 'notif-toast--alerta' : 'notif-toast--info';
        toast.classList.add(tipoClase);

        toast.innerHTML =
            '<div class="notif-toast__icon"><i class="fas fa-bell"></i></div>' +
            '<div class="notif-toast__body">' +
            '  <strong class="notif-toast__title">' + escapeHtml(notif.titulo) + '</strong>' +
            '  <p class="notif-toast__msg">' + escapeHtml(notif.mensaje) + '</p>' +
            '  <small class="notif-toast__time">Recibido ahora mismo · En línea</small>' +
            '</div>' +
            '<div class="notif-toast__actions">' +
            '  <button type="button" class="notif-toast__btn-read" title="Marcar como leída"><i class="fas fa-check"></i></button>' +
            '  <button type="button" class="notif-toast__btn-close" title="Cerrar"><i class="fas fa-times"></i></button>' +
            '</div>';

        cont.appendChild(toast);

        // Sonido y voz para personas ciegas
        emitirCampanilla();
        var locucion = 'Nueva notificación de Bustraker: ' + notif.titulo + '. ' + notif.mensaje;
        hablarMensaje(locucion, false);
        anunciarLectorPantalla(locucion);

        function cerrar() {
            toast.classList.add('notif-toast--fadeout');
            setTimeout(function () {
                if (toast.parentNode) toast.parentNode.removeChild(toast);
            }, 250);
        }

        var btnClose = toast.querySelector('.notif-toast__btn-close');
        if (btnClose) btnClose.addEventListener('click', cerrar);

        var btnRead = toast.querySelector('.notif-toast__btn-read');
        if (btnRead) {
            btnRead.addEventListener('click', function () {
                marcarLeida(notif.id);
                cerrar();
            });
        }

        // Auto-cierre tras 9 segundos
        setTimeout(function () {
            if (toast.parentNode) cerrar();
        }, 9000);
    }

    /* ===================== COMUNICACIÓN CON BACKEND ===================== */
    async function consultarPendientes() {
        try {
            var resp = await fetch(urlApp('/api/notificaciones/pendientes'), {
                headers: { 'Accept': 'application/json' },
                credentials: 'same-origin',
                cache: 'no-store'
            });
            if (resp.status === 401 || resp.status === 403) {
                // Usuario no autenticado; detener el polling
                detenerPolling();
                return;
            }
            if (!resp.ok) return;

            var datos = await resp.json();
            var noLeidas = datos.noLeidas || [];
            actualizarBadge(datos.totalNoLeidas || noLeidas.length);

            noLeidas.forEach(function (n) {
                if (!notificacionesVistas.has(n.id)) {
                    notificacionesVistas.add(n.id);
                    mostrarToastNotificacion(n);
                }
            });

            // Si el drawer está abierto, actualizar su lista
            actualizarListaDrawer();
        } catch (e) {
            console.debug('Heartbeat de notificaciones falló:', e);
        }
    }

    async function marcarLeida(id) {
        try {
            await fetch(urlApp('/api/notificaciones/' + encodeURIComponent(id) + '/leida'), {
                method: 'PATCH',
                headers: csrfHeaders(),
                credentials: 'same-origin'
            });
            consultarPendientes();
        } catch (e) {
            console.error('Error al marcar leída:', e);
        }
    }

    async function marcarTodasLeidas() {
        try {
            await fetch(urlApp('/api/notificaciones/marcar-todas-leidas'), {
                method: 'POST',
                headers: csrfHeaders(),
                credentials: 'same-origin'
            });
            consultarPendientes();
        } catch (e) {
            console.error('Error al marcar todas leídas:', e);
        }
    }

    function avisarDesconexion() {
        try {
            if (navigator.sendBeacon) {
                var metaToken = document.querySelector('meta[name="_csrf"]');
                var token = metaToken ? metaToken.content : '';
                var url = urlApp('/api/notificaciones/desconectar') + (token ? '?_csrf=' + encodeURIComponent(token) : '');
                navigator.sendBeacon(url);
            }
        } catch (e) {
            // Ignorar
        }
    }

    /* ===================== CONTROL DE DRAWER / PANEL ===================== */
    function actualizarBadge(cantidad) {
        var badges = document.querySelectorAll('.notif-badge, #badgeNotifViaje, #avisosFavoritosNoLeidos');
        badges.forEach(function (b) {
            if (cantidad > 0) {
                b.textContent = cantidad > 99 ? '99+' : String(cantidad);
                b.hidden = false;
                b.style.display = 'inline-flex';
            } else {
                b.hidden = true;
                b.style.display = 'none';
            }
        });
    }

    async function actualizarListaDrawer() {
        var contenedor = document.getElementById('notifDrawerList');
        if (!contenedor) return;

        try {
            var resp = await fetch(urlApp('/api/notificaciones'), {
                headers: { 'Accept': 'application/json' },
                credentials: 'same-origin',
                cache: 'no-store'
            });
            if (!resp.ok) return;
            var lista = await resp.json();

            contenedor.innerHTML = '';
            if (!lista || lista.length === 0) {
                contenedor.innerHTML =
                    '<div class="notif-drawer__empty">' +
                    '  <i class="fas fa-bell-slash"></i>' +
                    '  <strong>Sin notificaciones recientes</strong>' +
                    '  <p>Recibirás avisos sonoros y por voz cuando surjan alertas en tus rutas.</p>' +
                    '</div>';
                return;
            }

            lista.forEach(function (n) {
                var item = document.createElement('div');
                item.className = 'notif-drawer__item' + (n.leido ? ' notif-drawer__item--leido' : '');
                item.innerHTML =
                    '<div class="notif-drawer__item-head">' +
                    '  <span class="notif-drawer__item-type">' + escapeHtml(n.tipo || 'Aviso') + '</span>' +
                    '  <span class="notif-drawer__item-channel">' + (n.canalEnvio === 'EMAIL' ? '<i class="fas fa-envelope"></i> Enviado a correo' : '<i class="fas fa-desktop"></i> En vivo') + '</span>' +
                    '</div>' +
                    '<strong class="notif-drawer__item-title">' + escapeHtml(n.titulo) + '</strong>' +
                    '<p class="notif-drawer__item-msg">' + escapeHtml(n.mensaje) + '</p>' +
                    '<div class="notif-drawer__item-actions">' +
                    '  <button type="button" class="notif-btn-speak"><i class="fas fa-volume-high"></i> Escuchar</button>' +
                    (!n.leido ? '  <button type="button" class="notif-btn-mark"><i class="fas fa-check"></i> Marcar leída</button>' : '') +
                    '</div>';

                var btnSpeak = item.querySelector('.notif-btn-speak');
                if (btnSpeak) {
                    btnSpeak.addEventListener('click', function () {
                        emitirCampanilla();
                        hablarMensaje(n.titulo + '. ' + n.mensaje, true);
                    });
                }

                var btnMark = item.querySelector('.notif-btn-mark');
                if (btnMark) {
                    btnMark.addEventListener('click', function () {
                        marcarLeida(n.id);
                    });
                }

                contenedor.appendChild(item);
            });
        } catch (e) {
            console.debug('Error listando notificaciones en drawer:', e);
        }
    }

    function alternarDrawer() {
        var drawer = document.getElementById('panelNotificacionesDrawer');
        if (!drawer) return;
        var abierto = !drawer.hidden;
        drawer.hidden = abierto;
        if (!abierto) {
            actualizarListaDrawer();
            hablarMensaje('Panel de notificaciones abierto.', true);
        }
    }

    function toggleAudio() {
        audioHabilitado = !audioHabilitado;
        try {
            global.localStorage.setItem(PREF_AUDIO, String(audioHabilitado));
        } catch (e) {
            console.warn('No se pudo guardar la preferencia de audio:', e);
        }

        actualizarBotonAudio();
        if (audioHabilitado) {
            emitirCampanilla();
            hablarMensaje('Audio y notificaciones por voz activadas.', true);
        } else if (global.speechSynthesis) {
            global.speechSynthesis.cancel();
        }
    }

    function actualizarBotonAudio() {
        var btns = document.querySelectorAll('#btnAudioAccesible, .btn-audio-accesible');
        btns.forEach(function (btn) {
            var icon = btn.querySelector('i');
            if (icon) {
                icon.className = audioHabilitado ? 'fas fa-volume-high' : 'fas fa-volume-xmark';
            }
            btn.setAttribute('aria-pressed', String(audioHabilitado));
            btn.setAttribute('title', audioHabilitado ? 'Voz y sonido: ACTIVADOS' : 'Voz y sonido: SILENCIADOS');
        });
    }

    function iniciarPolling() {
        detenerPolling();
        // Primera consulta rápida
        consultarPendientes();
        temporizador = setInterval(consultarPendientes, HEARTBEAT_MS);
    }

    function detenerPolling() {
        if (temporizador) {
            clearInterval(temporizador);
            temporizador = null;
        }
    }

    /* ===================== INICIALIZACIÓN ===================== */
    function init() {
        var btnAudio = document.getElementById('btnAudioAccesible');
        if (btnAudio) {
            btnAudio.addEventListener('click', toggleAudio);
            actualizarBotonAudio();
        }

        configurarVoz();

        var drawer = document.getElementById('panelNotificacionesDrawer');
        if (!drawer) return;

        var btnCampana = document.getElementById('btnNotificacionesViaje');
        if (btnCampana) {
            btnCampana.addEventListener('click', alternarDrawer);
        }

        var btnCerrarDrawer = document.getElementById('btnCerrarNotifDrawer');
        if (btnCerrarDrawer) {
            btnCerrarDrawer.addEventListener('click', function () {
                var d = document.getElementById('panelNotificacionesDrawer');
                if (d) d.hidden = true;
            });
        }

        var btnMarcarTodas = document.getElementById('btnMarcarTodasLeidas');
        if (btnMarcarTodas) {
            btnMarcarTodas.addEventListener('click', marcarTodasLeidas);
        }

        var btnProbarAudio = document.getElementById('btnProbarAudioNotif');
        if (btnProbarAudio) {
            btnProbarAudio.addEventListener('click', function () {
                emitirCampanilla();
                hablarMensaje('Buenas, el sistema de audio de Bustraker está funcionando correctamente. Estamos listos para guiarte por las rutas de Cartagena.', true);
            });
        }

        // Registrar eventos de ciclo de vida
        global.addEventListener('beforeunload', avisarDesconexion);
        document.addEventListener('visibilitychange', function () {
            if (document.visibilityState === 'visible') {
                consultarPendientes();
            }
        });

        iniciarPolling();
    }

    // Inicializar cuando el DOM esté listo
    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', init);
    } else {
        init();
    }

    // Exportar API global para uso en mapa y accesibilidad
    global.BustrakerNotificaciones = {
        hablar: hablarMensaje,
        chime: emitirCampanilla,
        anunciarLector: anunciarLectorPantalla,
        marcarLeida: marcarLeida,
        marcarTodasLeidas: marcarTodasLeidas,
        toggleAudio: toggleAudio,
        consultar: consultarPendientes
    };

})(window);
