document.addEventListener('DOMContentLoaded', function () {
    const root = document.documentElement;
    const textKey = 'bustraker.accessibility.text-scale';
    const contrastKey = 'bustraker.accessibility.high-contrast';
    const allowedScales = [100, 110, 125];
    const loginRoutes = [
        '/inicio-de-sesion-mejorado',
        '/inicio-de-sesion-mejorado.html',
        '/inicio_de_sesion',
        '/admin-login',
        '/registro-mejorado',
        '/registro-mejorado.html',
        '/registro'
    ];

    if (loginRoutes.some(function (route) {
        return window.location.pathname === route || window.location.pathname.startsWith(route + '/');
    })) {
        sessionStorage.removeItem(textKey);
        sessionStorage.removeItem(contrastKey);
    }

    let textScale = Number(sessionStorage.getItem(textKey) || 100);
    let highContrast = sessionStorage.getItem(contrastKey) === 'true';

    if (!allowedScales.includes(textScale)) {
        textScale = 100;
    }

    let controls = document.querySelector('.accessibility-controls');
    if (!controls) {
        controls = document.createElement('aside');
        controls.className = 'accessibility-controls';
        controls.setAttribute('aria-label', 'Opciones de accesibilidad');
        controls.innerHTML =
            '<div class="accessibility-controls__heading"><span class="accessibility-controls__eyebrow">Accesibilidad</span>' +
            '<strong>Visualización</strong></div>' +
            '<div class="accessibility-controls__actions">' +
            '<button type="button" id="accessibilityTextDecrease" aria-label="Reducir tamaño del texto">A<span aria-hidden="true">−</span></button>' +
            '<button type="button" id="accessibilityTextIncrease" aria-label="Aumentar tamaño del texto">A<span aria-hidden="true">+</span></button>' +
            '<button type="button" id="accessibilityTextReset" aria-label="Restablecer tamaño del texto">100%</button>' +
            '<button type="button" id="accessibilityContrast" aria-pressed="false"><span aria-hidden="true">◐</span> Alto contraste</button>' +
            '</div>' +
            '<span id="accessibilityControlsStatus" class="accessibility-controls__status" role="status" aria-live="polite"></span>';
        document.body.appendChild(controls);
    }

    const increase = document.getElementById('accessibilityTextIncrease');
    const decrease = document.getElementById('accessibilityTextDecrease');
    const reset = document.getElementById('accessibilityTextReset');
    const contrast = document.getElementById('accessibilityContrast');
    const status = document.getElementById('accessibilityControlsStatus');

    function updateTextScale() {
        root.classList.toggle('accessibility-text-110', textScale === 110);
        root.classList.toggle('accessibility-text-125', textScale === 125);
        sessionStorage.setItem(textKey, String(textScale));

        if (reset) {
            reset.textContent = textScale + '%';
            reset.setAttribute('aria-label', 'Restablecer tamaño del texto a 100 por ciento');
        }
        if (status) {
            status.textContent = 'Texto al ' + textScale + ' por ciento.';
        }
    }

    function updateContrast() {
        root.classList.toggle('accessibility-high-contrast', highContrast);
        if (contrast) {
            contrast.setAttribute('aria-pressed', String(highContrast));
            contrast.classList.toggle('is-active', highContrast);
        }
        sessionStorage.setItem(contrastKey, String(highContrast));
        controls.classList.toggle('is-high-contrast', highContrast);
        if (status) {
            status.textContent = highContrast
                ? 'Alto contraste activado.'
                : 'Alto contraste desactivado.';
        }
    }

    if (increase) {
        increase.addEventListener('click', function () {
            const currentIndex = allowedScales.indexOf(textScale);
            textScale = allowedScales[Math.min(currentIndex + 1, allowedScales.length - 1)];
            updateTextScale();
        });
    }

    if (decrease) {
        decrease.addEventListener('click', function () {
            const currentIndex = allowedScales.indexOf(textScale);
            textScale = allowedScales[Math.max(currentIndex - 1, 0)];
            updateTextScale();
        });
    }

    if (reset) {
        reset.addEventListener('click', function () {
            textScale = 100;
            updateTextScale();
        });
    }

    if (contrast) {
        contrast.addEventListener('click', function () {
            highContrast = !highContrast;
            updateContrast();
        });
    }

    updateTextScale();
    updateContrast();
});
