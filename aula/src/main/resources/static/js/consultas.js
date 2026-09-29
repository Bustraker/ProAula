document.addEventListener('DOMContentLoaded', function() {
    const busItems = Array.from(document.querySelectorAll('#base > li'));
    const searchInput = document.getElementById('searchInput');
    const routeFilter = document.getElementById('routeFilter');
    const statusFilter = document.getElementById('statusFilter');
    const searchButton = document.getElementById('searchButton');
    const clearButton = document.getElementById('clearButton');
    const toggleCards = document.getElementById('toggleCards');
    const toggleTable = document.getElementById('toggleTable');
    const base = document.getElementById('base');
    const tableContainer = document.getElementById('busTableContainer');
    const tableBody = document.querySelector('#busTable tbody');

    function buildRouteOptions() {
        const routes = new Set();
        busItems.forEach(li => {
            const ruta = li.dataset.ruta?.trim();
            if (ruta) routes.add(ruta);
        });
        routes.forEach(ruta => {
            const option = document.createElement('option');
            option.value = ruta;
            option.textContent = ruta;
            routeFilter.appendChild(option);
        });
    }

    function matchesFilter(li) {
        const text = [
            li.querySelector('.bus-title')?.textContent,
            li.dataset.ruta,
            li.dataset.conductor,
            li.dataset.modelo,
            li.dataset.color
        ].join(' ').toLowerCase();

        const searchValue = searchInput.value.trim().toLowerCase();
        const routeValue = routeFilter.value;
        const statusValue = statusFilter.value;

        const matchesSearch = !searchValue || text.includes(searchValue);
        const matchesRoute = !routeValue || li.dataset.ruta === routeValue;
        const matchesStatus = !statusValue || li.dataset.estado === statusValue;

        return matchesSearch && matchesRoute && matchesStatus;
    }

    function applyFilter() {
        let visibleCount = 0;
        tableBody.innerHTML = '';

        busItems.forEach(li => {
            const visible = matchesFilter(li);
            li.style.display = visible ? '' : 'none';
            if (visible) {
                visibleCount += 1;
                if (tableContainer.style.display !== 'none') {
                    const row = document.createElement('tr');
                    row.innerHTML = `
                        <td>${li.querySelector('.bus-title')?.textContent || ''}</td>
                        <td>${li.dataset.ruta || 'Sin ruta'}</td>
                        <td>${li.dataset.conductor || 'Sin asignar'}</td>
                        <td>${li.dataset.modelo || 'N/A'}</td>
                        <td>${li.dataset.color || 'N/A'}</td>
                        <td>${Array.from(li.querySelectorAll('.barrios li')).map(item => item.textContent).join(', ') || 'Sin barrios'}</td>
                    `;
                    tableBody.appendChild(row);
                }
            }
        });

        if (visibleCount === 0) {
            tableBody.innerHTML = `<tr><td colspan="6" style="text-align:center;padding:1rem;color:#6b7a94;">No hay buses que coincidan con los filtros.</td></tr>`;
        }
    }

    function resetFilters() {
        searchInput.value = '';
        routeFilter.value = '';
        statusFilter.value = '';
        applyFilter();
    }

    function showCards() {
        base.style.display = '';
        tableContainer.style.display = 'none';
        toggleCards.classList.add('active');
        toggleTable.classList.remove('active');
        applyFilter();
    }

    function showTable() {
        base.style.display = 'none';
        tableContainer.style.display = '';
        toggleCards.classList.remove('active');
        toggleTable.classList.add('active');
        applyFilter();
    }

    busItems.forEach(li => {
        li.addEventListener('keydown', e => {
            if (e.key === 'Enter' || e.key === ' ') {
                li.classList.toggle('open');
                e.preventDefault();
            }
        });
        li.addEventListener('click', () => li.classList.toggle('open'));
    });

    buildRouteOptions();
    searchButton.addEventListener('click', applyFilter);
    clearButton.addEventListener('click', resetFilters);
    toggleCards.addEventListener('click', showCards);
    toggleTable.addEventListener('click', showTable);
    applyFilter();
});
