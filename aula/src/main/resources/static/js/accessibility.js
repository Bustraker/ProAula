document.addEventListener('DOMContentLoaded', function () {
    const root = document.documentElement;
    const increase = document.getElementById('accessibilityTextIncrease');
    const decrease = document.getElementById('accessibilityTextDecrease');
    const reset = document.getElementById('accessibilityTextReset');
    const contrast = document.getElementById('accessibilityContrast');
    const status = document.getElementById('accessibilityControlsStatus');
    const textKey = 'bustraker.accessibility.text-scale';
    const contrastKey = 'bustraker.accessibility.high-contrast';
    const allowedScales = [100, 110, 125];
    let textScale = Number(localStorage.getItem(textKey) || 100);
    let highContrast = localStorage.getItem(contrastKey) === 'true';

    if (!allowedScales.includes(textScale)) {
        textScale = 100;
    }

    function updateTextScale() {
        root.classList.toggle('accessibility-text-110', textScale === 110);
        root.classList.toggle('accessibility-text-125', textScale === 125);
        localStorage.setItem(textKey, String(textScale));
        if (status) {
            status.textContent = `Tamaño del texto: ${textScale}%.`;
        }
    }

    function updateContrast() {
        root.classList.toggle('accessibility-high-contrast', highContrast);
        if (contrast) {
            contrast.setAttribute('aria-pressed', String(highContrast));
        }
        localStorage.setItem(contrastKey, String(highContrast));
    }

    if (increase) {
        increase.addEventListener('click', function () {
            textScale = allowedScales[Math.min(allowedScales.indexOf(textScale) + 1, allowedScales.length - 1)];
            updateTextScale();
        });
    }

    if (decrease) {
        decrease.addEventListener('click', function () {
            textScale = allowedScales[Math.max(allowedScales.indexOf(textScale) - 1, 0)];
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
