document.addEventListener('DOMContentLoaded', function () {
    const root = document.documentElement;
    const baseRootFontSize = Number.parseFloat(window.getComputedStyle(root).fontSize) || 16;
    const textScales = [80, 90, 100, 110, 125, 150, 175, 200];
    const globalPreferenceKey = 'bustraker.accessibility.global';
    const preferenceKeys = {
        global: globalPreferenceKey,
        user: 'bustraker.accessibility.user',
        admin: 'bustraker.accessibility.admin',
        public: 'bustraker.accessibility.public'
    };
    const defaults = {
        textScale: 100,
        highContrast: false,
        readableText: false,
        underlineLinks: false,
        reducedMotion: false,
        enhancedFocus: false,
        largeTargets: false,
        visualAlerts: false,
        readingGuide: false,
        speechRate: 1,
        voiceURI: ''
    };
    const toggleKeys = [
        'highContrast',
        'readableText',
        'underlineLinks',
        'reducedMotion',
        'enhancedFocus',
        'largeTargets',
        'visualAlerts',
        'readingGuide'
    ];
    let storageAvailable = true;

    function resolveScope() {
        const explicitScope = document.body.dataset.accessibilityScope;
        if (Object.prototype.hasOwnProperty.call(preferenceKeys, explicitScope)) {
            return explicitScope;
        }

        const path = window.location.pathname.replace(/\/+$/, '') || '/';
        const adminRoutes = [
            '/index_2', '/reportes', '/gestionar-usuarios', '/editar-usuario',
            '/eliminar-usuario', '/actualizar-usuario', '/rutas/admin',
            '/registro-buses', '/actualizarbuses', '/eliminarbuses',
            '/actualizar-bus', '/eliminar-buses', '/editar-ruta',
            '/eliminar-ruta', '/mensajes_contacto', '/contacto/mensajes',
            '/eliminar_mensaje', '/incidentes/panel'
        ];
        if (path.startsWith('/admin/') || adminRoutes.some(function (route) {
            return path === route || path.startsWith(route + '/');
        })) {
            return 'admin';
        }

        const userRoutes = [
            '/dashboard', '/index_3', '/perfil', '/viajar', '/historial',
            '/consultas', '/rutas', '/ruta', '/buses', '/usuario',
            '/incidentes', '/contacto_usuario', '/perfil-usuario'
        ];
        if (userRoutes.some(function (route) {
            return path === route || path.startsWith(route + '/');
        })) {
            return 'user';
        }
        return 'public';
    }

    const scope = resolveScope();
    const preferenceKey = preferenceKeys[scope];

    function readStoredPreferences(key) {
        try {
            const serialized = window.localStorage.getItem(key);
            if (!serialized) {
                return {};
            }
            const saved = JSON.parse(serialized);
            return saved && typeof saved === 'object' && !Array.isArray(saved) ? saved : {};
        } catch (error) {
            storageAvailable = false;
            console.warn('No se pudieron leer las preferencias de accesibilidad:', error);
            return {};
        }
    }

    function normalizePreferences(saved, baseline) {
        const normalized = { ...baseline };
        Object.keys(defaults).forEach(function (key) {
            if (Object.prototype.hasOwnProperty.call(saved, key)) {
                normalized[key] = saved[key];
            }
        });

        const scale = Number(normalized.textScale);
        normalized.textScale = textScales.includes(scale) ? scale : baseline.textScale;
        const rate = Number(normalized.speechRate);
        normalized.speechRate = Number.isFinite(rate)
            ? Math.min(1.4, Math.max(0.7, rate))
            : baseline.speechRate;
        toggleKeys.forEach(function (key) {
            normalized[key] = typeof normalized[key] === 'boolean'
                ? normalized[key]
                : baseline[key];
        });
        normalized.voiceURI = typeof normalized.voiceURI === 'string'
            ? normalized.voiceURI
            : baseline.voiceURI;
        return normalized;
    }

    function normalizeOverrides(saved) {
        const overrides = {};
        if (textScales.includes(Number(saved.textScale))) {
            overrides.textScale = Number(saved.textScale);
        }
        toggleKeys.forEach(function (key) {
            if (typeof saved[key] === 'boolean') {
                overrides[key] = saved[key];
            }
        });
        const rate = Number(saved.speechRate);
        if (Number.isFinite(rate)) {
            overrides.speechRate = Math.min(1.4, Math.max(0.7, rate));
        }
        if (typeof saved.voiceURI === 'string') {
            overrides.voiceURI = saved.voiceURI;
        }
        return overrides;
    }

    const globalPreferences = normalizePreferences(
        readStoredPreferences(globalPreferenceKey),
        defaults
    );
    const localOverrides = scope === 'global'
        ? {}
        : normalizeOverrides(readStoredPreferences(preferenceKey));
    const preferences = scope === 'global'
        ? { ...globalPreferences }
        : normalizePreferences(localOverrides, globalPreferences);
    const controls = document.querySelector('.accessibility-controls');
    const triggers = document.querySelectorAll('[data-accessibility-trigger]');
    const status = document.getElementById('accessibilityControlsStatus');
    const scopeCopy = {
        global: {
            eyebrow: 'CONFIGURACIÓN GENERAL',
            description: 'Los cambios hechos desde la portada se aplican como preferencias generales en todo Bustraker.',
            reset: 'Restablecer configuración general',
            opened: 'Estás editando las preferencias generales de Bustraker.',
            saved: 'Se guardó en las preferencias generales.'
        },
        user: {
            eyebrow: 'PANEL DE USUARIO',
            description: 'Estas preferencias se aplican al área de usuario. Las opciones generales de la portada se conservan.',
            reset: 'Restablecer preferencias de usuario',
            opened: 'Estás editando las preferencias locales del panel de usuario.',
            saved: 'Se guardó en las preferencias del panel de usuario.'
        },
        admin: {
            eyebrow: 'PANEL DE ADMINISTRACIÓN',
            description: 'Estas preferencias se aplican al área de administración. Las opciones generales de la portada se conservan.',
            reset: 'Restablecer preferencias de administración',
            opened: 'Estás editando las preferencias locales del panel de administración.',
            saved: 'Se guardó en las preferencias del panel de administración.'
        },
        public: {
            eyebrow: 'PREFERENCIAS LOCALES',
            description: 'Estas preferencias se aplican a las páginas públicas. La configuración general de la portada se conserva.',
            reset: 'Restablecer preferencias locales',
            opened: 'Estás editando preferencias locales de las páginas públicas.',
            saved: 'Se guardó en las preferencias locales.'
        }
    }[scope];

    if (controls) {
        controls.dataset.preferenceScope = scope;
        const scopeEyebrow = document.getElementById('accessibilityControlsScope');
        const description = document.getElementById('accessibilityControlsDescription');
        const resetAll = document.getElementById('accessibilityResetAll');
        if (scopeEyebrow) scopeEyebrow.textContent = scopeCopy.eyebrow;
        if (description) description.textContent = scopeCopy.description;
        if (resetAll) resetAll.textContent = scopeCopy.reset;
    }

    let activeTrigger = null;
    let stopSpeechReading = function () {};
    const readingGuide = document.createElement('div');
    readingGuide.className = 'accessibility-reading-guide';
    readingGuide.setAttribute('aria-hidden', 'true');
    document.body.appendChild(readingGuide);

    function announce(message) {
        if (status) {
            status.textContent = message;
        }
    }

    function savePreferences() {
        try {
            const toSave = scope === 'global' ? preferences : localOverrides;
            window.localStorage.setItem(preferenceKey, JSON.stringify(toSave));
            storageAvailable = true;
        } catch (error) {
            storageAvailable = false;
            console.warn('No se pudieron guardar las preferencias de accesibilidad:', error);
        }
    }

    function updatePreference(key, value) {
        preferences[key] = value;
        if (scope === 'global') {
            globalPreferences[key] = value;
        } else {
            localOverrides[key] = value;
        }
    }

    function updatePreferences(values) {
        Object.keys(values).forEach(function (key) {
            updatePreference(key, values[key]);
        });
    }

    function applyPreferences() {
        const scaleClass = 'accessibility-text-' + preferences.textScale;
        Array.from(root.classList).forEach(function (className) {
            if (/^accessibility-text-\d+$/.test(className)) {
                root.classList.remove(className);
            }
        });
        if (preferences.textScale !== 100) {
            root.classList.add(scaleClass);
        }
        if (preferences.textScale === 100) {
            root.style.removeProperty('font-size');
        } else {
            root.style.setProperty(
                'font-size',
                (baseRootFontSize * preferences.textScale / 100) + 'px',
                'important'
            );
        }
        toggleKeys.forEach(function (key) {
            root.classList.toggle('accessibility-' + key.replace(/[A-Z]/g, function (letter) {
                return '-' + letter.toLowerCase();
            }), preferences[key]);
        });

        if (controls) {
            controls.querySelectorAll('[data-accessibility-toggle]').forEach(function (button) {
                const key = button.getAttribute('data-accessibility-toggle');
                const enabled = preferences[key] === true;
                button.setAttribute('aria-pressed', String(enabled));
                button.classList.toggle('is-active', enabled);
            });
        }
    }

    function updateTextScale(value) {
        const index = Math.min(textScales.length - 1, Math.max(0, value));
        updatePreference('textScale', textScales[index]);
        applyPreferences();
        savePreferences();

        const slider = document.getElementById('accessibilityTextScale');
        const output = document.getElementById('accessibilityTextValue');
        if (slider) {
            slider.value = String(index);
            slider.setAttribute('aria-valuetext', preferences.textScale + ' por ciento');
        }
        if (output) {
            output.value = preferences.textScale + '%';
            output.textContent = output.value;
        }
        announce('Tamaño del texto: ' + preferences.textScale + ' por ciento. ' + scopeCopy.saved);
    }

    function syncTextScaleControl() {
        const index = textScales.indexOf(preferences.textScale);
        const slider = document.getElementById('accessibilityTextScale');
        const output = document.getElementById('accessibilityTextValue');
        if (slider) {
            slider.value = String(index);
            slider.setAttribute('aria-valuetext', preferences.textScale + ' por ciento');
        }
        if (output) {
            output.value = preferences.textScale + '%';
            output.textContent = output.value;
        }
    }

    function updateSpeechRateOutput() {
        const rateInput = document.getElementById('accessibilitySpeechRate');
        const rateOutput = document.getElementById('accessibilitySpeechRateValue');
        if (rateInput) {
            rateInput.value = String(preferences.speechRate);
        }
        if (rateOutput) {
            rateOutput.value = Number(preferences.speechRate).toFixed(1) + '×';
            rateOutput.textContent = rateOutput.value;
        }
    }

    function setToggle(key, enabled) {
        updatePreference(key, enabled);
        applyPreferences();
        savePreferences();
        const labels = {
            highContrast: 'Alto contraste',
            readableText: 'Lectura clara',
            underlineLinks: 'Identificar enlaces',
            reducedMotion: 'Reducir movimiento',
            enhancedFocus: 'Resaltar foco del teclado',
            largeTargets: 'Controles más amplios',
            visualAlerts: 'Avisos visuales reforzados',
            readingGuide: 'Guía de lectura'
        };
        announce(labels[key] + (enabled ? ' activado. ' : ' desactivado. ') + scopeCopy.saved);
    }

    function closeControls(returnFocus) {
        if (!controls || !controls.open) {
            return;
        }
        controls.close();
        triggers.forEach(function (trigger) {
            trigger.setAttribute('aria-expanded', 'false');
        });
        stopSpeechReading();
        if (returnFocus && returnFocus.isConnected) {
            returnFocus.focus();
        }
    }

    function bindSettingsControls() {
        if (!controls) {
            return;
        }

        const decrease = document.getElementById('accessibilityTextDecrease');
        const increase = document.getElementById('accessibilityTextIncrease');
        const resetText = document.getElementById('accessibilityTextReset');
        const slider = document.getElementById('accessibilityTextScale');
        const resetAll = document.getElementById('accessibilityResetAll');
        const readingPreset = document.getElementById('accessibilityReadingPreset');
        const closeButton = controls.querySelector('[data-accessibility-close]');

        if (decrease) {
            decrease.addEventListener('click', function () {
                updateTextScale(textScales.indexOf(preferences.textScale) - 1);
            });
        }
        if (increase) {
            increase.addEventListener('click', function () {
                updateTextScale(textScales.indexOf(preferences.textScale) + 1);
            });
        }
        if (resetText) {
            resetText.addEventListener('click', function () {
                updateTextScale(textScales.indexOf(100));
            });
        }
        if (slider) {
            slider.addEventListener('input', function () {
                updateTextScale(Number(slider.value));
            });
        }

        controls.querySelectorAll('[data-accessibility-toggle]').forEach(function (button) {
            button.addEventListener('click', function () {
                const key = button.getAttribute('data-accessibility-toggle');
                setToggle(key, !preferences[key]);
            });
        });

        if (readingPreset) {
            readingPreset.addEventListener('click', function () {
                updatePreferences({
                    textScale: 125,
                    readableText: true,
                    underlineLinks: true
                });
                applyPreferences();
                syncTextScaleControl();
                savePreferences();
                announce('Lectura cómoda activada: texto amplio, lectura clara y enlaces destacados. ' + scopeCopy.saved);
            });
        }

        if (resetAll) {
            resetAll.addEventListener('click', function () {
                if (scope === 'global') {
                    Object.assign(preferences, defaults);
                    Object.assign(globalPreferences, defaults);
                } else {
                    Object.keys(localOverrides).forEach(function (key) {
                        delete localOverrides[key];
                    });
                    Object.assign(preferences, globalPreferences);
                }
                applyPreferences();
                syncTextScaleControl();
                const voiceInput = document.getElementById('accessibilityVoice');
                if (voiceInput) {
                    voiceInput.value = preferences.voiceURI;
                }
                updateSpeechRateOutput();
                savePreferences();
                stopSpeechReading();
                announce(scope === 'global'
                    ? 'Se restablecieron las preferencias generales.'
                    : 'Se restablecieron las preferencias locales; se mantienen las generales de la portada.');
            });
        }

        if (closeButton) {
            closeButton.addEventListener('click', function () {
                closeControls(activeTrigger);
            });
        }

        controls.addEventListener('cancel', function (event) {
            event.preventDefault();
            closeControls(activeTrigger);
        });

        controls.addEventListener('click', function (event) {
            const bounds = controls.getBoundingClientRect();
            const clickedOutside = event.clientX < bounds.left
                || event.clientX > bounds.right
                || event.clientY < bounds.top
                || event.clientY > bounds.bottom;
            if (clickedOutside) {
                closeControls(activeTrigger);
            }
        });

        triggers.forEach(function (trigger) {
            trigger.addEventListener('click', function () {
                if (controls.open) {
                    closeControls(trigger);
                    return;
                }

                activeTrigger = trigger;
                controls.showModal();
                triggers.forEach(function (item) {
                    item.setAttribute('aria-expanded', String(item === trigger));
                });
                const firstControl = document.getElementById('accessibilityTextDecrease');
                if (firstControl) {
                    firstControl.focus();
                }
                announce(storageAvailable
                    ? scopeCopy.opened
                    : 'Las preferencias se aplican, pero el navegador no permite guardarlas.');
            });
        });
    }

    function bindReadingGuide() {
        let framePending = false;
        let guideTop = window.innerHeight / 2;

        function positionGuide(top) {
            guideTop = Math.max(24, Math.min(window.innerHeight - 24, top));
            if (framePending) {
                return;
            }
            framePending = true;
            window.requestAnimationFrame(function () {
                root.style.setProperty('--accessibility-reading-guide-top', guideTop + 'px');
                framePending = false;
            });
        }

        document.addEventListener('pointermove', function (event) {
            if (preferences.readingGuide && event.pointerType !== 'touch') {
                positionGuide(event.clientY);
            }
        }, { passive: true });

        document.addEventListener('focusin', function (event) {
            if (preferences.readingGuide && !(controls && controls.contains(event.target))) {
                const bounds = event.target.getBoundingClientRect();
                positionGuide(bounds.top + Math.min(bounds.height / 2, 20));
            }
        });
    }

    function splitSpeechText(text) {
        const words = text.replace(/\s+/g, ' ').trim().split(' ');
        const chunks = [];
        let chunk = '';
        words.forEach(function (word) {
            if (chunk && (chunk.length + word.length + 1) > 180) {
                chunks.push(chunk);
                chunk = '';
            }
            chunk += (chunk ? ' ' : '') + word;
        });
        if (chunk) {
            chunks.push(chunk);
        }
        return chunks;
    }

    function bindSpeechControls() {
        const readButton = document.getElementById('accessibilityReadAloud');
        if (!readButton) {
            return;
        }

        const pauseButton = document.getElementById('accessibilityPauseReading');
        const stopButton = document.getElementById('accessibilityStopReading');
        const voiceSelect = document.getElementById('accessibilityVoice');
        const rateInput = document.getElementById('accessibilitySpeechRate');
        const supported = typeof window.speechSynthesis !== 'undefined'
            && typeof window.SpeechSynthesisUtterance !== 'undefined';
        let speechChunks = [];

        function populateVoices() {
            if (!supported || !voiceSelect) {
                return;
            }
            const voices = window.speechSynthesis.getVoices().slice().sort(function (a, b) {
                const spanishOrder = Number(b.lang.toLowerCase().startsWith('es'))
                    - Number(a.lang.toLowerCase().startsWith('es'));
                return spanishOrder || a.name.localeCompare(b.name);
            });
            voiceSelect.replaceChildren();
            const defaultOption = document.createElement('option');
            defaultOption.value = '';
            defaultOption.textContent = 'Voz predeterminada del dispositivo';
            voiceSelect.appendChild(defaultOption);
            voices.forEach(function (voice) {
                const option = document.createElement('option');
                option.value = voice.voiceURI;
                option.textContent = voice.name + ' · ' + voice.lang;
                voiceSelect.appendChild(option);
            });
            voiceSelect.value = voices.some(function (voice) {
                return voice.voiceURI === preferences.voiceURI;
            }) ? preferences.voiceURI : '';
        }

        function setPausedState(paused) {
            if (!pauseButton) {
                return;
            }
            pauseButton.textContent = paused ? 'Reanudar' : 'Pausar';
            pauseButton.setAttribute('aria-pressed', String(paused));
        }

        function speakNextChunk() {
            if (!supported || !speechChunks.length) {
                if (!speechChunks.length) {
                    announce('Terminó la lectura del contenido principal.');
                }
                return;
            }
            const utterance = new window.SpeechSynthesisUtterance(speechChunks.shift());
            const selectedVoice = window.speechSynthesis.getVoices().find(function (voice) {
                return voice.voiceURI === preferences.voiceURI;
            });
            if (selectedVoice) {
                utterance.voice = selectedVoice;
            }
            utterance.rate = preferences.speechRate;
            utterance.lang = selectedVoice ? selectedVoice.lang : document.documentElement.lang || 'es';
            utterance.onend = speakNextChunk;
            utterance.onerror = function (event) {
                if (event.error !== 'canceled' && event.error !== 'interrupted') {
                    announce('No se pudo continuar la lectura en voz alta.');
                }
            };
            window.speechSynthesis.speak(utterance);
        }

        function startReading() {
            if (!supported) {
                announce('La lectura en voz alta no está disponible en este navegador.');
                return;
            }
            if (window.speechSynthesis.paused) {
                window.speechSynthesis.resume();
                setPausedState(false);
                announce('Lectura reanudada.');
                return;
            }
            const main = document.querySelector('main') || document.body;
            const copy = main.cloneNode(true);
            copy.querySelectorAll('script, style, noscript, svg, [aria-hidden="true"], [hidden], .accessibility-controls, [data-accessibility-trigger]')
                .forEach(function (element) {
                    element.remove();
                });
            const text = copy.innerText || copy.textContent || '';
            speechChunks = splitSpeechText(text);
            if (!speechChunks.length) {
                announce('No se encontró contenido principal para leer.');
                return;
            }
            window.speechSynthesis.cancel();
            setPausedState(false);
            announce('Leyendo el contenido principal.');
            speakNextChunk();
        }

        function stopReading() {
            if (supported) {
                window.speechSynthesis.cancel();
            }
            speechChunks = [];
            setPausedState(false);
        }

        stopSpeechReading = stopReading;
        if (!supported) {
            readButton.disabled = true;
            if (pauseButton) pauseButton.disabled = true;
            if (stopButton) stopButton.disabled = true;
            if (voiceSelect) voiceSelect.disabled = true;
            announce('La lectura en voz alta no está disponible en este navegador.');
        } else {
            populateVoices();
            if (typeof window.speechSynthesis.addEventListener === 'function') {
                window.speechSynthesis.addEventListener('voiceschanged', populateVoices);
            }
            window.setTimeout(populateVoices, 250);
            readButton.addEventListener('click', startReading);
            if (pauseButton) {
                pauseButton.addEventListener('click', function () {
                    if (!window.speechSynthesis.speaking && !window.speechSynthesis.paused) {
                        announce('Inicia primero la lectura del contenido.');
                    } else if (window.speechSynthesis.paused) {
                        window.speechSynthesis.resume();
                        setPausedState(false);
                        announce('Lectura reanudada.');
                    } else {
                        window.speechSynthesis.pause();
                        setPausedState(true);
                        announce('Lectura pausada.');
                    }
                });
            }
            if (stopButton) {
                stopButton.addEventListener('click', function () {
                    stopReading();
                    announce('Lectura detenida.');
                });
            }
            if (voiceSelect) {
                voiceSelect.value = preferences.voiceURI;
                voiceSelect.addEventListener('change', function () {
                    updatePreference('voiceURI', voiceSelect.value);
                    savePreferences();
                    announce('Se actualizó la voz de lectura. ' + scopeCopy.saved);
                });
            }
        }

        updateSpeechRateOutput();
        if (rateInput) {
            rateInput.addEventListener('input', function () {
                updatePreference('speechRate', Number(rateInput.value));
                updateSpeechRateOutput();
                savePreferences();
                announce('Velocidad de lectura: ' + preferences.speechRate.toFixed(1) + ' veces. ' + scopeCopy.saved);
            });
        }

    }

    applyPreferences();
    syncTextScaleControl();
    bindSettingsControls();
    bindReadingGuide();
    bindSpeechControls();

    if (!storageAvailable) {
        announce('No se pudieron cargar las preferencias guardadas; puedes cambiarlas durante esta visita.');
    }
});
