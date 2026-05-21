// Toggle entre vista de grid y lista
let vistaGrid = true;
function toggleVista() {
    const grid = document.getElementById('rutasGrid');
    if (grid) {
        vistaGrid = !vistaGrid;
        if (vistaGrid) {
            grid.style.display = 'grid';
        } else {
            grid.style.display = 'flex';
            grid.style.flexDirection = 'column';
        }
    }
}

// Animación de entrada para las cards y enlace del botón de toggle
document.addEventListener('DOMContentLoaded', function() {
    document.querySelectorAll('.js-toggle-vista').forEach(btn => {
        btn.addEventListener('click', function(e) {
            e.preventDefault();
            toggleVista();
        });
    });

    const cards = document.querySelectorAll('.ruta-card');
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
