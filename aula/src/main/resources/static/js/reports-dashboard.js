(function () {
    'use strict';

    var COLORS = ['#9f3444', '#477e71', '#c1874c', '#657aa4', '#a96f84', '#7b8791'];
    var SVG_NS = 'http://www.w3.org/2000/svg';
    var REFRESH_INTERVAL_MS = 15000;
    var dashboard;
    var period = 12;
    var refreshTimer;
    var loading = false;

    document.addEventListener('DOMContentLoaded', function () {
        dashboard = {
            activity: document.getElementById('chartActivity'),
            fleet: document.getElementById('chartFleet'),
            incidents: document.getElementById('chartIncidentTypes'),
            routes: document.getElementById('chartRoutes'),
            occupancy: document.getElementById('chartOccupancy'),
            routeStates: document.getElementById('chartRouteStates'),
            coverageHigh: document.getElementById('coverageHighCount'),
            coverageMedium: document.getElementById('coverageMediumCount'),
            coverageLow: document.getElementById('coverageLowCount'),
            coverageUnservedPopulation: document.getElementById('coverageUnservedPopulation'),
            coverageNotice: document.getElementById('coverageModelNotice'),
            coverageChart: document.getElementById('coveragePriorityChart'),
            coverageTable: document.getElementById('coveragePriorityTable'),
            state: document.getElementById('reportsDataState'),
            notice: document.getElementById('reportsSyntheticNotice'),
            error: document.getElementById('reportsError'),
            updated: document.getElementById('reportsUpdatedAt'),
            period: document.getElementById('activityPeriod')
        };
        dashboard.period.addEventListener('change', function () {
            period = Number(dashboard.period.value);
            if (dashboard.data) drawActivity(dashboard.data.actividadMensual);
        });
        document.addEventListener('visibilitychange', function () {
            if (document.hidden) {
                window.clearTimeout(refreshTimer);
                return;
            }
            loadDashboard();
        });
        loadDashboard();
    });

    function element(tag, className, text) {
        var node = document.createElement(tag);
        if (className) node.className = className;
        if (text != null) node.textContent = text;
        return node;
    }

    function svgElement(tag, attributes) {
        var node = document.createElementNS(SVG_NS, tag);
        Object.keys(attributes).forEach(function (name) {
            node.setAttribute(name, attributes[name]);
        });
        return node;
    }

    function addSvgText(svg, x, y, text, className, anchor) {
        var label = svgElement('text', { x: x, y: y, class: className || '' });
        if (anchor) label.setAttribute('text-anchor', anchor);
        label.textContent = text;
        svg.appendChild(label);
        return label;
    }

    function replace(container, node) {
        while (container.firstChild) container.removeChild(container.firstChild);
        container.appendChild(node);
    }

    function showEmpty(container, message) {
        replace(container, element('p', 'reports-chart-empty', message));
    }

    function setMetric(selector, value) {
        var target = document.querySelector(selector);
        if (target) target.textContent = Number(value || 0).toLocaleString('es-CO');
    }

    async function loadDashboard() {
        if (loading) return;
        loading = true;
        window.clearTimeout(refreshTimer);
        dashboard.error.hidden = true;
        dashboard.state.textContent = 'Actualizando…';
        try {
            var meta = document.querySelector('meta[name="app-context-path"]');
            var contextPath = meta ? meta.content.replace(/\/+$/, '') : '';
            var response = await fetch(contextPath + '/api/admin/reportes/indicadores', {
                credentials: 'same-origin',
                headers: { Accept: 'application/json' },
                cache: 'no-store'
            });
            if (!response.ok) throw new Error('No fue posible consultar los indicadores (HTTP ' + response.status + ').');

            var data = await response.json();
            if (!data || !Array.isArray(data.actividadMensual)
                || !data.flotaPorEstado || !data.incidentesPorTipo
                || !data.prioridadCoberturaBarrios
                || !Array.isArray(data.prioridadCoberturaBarrios.barriosPrioritarios)) {
                throw new Error('El servidor devolvió un formato de indicadores no válido.');
            }

            dashboard.data = data;
            renderDashboard(data);
        } catch (error) {
            console.error('Error al cargar los indicadores de reportes:', error);
            dashboard.state.textContent = 'Indicadores no disponibles';
            dashboard.error.textContent = error.message || 'No se pudieron cargar los datos del sistema.';
            var retry = element('button', 'reports-button reports-button--quiet', 'Reintentar');
            retry.type = 'button';
            retry.addEventListener('click', loadDashboard);
            dashboard.error.appendChild(retry);
            dashboard.error.hidden = false;
        } finally {
            loading = false;
            if (!document.hidden) {
                refreshTimer = window.setTimeout(loadDashboard, REFRESH_INTERVAL_MS);
            }
        }
    }

    function renderDashboard(data) {
        setMetric('.reports-metric--fleet .reports-metric__value', data.totalBuses);
        setMetric('#metricActiveRoutes', data.rutasActivas);
        setMetric('.reports-metric--accounts .reports-metric__value', data.totalUsuarios);
        setMetric('#metricOpenIncidents', data.incidentesPorAtender);
        dashboard.state.textContent = Number(data.paradasActivas || 0).toLocaleString('es-CO') +
            ' paradas activas · ' + Number(data.totalPasajes || 0).toLocaleString('es-CO') +
            ' pasajes registrados';

        dashboard.notice.hidden = !data.datosSinteticosConfigurados;
        dashboard.notice.textContent = data.datosSinteticosConfigurados
            ? 'El entorno tiene habilitada la generación de datos sintéticos. Los conteos de viajes, pasajes, incidentes y flota pueden incluir registros de demostración; no son indicadores de operación real.'
            : 'Los gráficos reflejan registros almacenados en Bustraker. Las probabilidades de modelos y las estimaciones de ruta no se presentan como actividad real.';

        drawActivity(data.actividadMensual);
        drawFleet(data.flotaPorEstado, data.totalBuses);
        drawCategories(dashboard.incidents, data.incidentesPorTipo, 'No hay incidentes registrados.', 'reports-bar-list--incidents');
        drawCategories(dashboard.routes, data.rutasMasConsultadas,
            'La bitácora aún no contiene viajes para comparar rutas.', 'reports-bar-list--routes');
        drawCategories(dashboard.occupancy, Object.keys(data.flotaPorOcupacion || {}).map(function (name) {
            return { nombre: name, cantidad: data.flotaPorOcupacion[name] };
        }), 'No hay datos de ocupación para los buses.', 'reports-bar-list--occupancy');
        drawCategories(dashboard.routeStates, Object.keys(data.rutasPorEstado || {}).map(function (name) {
            return { nombre: name, cantidad: data.rutasPorEstado[name] };
        }), 'No hay rutas registradas.', 'reports-bar-list--route-states');
        drawCoveragePriority(data.prioridadCoberturaBarrios);
        drawIncidentStates(data.incidentesPorEstado);
        dashboard.updated.textContent = 'Actualizado ' + new Intl.DateTimeFormat('es-CO', {
            dateStyle: 'medium',
            timeStyle: 'short'
        }).format(new Date()) + ' · actualización automática cada 15 s';
    }

    function drawActivity(months) {
        var selectedMonths = months.slice(-period);
        if (!selectedMonths.some(function (month) {
            return Number(month.viajesRegistrados) > 0 || Number(month.incidentesReportados) > 0;
        })) {
            showEmpty(dashboard.activity, 'Aún no hay viajes o incidentes registrados en este periodo.');
            return;
        }

        var width = 720;
        var height = 265;
        var left = 44;
        var right = 12;
        var top = 18;
        var bottom = 44;
        var plotHeight = height - top - bottom;
        var plotWidth = width - left - right;
        var maximum = Math.max.apply(null, selectedMonths.map(function (month) {
            return Math.max(Number(month.viajesRegistrados) || 0, Number(month.incidentesReportados) || 0);
        }));
        var scaleMaximum = Math.max(1, Math.ceil(maximum / 5) * 5);
        var svg = svgElement('svg', {
            class: 'reports-activity-svg',
            viewBox: '0 0 ' + width + ' ' + height,
            role: 'img',
            'aria-label': 'Actividad mensual: viajes guardados en la bitácora e incidentes reportados'
        });
        var title = svgElement('title', {});
        title.textContent = 'Viajes e incidentes registrados por mes';
        svg.appendChild(title);

        for (var tick = 0; tick <= 4; tick++) {
            var y = top + plotHeight * tick / 4;
            svg.appendChild(svgElement('line', {
                x1: left, y1: y, x2: width - right, y2: y, class: 'reports-activity-gridline'
            }));
            addSvgText(svg, left - 8, y + 4, String(Math.round(scaleMaximum * (4 - tick) / 4)),
                'reports-activity-axis-label', 'end');
        }

        var groupWidth = plotWidth / selectedMonths.length;
        var barWidth = Math.max(3, Math.min(15, groupWidth * 0.26));
        selectedMonths.forEach(function (month, index) {
            var center = left + groupWidth * (index + 0.5);
            [
                { key: 'viajesRegistrados', color: COLORS[0], offset: -barWidth / 2 },
                { key: 'incidentesReportados', color: COLORS[1], offset: barWidth / 2 }
            ].forEach(function (series) {
                var value = Number(month[series.key]) || 0;
                var barHeight = plotHeight * value / scaleMaximum;
                var bar = svgElement('rect', {
                    x: center + series.offset - barWidth / 2,
                    y: top + plotHeight - barHeight,
                    width: barWidth,
                    height: Math.max(value > 0 ? 1 : 0, barHeight),
                    rx: 3,
                    fill: series.color,
                    class: 'reports-activity-bar'
                });
                var tooltip = svgElement('title', {});
                tooltip.textContent = month.mes + ' · ' +
                    (series.key === 'viajesRegistrados' ? 'Viajes registrados: ' : 'Incidentes: ') + value;
                bar.appendChild(tooltip);
                svg.appendChild(bar);
            });
            addSvgText(svg, center, height - 19, month.mes,
                'reports-activity-axis-label reports-activity-month-label', 'middle');
        });

        var legendY = height - 3;
        [
            { name: 'Viajes en bitácora', color: COLORS[0], x: left + 6 },
            { name: 'Incidentes reportados', color: COLORS[1], x: left + 170 }
        ].forEach(function (item) {
            svg.appendChild(svgElement('circle', {
                cx: item.x, cy: legendY - 4, r: 4, fill: item.color
            }));
            addSvgText(svg, item.x + 9, legendY, item.name, 'reports-activity-legend');
        });
        replace(dashboard.activity, svg);
    }

    function drawFleet(distribution, total) {
        var values = Object.keys(distribution || {}).map(function (name) {
            return { nombre: name, cantidad: Number(distribution[name]) || 0 };
        });
        if (!total) {
            showEmpty(dashboard.fleet, 'No hay buses registrados.');
            return;
        }

        var wrapper = element('div', 'reports-donut-layout');
        var svg = svgElement('svg', {
            class: 'reports-donut',
            viewBox: '0 0 160 160',
            role: 'img',
            'aria-label': 'Estado operativo de ' + total + ' buses'
        });
        var title = svgElement('title', {});
        title.textContent = 'Distribución de buses por estado';
        svg.appendChild(title);
        var circumference = 2 * Math.PI * 53;
        svg.appendChild(svgElement('circle', {
            cx: 80, cy: 80, r: 53, class: 'reports-donut-track'
        }));
        var offset = 0;
        values.forEach(function (item, index) {
            if (!item.cantidad) return;
            var segment = circumference * item.cantidad / total;
            var ring = svgElement('circle', {
                cx: 80, cy: 80, r: 53,
                class: 'reports-donut-segment',
                stroke: COLORS[index % COLORS.length],
                'stroke-dasharray': segment + ' ' + (circumference - segment),
                'stroke-dashoffset': -offset
            });
            var tooltip = svgElement('title', {});
            tooltip.textContent = item.nombre + ': ' + item.cantidad;
            ring.appendChild(tooltip);
            svg.appendChild(ring);
            offset += segment;
        });
        addSvgText(svg, 80, 77, Number(total).toLocaleString('es-CO'), 'reports-donut-total', 'middle');
        addSvgText(svg, 80, 93, 'BUSES', 'reports-donut-caption', 'middle');
        wrapper.appendChild(svg);
        var legend = element('ul', 'reports-donut-legend');
        values.forEach(function (item, index) {
            var row = element('li');
            var marker = element('span', 'reports-legend-dot');
            marker.style.backgroundColor = COLORS[index % COLORS.length];
            row.appendChild(marker);
            row.appendChild(element('span', 'reports-legend-name', item.nombre));
            row.appendChild(element('strong', null, Number(item.cantidad).toLocaleString('es-CO')));
            legend.appendChild(row);
        });
        wrapper.appendChild(legend);
        replace(dashboard.fleet, wrapper);
    }

    function drawCategories(container, categories, emptyMessage, listClass) {
        var values = (categories || []).filter(function (item) {
            return item && Number.isFinite(Number(item.cantidad)) && Number(item.cantidad) > 0;
        });
        if (!values.length) {
            showEmpty(container, emptyMessage);
            return;
        }
        var maximum = Math.max.apply(null, values.map(function (item) { return Number(item.cantidad); }));
        var list = element('div', 'reports-bar-list ' + listClass);
        values.forEach(function (item, index) {
            var row = element('div', 'reports-bar-row');
            var meta = element('div', 'reports-bar-row__meta');
            meta.appendChild(element('span', 'reports-bar-row__name',
                String(item.nombre).replace(/_/g, ' ')));
            meta.appendChild(element('strong', null, Number(item.cantidad).toLocaleString('es-CO')));
            var track = element('div', 'reports-bar-track');
            var bar = element('span', 'reports-bar-fill');
            bar.style.width = Math.max(3, Number(item.cantidad) / maximum * 100) + '%';
            bar.style.backgroundColor = COLORS[index % COLORS.length];
            track.appendChild(bar);
            row.appendChild(meta);
            row.appendChild(track);
            list.appendChild(row);
        });
        replace(container, list);
    }

    function drawIncidentStates(distribution) {
        var values = Object.keys(distribution || {}).map(function (name) {
            return { nombre: name.replace(/_/g, ' '), cantidad: Number(distribution[name]) || 0 };
        }).filter(function (item) { return item.cantidad > 0; });
        if (!values.length) return;
        var legend = element('ul', 'reports-status-legend');
        values.forEach(function (item, index) {
            var row = element('li');
            row.appendChild(element('span', 'reports-status-name', item.nombre));
            row.appendChild(element('strong', null, Number(item.cantidad).toLocaleString('es-CO')));
            row.style.setProperty('--reports-status-color', COLORS[(index + 2) % COLORS.length]);
            legend.appendChild(row);
        });
        dashboard.incidents.appendChild(legend);
    }

    function drawCoveragePriority(model) {
        dashboard.coverageHigh.textContent =
            Number(model.barriosPrioridadAlta || 0).toLocaleString('es-CO');
        dashboard.coverageMedium.textContent =
            Number(model.barriosPrioridadMedia || 0).toLocaleString('es-CO');
        dashboard.coverageLow.textContent =
            Number(model.barriosPrioridadBaja || 0).toLocaleString('es-CO');
        dashboard.coverageUnservedPopulation.textContent =
            Number(model.poblacionEnBarriosSinRutaVerificada || 0).toLocaleString('es-CO');
        dashboard.coverageNotice.textContent = [
            model.nombreModelo + ' · versión ' + model.versionModelo + '.',
            model.metodo,
            model.advertencia,
            'Barrios analizados: ' + Number(model.totalBarriosAnalizados || 0).toLocaleString('es-CO') + '.'
        ].filter(Boolean).join(' ');

        drawCoverageChart(dashboard.coverageChart, model.barriosPrioritarios);
        drawCoverageTable(dashboard.coverageTable, model.barriosPrioritarios);
    }

    function drawCoverageChart(container, neighborhoods) {
        var values = (neighborhoods || []).slice(0, 10).filter(function (item) {
            return item && Number.isFinite(Number(item.indicePrioridad));
        });
        if (!values.length) {
            showEmpty(container, 'No hay suficientes datos territoriales y rutas verificadas para calcular el índice.');
            return;
        }

        var list = element('div', 'reports-priority-list');
        values.forEach(function (item, index) {
            var score = Math.max(0, Math.min(100, Number(item.indicePrioridad)));
            var row = element('div', 'reports-priority-row');
            var meta = element('div', 'reports-priority-row__meta');
            var rank = element('span', 'reports-priority-row__rank', String(index + 1).padStart(2, '0'));
            var name = element('span', 'reports-priority-row__name', item.barrio || 'Barrio sin nombre');
            var scoreLabel = element('strong', 'reports-priority-row__score',
                score.toLocaleString('es-CO', { maximumFractionDigits: 1 }));
            var track = element('div', 'reports-priority-track');
            var bar = element('span', 'reports-priority-fill reports-priority-fill--' +
                String(item.nivelPrioridad || 'BAJA').toLowerCase());
            bar.style.width = score + '%';
            bar.title = (item.barrio || 'Barrio') + ': ' +
                score.toLocaleString('es-CO', { maximumFractionDigits: 1 }) + ' puntos · ' +
                (item.nivelPrioridad || 'BAJA');
            track.appendChild(bar);
            meta.appendChild(rank);
            meta.appendChild(name);
            meta.appendChild(scoreLabel);
            row.appendChild(meta);
            row.appendChild(track);
            list.appendChild(row);
        });
        replace(container, list);
    }

    function drawCoverageTable(container, neighborhoods) {
        var values = (neighborhoods || []).slice(0, 15);
        if (!values.length) {
            showEmpty(container, 'No hay barrios disponibles para mostrar.');
            return;
        }

        var table = element('table', 'reports-priority-table');
        var head = element('thead');
        var header = element('tr');
        ['Barrio', 'Población', 'Rutas', 'Paradas con ubicación', 'Índice / 100', 'Factores'].forEach(function (label) {
            var cell = element('th', null, label);
            cell.scope = 'col';
            header.appendChild(cell);
        });
        head.appendChild(header);
        table.appendChild(head);

        var body = element('tbody');
        values.forEach(function (item) {
            var row = element('tr');
            var name = element('td', 'reports-priority-table__name');
            name.appendChild(element('strong', null, item.barrio || 'Barrio sin nombre'));
            if (item.localidad) name.appendChild(element('small', null, item.localidad));

            var population = item.poblacionRegistrada == null
                ? NaN : Number(item.poblacionRegistrada);
            var populationCell = element('td', null,
                Number.isFinite(population) ? population.toLocaleString('es-CO') : 'Sin dato');
            var routes = Number(item.rutasActivasVerificadas) || 0;
            var routeCell = element('td', null, routes.toLocaleString('es-CO'));
            routeCell.title = routes === 0
                ? 'No aparece en rutas activas verificadas.'
                : routes + ' rutas activas verificadas incluyen este barrio.';

            var georeferenced = Number(item.paradasActivasGeorreferenciadas) || 0;
            var totalStops = Number(item.paradasActivasRegistradas) || 0;
            var stopsCell = element('td', null, georeferenced.toLocaleString('es-CO') +
                ' / ' + totalStops.toLocaleString('es-CO'));
            stopsCell.title = 'Paradas activas con coordenadas / paradas activas registradas.';

            var score = Number(item.indicePrioridad);
            var scoreCell = element('td');
            var badge = element('span', 'reports-priority-badge reports-priority-badge--' +
                String(item.nivelPrioridad || 'BAJA').toLowerCase(),
                Number.isFinite(score) ? score.toLocaleString('es-CO', { maximumFractionDigits: 1 }) +
                    ' · ' + (item.nivelPrioridad || 'BAJA') : 'Sin dato');
            scoreCell.appendChild(badge);

            var factorsCell = element('td');
            var factors = element('details', 'reports-priority-details');
            factors.appendChild(element('summary', null, 'Ver factores'));
            var factorList = element('ul', 'reports-priority-factors');
            (Array.isArray(item.factores) ? item.factores : []).forEach(function (factor) {
                factorList.appendChild(element('li', null, factor));
            });
            factors.appendChild(factorList);
            factorsCell.appendChild(factors);

            row.appendChild(name);
            row.appendChild(populationCell);
            row.appendChild(routeCell);
            row.appendChild(stopsCell);
            row.appendChild(scoreCell);
            row.appendChild(factorsCell);
            body.appendChild(row);
        });
        table.appendChild(body);

        var wrapper = element('div', 'reports-priority-table-scroll');
        wrapper.appendChild(table);
        replace(container, wrapper);
    }
}());
