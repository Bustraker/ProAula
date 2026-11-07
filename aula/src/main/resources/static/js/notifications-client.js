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
    var HEARTBEAT_MS = 25000;
    var notificacionesVistas = new Set();
    var temporizador = null;
    var audioHabilitado = true;

    // Inicializar preferencia de audio
    try {
        var guardado = global.localStorage.getItem(PREF_AUDIO);
        audioHabilitado = guardado !== 'false';
    } catch (e) {
        audioHabilitado = true;
    }

    /* ===================== UTILIDADES ===================== */
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

    /* ===================== SÍNTESIS SONORA Y VOZ ACCESIBLE ===================== */

    /**
     * Campanilla sintética de alta fidelidad con Web Audio API (D5 -> A5).
     * No requiere descargas ni archivos mp3 externos.
     */
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
     * Lectura por voz de la notificación para personas ciegas o con discapacidad visual.
     */
    function hablarMensaje(texto, prioridad) {
        if (!audioHabilitado || !global.speechSynthesis) return;
        try {
            if (prioridad) {
                global.speechSynthesis.cancel();
            }
            var utterance = new SpeechSynthesisUtterance(texto);
            utterance.lang = 'es-CO';
            utterance.rate = 1.0;
            utterance.pitch = 1.0;

            // Intentar asignar una voz en español
            var voces = global.speechSynthesis.getVoices();
            if (voces && voces.length > 0) {
                var vozEs = voces.find(function (v) {
                    return v.lang && (v.lang.toLowerCase().startsWith('es-co') || v.lang.toLowerCase().startsWith('es'));
                });
                if (vozEs) utterance.voice = vozEs;
            }

            global.speechSynthesis.speak(utterance);
        } catch (e) {
            console.debug('Síntesis de voz no disponible:', e);
        }
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
        } catch (e) {}

        actualizarBotonAudio();
        if (audioHabilitado) {
            emitirCampanilla();
            hablarMensaje('Audio y notificaciones por voz activadas.', true);
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
        // Enlazar botones del header si existen
        var btnCampana = document.getElementById('btnNotificacionesViaje');
        if (btnCampana) {
            btnCampana.addEventListener('click', alternarDrawer);
        }

        var btnAudio = document.getElementById('btnAudioAccesible');
        if (btnAudio) {
            btnAudio.addEventListener('click', toggleAudio);
            actualizarBotonAudio();
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
                hablarMensaje('Esta es una prueba del sistema de sonido y voz para personas ciegas o con discapacidad visual en Bustraker.', true);
            });
        }

        // Registrar eventos de ciclo de vida
        global.addEventListener('beforeunload', avisarDesconexion);
        document.addEventListener('visibilitychange', function () {
            if (document.visibilityState === 'visible') {
                consultarPendientes();
            }
        });

        // Cargar voces del navegador
        if (global.speechSynthesis) {
            global.speechSynthesis.onvoiceschanged = function () {
                // Precalentar voces
            };
        }

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
