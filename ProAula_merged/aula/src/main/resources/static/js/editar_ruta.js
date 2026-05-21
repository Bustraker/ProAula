function agregarBarrio() {
    const ul = document.getElementById('barrio-list');
    if (!ul) return;
    const li = document.createElement('li');
    li.innerHTML = '<input type="text" name="barrios[]" placeholder="Nuevo barrio"><button type="button" class="btn-remove-barrio">Eliminar</button>';
    ul.appendChild(li);
}

function eliminarBarrio(btn) {
    if (btn && btn.parentElement) {
        btn.parentElement.remove();
    }
}

document.addEventListener('click', function(event) {
    if (event.target && event.target.matches('.btn-remove-barrio')) {
        eliminarBarrio(event.target);
    }
});

// Handle route select redirect when element has class 'js-route-select'
document.querySelectorAll('.js-route-select').forEach(sel => {
    sel.addEventListener('change', function() {
        const val = this.value;
        if (val) window.location.href = '/editar-ruta/' + val;
    });
});
