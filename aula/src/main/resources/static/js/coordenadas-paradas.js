(function () {
    'use strict';

    document.addEventListener('DOMContentLoaded', function () {
        document.querySelectorAll('[data-coordinates-gps]').forEach(function (boton) {
            boton.addEventListener('click', function () {
                var formulario = boton.closest('form');
                var feedback = formulario.querySelector('.coordinates-feedback');
                if (!navigator.geolocation) {
                    feedback.textContent = 'Este navegador no permite capturar ubicación GPS.';
                    return;
                }

                boton.disabled = true;
                feedback.textContent = 'Solicitando ubicación GPS…';
                navigator.geolocation.getCurrentPosition(function (posicion) {
                    formulario.querySelector('[name="latitud"]').value = posicion.coords.latitude.toFixed(7);
                    formulario.querySelector('[name="longitud"]').value = posicion.coords.longitude.toFixed(7);
                    var precision = Math.round(posicion.coords.accuracy);
                    feedback.textContent = 'Coordenadas capturadas con precisión aproximada de ±' +
                        precision + ' m. Verifica que estás en la parada real antes de guardar.';
                    boton.disabled = false;
                }, function (error) {
                    var mensajes = {
                        1: 'Permiso GPS denegado. Habilítalo en el navegador o ingresa coordenadas verificadas.',
                        2: 'No se pudo determinar la ubicación. Intenta en un lugar con mejor señal.',
                        3: 'La captura tardó demasiado. Inténtalo de nuevo.'
                    };
                    feedback.textContent = mensajes[error.code] || 'No se pudo capturar la ubicación GPS.';
                    boton.disabled = false;
                }, { enableHighAccuracy: true, maximumAge: 0, timeout: 20000 });
            });
        });
    });
})();
