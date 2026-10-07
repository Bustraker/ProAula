(() => {
    const search = document.getElementById('fleetSearch');
    const busList = document.getElementById('fleetBusList');
    const noResults = document.getElementById('fleetNoResults');
    const form = document.getElementById('fleetEditForm');

    if (search && busList && noResults) {
        const cards = Array.from(busList.querySelectorAll('.fleet-bus-card'));
        const filterBuses = () => {
            const query = search.value.trim().toLocaleLowerCase('es');
            let visibleCount = 0;

            cards.forEach((card) => {
                const matches = (card.dataset.search || '').toLocaleLowerCase('es').includes(query);
                card.hidden = !matches;
                visibleCount += matches ? 1 : 0;
            });

            noResults.hidden = visibleCount !== 0 || cards.length === 0;
        };

        search.addEventListener('input', filterBuses);
        document.addEventListener('keydown', (event) => {
            if (event.key === '/' && !event.ctrlKey && !event.metaKey && !event.altKey
                && !['INPUT', 'TEXTAREA', 'SELECT'].includes(document.activeElement?.tagName)) {
                event.preventDefault();
                search.focus();
            }
        });
    }

    if (form) {
        const fields = Array.from(form.querySelectorAll('.fleet-input-wrap input'));

        fields.forEach((field) => {
            const wrapper = field.closest('.fleet-input-wrap');
            const updateValidity = () => wrapper?.classList.toggle('is-valid', field.value.trim().length > 0);

            field.addEventListener('input', updateValidity);
            updateValidity();
        });
    }
})();
