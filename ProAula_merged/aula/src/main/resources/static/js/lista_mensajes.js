document.addEventListener('DOMContentLoaded', function() {
    const sidebar = document.querySelector('.sidebar');
    const toggleButton = document.querySelector('.sidebar-toggle');

    if (window.innerWidth <= 768 && sidebar) {
        sidebar.classList.add('collapsed');
    }

    window.addEventListener('resize', function() {
        if (!sidebar) return;
        if (window.innerWidth <= 768) {
            sidebar.classList.add('collapsed');
        } else {
            sidebar.classList.remove('collapsed');
        }
    });
});
