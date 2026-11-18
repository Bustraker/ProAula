document.addEventListener('DOMContentLoaded', function() {
    const stopFilter = document.getElementById('stopAccessibilityFilter');
    const stopFilterStatus = document.getElementById('stopAccessibilityFilterStatus');

    function filterStops() {
        if (!stopFilter) {
            return;
        }
        const rows = Array.from(document.querySelectorAll('tr[data-accessibility-status]'));
        const visibleRows = rows.filter(row => {
            const matches = stopFilter.value === 'all'
                || row.dataset.accessibilityStatus === stopFilter.value;
            row.hidden = !matches;
            return matches;
        });
        if (stopFilterStatus) {
            stopFilterStatus.textContent = `${visibleRows.length} de ${rows.length} paradas mostradas.`;
        }
    }

    if (stopFilter) {
        stopFilter.addEventListener('change', filterStops);
        filterStops();
    }

    const cards = document.querySelectorAll('.card');
    cards.forEach((card, index) => {
        card.style.opacity = '0';
        card.style.transform = 'translateY(20px)';
        setTimeout(() => {
            card.style.transition = 'all 0.4s ease';
            card.style.opacity = '1';
            card.style.transform = 'translateY(0)';
        }, index * 100);
    });
});
