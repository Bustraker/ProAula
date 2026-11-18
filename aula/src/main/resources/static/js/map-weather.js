(function () {
    'use strict';

    var status = document.getElementById('weatherStatus');
    if (!status) return;

    var WEATHER_URL = 'https://api.open-meteo.com/v1/forecast'
        + '?latitude=10.4&longitude=-75.5'
        + '&current=temperature_2m,precipitation,weather_code,wind_speed_10m'
        + '&hourly=precipitation_probability&forecast_days=2&timezone=America%2FBogota';

    function weatherDescription(code) {
        if (code === 0) return 'Despejado';
        if (code === 1) return 'Mayormente despejado';
        if (code === 2) return 'Parcialmente nublado';
        if (code === 3) return 'Nublado';
        if (code === 45 || code === 48) return 'Niebla';
        if (code >= 51 && code <= 57) return 'Llovizna';
        if (code >= 61 && code <= 67) return 'Lluvia';
        if (code >= 71 && code <= 77) return 'Nieve';
        if (code >= 80 && code <= 82) return 'Chubascos';
        if (code === 85 || code === 86) return 'Chubascos de nieve';
        if (code >= 95 && code <= 99) return 'Tormenta';
        return 'Condición no disponible';
    }

    function maximumRainProbability(data) {
        var times = data.hourly && data.hourly.time;
        var probabilities = data.hourly && data.hourly.precipitation_probability;
        if (!Array.isArray(times) || !Array.isArray(probabilities)
                || times.length !== probabilities.length) {
            return null;
        }

        var currentTime = Date.parse(data.current.time);
        if (!Number.isFinite(currentTime)) return null;
        var forecastHorizon = currentTime + 3 * 60 * 60 * 1000;
        var maximum = null;
        for (var index = 0; index < times.length; index++) {
            var forecastTime = Date.parse(times[index]);
            if (!Number.isFinite(forecastTime) || forecastTime < currentTime) continue;
            if (forecastTime > forecastHorizon) break;
            var probability = probabilities[index];
            if (Number.isFinite(probability) && probability >= 0 && probability <= 100) {
                maximum = maximum === null ? probability : Math.max(maximum, probability);
            }
        }
        return maximum;
    }

    function showUnavailable() {
        status.classList.add('weather-panel--unavailable');
        status.textContent = 'Clima en línea no disponible. El planificador sigue funcionando '
            + 'sin asumir condiciones meteorológicas.';
    }

    async function loadWeather() {
        var controller = new AbortController();
        var timeout = setTimeout(function () { controller.abort(); }, 8000);
        try {
            var response = await fetch(WEATHER_URL, {
                headers: { Accept: 'application/json' },
                referrerPolicy: 'no-referrer',
                signal: controller.signal
            });
            if (!response.ok) throw new Error('Open-Meteo respondió HTTP ' + response.status + '.');

            var data = await response.json();
            var current = data && data.current;
            if (!current
                    || !Number.isFinite(current.temperature_2m)
                    || !Number.isFinite(current.precipitation)
                    || !Number.isFinite(current.weather_code)
                    || !Number.isFinite(current.wind_speed_10m)
                    || typeof current.time !== 'string') {
                throw new Error('La respuesta meteorológica está incompleta.');
            }

            var rainProbability = maximumRainProbability(data);
            var details = [
                weatherDescription(current.weather_code),
                Math.round(current.temperature_2m) + ' °C',
                'precipitación actual ' + current.precipitation.toFixed(1) + ' mm',
                'viento ' + Math.round(current.wind_speed_10m) + ' km/h'
            ];
            if (rainProbability !== null) {
                details.push('probabilidad máxima de lluvia en 3 h: ' + rainProbability + '%');
            }
            details.push('actualizado ' + current.time.replace('T', ' '));

            status.classList.remove('weather-panel--unavailable');
            status.textContent = details.join(' · ');
        } catch (error) {
            console.warn('No se pudo consultar el clima de Cartagena:', error);
            showUnavailable();
        } finally {
            clearTimeout(timeout);
        }
    }

    loadWeather();
})();
