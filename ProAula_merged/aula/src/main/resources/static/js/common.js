document.addEventListener('DOMContentLoaded', function() {
    document.querySelectorAll('.js-print').forEach(button => {
        button.addEventListener('click', function(event) {
            event.preventDefault();
            window.print();
        });
    });

    document.querySelectorAll('.js-dev-alert').forEach(element => {
        element.addEventListener('click', function(event) {
            event.preventDefault();
            alert('Función en desarrollo - Contacta al administrador');
        });
    });

    document.querySelectorAll('[data-confirm]').forEach(element => {
        element.addEventListener('click', function(event) {
            if (!confirm(this.dataset.confirm)) {
                event.preventDefault();
            }
        });
    });

    document.querySelectorAll('.js-go-back').forEach(button => {
        button.addEventListener('click', function(event) {
            event.preventDefault();
            history.back();
        });
    });

    document.querySelectorAll('.js-reload').forEach(button => {
        button.addEventListener('click', function(event) {
            event.preventDefault();
            window.location.reload();
        });
    });

    // Delegate calls to global functions using data-fn attribute: data-fn="functionName"
    document.querySelectorAll('[data-fn]').forEach(element => {
        element.addEventListener('click', function(event) {
            const fnName = this.dataset.fn;
            if (fnName && typeof window[fnName] === 'function') {
                event.preventDefault();
                // pass element as first arg if function expects it
                try { window[fnName](this); } catch (err) { console.error('Error calling', fnName, err); }
            }
        });
    });
});
