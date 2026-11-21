document.addEventListener('DOMContentLoaded', () => {
    const filters = document.querySelectorAll('.request-filter');
    const cards = document.querySelectorAll('.request-card');

    filters.forEach((filter) => {
        filter.addEventListener('click', () => {
            filters.forEach((item) => item.classList.remove('is-active'));
            filter.classList.add('is-active');
            const status = filter.dataset.filter;

            cards.forEach((card) => {
                card.classList.toggle('is-hidden', status !== 'ALL' && card.dataset.status !== status);
            });
        });
    });

    document.querySelectorAll('.request-copy').forEach((button) => {
        button.addEventListener('click', async () => {
            const code = button.dataset.code || '';
            try {
                await navigator.clipboard.writeText(code);
                button.innerHTML = '<i class="fas fa-check" aria-hidden="true"></i>';
                button.setAttribute('aria-label', 'Código copiado');
                window.setTimeout(() => {
                    button.innerHTML = '<i class="fas fa-copy" aria-hidden="true"></i>';
                    button.setAttribute('aria-label', 'Copiar código');
                }, 1800);
            } catch {
                window.prompt('Copia el código de acceso:', code);
            }
        });
    });
});
