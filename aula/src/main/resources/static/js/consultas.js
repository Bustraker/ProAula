document.addEventListener('DOMContentLoaded', function () {
    const busItems = Array.from(document.querySelectorAll('#base > .bus-card'));
    const searchInput = document.getElementById('searchInput');
    const routeFilter = document.getElementById('routeFilter');
    const clearButton = document.getElementById('clearButton');
    const emptyClearButton = document.getElementById('emptyClearButton');
    const toggleCards = document.getElementById('toggleCards');
    const toggleTable = document.getElementById('toggleTable');
    const base = document.getElementById('base');
    const tableContainer = document.getElementById('busTableContainer');
    const tableBody = document.querySelector('#busTable tbody');
    const visibleCount = document.getElementById('visibleCount');
    const visibleCountLabel = document.getElementById('visibleCountLabel');
    const resultsMessage = document.getElementById('resultsMessage');
    const noResults = document.getElementById('noResults');
    const authenticated = document.body.classList.contains('bus-directory-page--authenticated');

    if (!searchInput || !routeFilter || !base || !tableContainer || !tableBody) {
        return;
    }

    let listView = false;

    function normalize(value) {
        return (value || '')
            .toLocaleLowerCase('es')
            .normalize('NFD')
            .replace(/[\u0300-\u036f]/g, '')
            .trim();
    }

    function buildRouteOptions() {
        const routes = [...new Set(busItems
            .map((item) => item.dataset.ruta.trim())
            .filter(Boolean))]
            .sort((first, second) => first.localeCompare(second, 'es'));

        routes.forEach((route) => {
            const option = document.createElement('option');
            option.value = route;
            option.textContent = route;
            routeFilter.appendChild(option);
        });
    }

    function matchesFilter(item) {
        const neighborhoods = Array.from(item.querySelectorAll('.barrios li'))
            .map((neighborhood) => neighborhood.textContent.trim());
        const searchableText = normalize([
            item.dataset.placa,
            item.dataset.ruta,
            item.dataset.conductor,
            item.dataset.modelo,
            item.dataset.color,
            neighborhoods.join(' ')
        ].join(' '));
        const query = normalize(searchInput.value);
        const selectedRoute = routeFilter.value;

        return (!query || searchableText.includes(query))
            && (!selectedRoute || item.dataset.ruta === selectedRoute);
    }

    function appendCell(row, value) {
        const cell = document.createElement('td');
        cell.textContent = value || '—';
        row.appendChild(cell);
    }

    function renderTable(items) {
        tableBody.replaceChildren();
        items.forEach((item) => {
            const row = document.createElement('tr');
            const neighborhoods = Array.from(item.querySelectorAll('.barrios li'))
                .map((neighborhood) => neighborhood.textContent.trim())
                .filter(Boolean)
                .join(', ');

            if (authenticated) {
                appendCell(row, `#${item.dataset.id}`);
                appendCell(row, item.dataset.placa || 'Placa no registrada');
                appendCell(row, item.dataset.ruta || 'Sin ruta asignada');
                appendCell(row, item.dataset.conductor || 'Sin asignar');
                appendCell(row, item.dataset.modelo || 'No registrado');
                appendCell(row, item.dataset.color || 'No registrado');
                appendCell(row, neighborhoods || 'Sin barrios registrados');
                appendCell(row, item.dataset.hora || 'No registrada');
                appendCell(row, item.dataset.verificada === 'true' ? 'Verificada' : 'Pendiente');
            } else {
                appendCell(row, item.dataset.ruta || 'Ruta sin nombre');
                appendCell(row, neighborhoods || 'Sin barrios registrados');
            }
            tableBody.appendChild(row);
        });
    }

    function updateViewControls() {
        base.classList.toggle('is-list', listView);
        base.hidden = listView;
        tableContainer.hidden = !listView;
        toggleCards.classList.toggle('is-active', !listView);
        toggleTable.classList.toggle('is-active', listView);
        toggleCards.setAttribute('aria-pressed', String(!listView));
        toggleTable.setAttribute('aria-pressed', String(listView));
    }

    function applyFilter() {
        const matchingItems = busItems.filter(matchesFilter);
        const queryActive = Boolean(searchInput.value.trim() || routeFilter.value);

        busItems.forEach((item) => {
            const visible = matchingItems.includes(item);
            item.hidden = !visible;
        });

        visibleCount.textContent = String(matchingItems.length);
        visibleCountLabel.textContent = matchingItems.length === 1 ? 'unidad' : 'unidades';
        resultsMessage.textContent = queryActive
            ? `Mostrando ${matchingItems.length} de ${busItems.length} unidades`
            : 'Mostrando todas las unidades';
        noResults.hidden = busItems.length === 0 || matchingItems.length > 0;

        if (listView) {
            renderTable(matchingItems);
        }
    }

    function resetFilters() {
        searchInput.value = '';
        routeFilter.value = '';
        applyFilter();
        searchInput.focus();
    }

    busItems.forEach((item) => {
        const trigger = item.querySelector('.bus-card-trigger');
        const details = item.querySelector('.bus-card-details');
        if (!trigger || !details) {
            return;
        }

        trigger.addEventListener('click', () => {
            const isOpen = item.classList.toggle('is-open');
            trigger.setAttribute('aria-expanded', String(isOpen));
            details.setAttribute('aria-hidden', String(!isOpen));
        });
    });

    if (clearButton) {
        clearButton.addEventListener('click', resetFilters);
    }
    if (emptyClearButton) {
        emptyClearButton.addEventListener('click', resetFilters);
    }

    searchInput.addEventListener('input', applyFilter);
    routeFilter.addEventListener('change', applyFilter);

    toggleCards.addEventListener('click', () => {
        listView = false;
        updateViewControls();
        applyFilter();
    });

    toggleTable.addEventListener('click', () => {
        listView = true;
        updateViewControls();
        applyFilter();
    });

    document.addEventListener('keydown', (event) => {
        const target = event.target;
        const isTyping = target instanceof HTMLElement
            && (target.isContentEditable || ['INPUT', 'SELECT', 'TEXTAREA'].includes(target.tagName));

        if (event.key === '/' && !isTyping) {
            event.preventDefault();
            searchInput.focus();
        } else if (event.key === 'Escape' && target === searchInput && searchInput.value) {
            resetFilters();
        }
    });

    buildRouteOptions();
    updateViewControls();
    applyFilter();
});
