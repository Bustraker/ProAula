(function () {
    'use strict';

    var rootMeta = document.querySelector('meta[name="app-context-path"]');
    var csrfTokenMeta = document.querySelector('meta[name="_csrf"]');
    var csrfHeaderMeta = document.querySelector('meta[name="_csrf_header"]');
    var lista = document.getElementById('listaFavoritos');
    var listaTrayectos = document.getElementById('listaTrayectosFavoritos');

    function apiUrl(path) {
        var root = rootMeta ? rootMeta.content : '/';
        return root.replace(/\/$/, '') + path;
    }

    function headers() {
        var valores = { Accept: 'application/json' };
        if (csrfTokenMeta && csrfHeaderMeta) {
            valores[csrfHeaderMeta.content] = csrfTokenMeta.content;
        }
        return valores;
    }

    function mensaje(texto, error) {
        var contenedor = document.getElementById('favoritos-section');
        var salida = document.getElementById('favoritosFeedback');
        if (!salida) {
            salida = document.createElement('p');
            salida.id = 'favoritosFeedback';
            salida.className = 'favoritos-feedback';
            salida.setAttribute('role', 'status');
            contenedor.appendChild(salida);
        }
        salida.textContent = texto;
        salida.classList.toggle('favoritos-feedback--error', Boolean(error));
    }

    function actualizarListaVacia() {
        if (lista && lista.children.length) return;
        var panel = document.querySelector('#favoritos-section .favoritos-panel');
        if (document.getElementById('favoritosVacio')) return;

        var vacio = document.createElement('div');
        vacio.id = 'favoritosVacio';
        vacio.className = 'dashboard-empty-state favoritos-vacio';
        var icono = document.createElement('span');
        icono.className = 'empty-state-icon';
        var iconoInterno = document.createElement('i');
        iconoInterno.className = 'fas fa-bookmark';
        icono.setAttribute('aria-hidden', 'true');
        icono.appendChild(iconoInterno);
        var texto = document.createElement('div');
        var titulo = document.createElement('h3');
        titulo.textContent = 'Aún no tienes favoritos';
        var descripcion = document.createElement('p');
        descripcion.textContent = 'En el planificador, usa la estrella para guardar una ruta o una parada.';
        texto.appendChild(titulo);
        texto.appendChild(descripcion);
        vacio.appendChild(icono);
        vacio.appendChild(texto);
        panel.appendChild(vacio);
        if (lista) lista.remove();
        lista = null;
    }

    async function quitarFavorito(boton) {
        var id = boton.getAttribute('data-favorito-id');
        boton.disabled = true;
        try {
            var respuesta = await fetch(apiUrl('/api/favoritos/' + encodeURIComponent(id)), {
                method: 'DELETE',
                credentials: 'same-origin',
                headers: headers()
            });
            if (!respuesta.ok) {
                var error = await respuesta.json().catch(function () { return {}; });
                throw new Error(error.message || 'No se pudo quitar el favorito.');
            }
            var elemento = boton.closest('.favorito-item');
            if (elemento) elemento.remove();
            actualizarListaVacia();
            mensaje('Se quitó el elemento de tus favoritos.');
        } catch (error) {
            boton.disabled = false;
            mensaje(error.message || 'No se pudo quitar el favorito. Inténtalo nuevamente.', true);
        }
    }

    function actualizarTrayectosVacios() {
        if (listaTrayectos && listaTrayectos.children.length) return;
        if (document.getElementById('trayectosVacio')) return;

        var vacio = document.createElement('div');
        vacio.id = 'trayectosVacio';
        vacio.className = 'dashboard-empty-state favoritos-vacio';
        var icono = document.createElement('span');
        icono.className = 'empty-state-icon';
        icono.setAttribute('aria-hidden', 'true');
        var iconoInterno = document.createElement('i');
        iconoInterno.className = 'fas fa-map';
        icono.appendChild(iconoInterno);
        var contenido = document.createElement('div');
        var titulo = document.createElement('h3');
        titulo.textContent = 'Aún no has guardado trayectos';
        var descripcion = document.createElement('p');
        descripcion.textContent = 'Al llegar a tu destino podrás revisar el resumen y guardarlo aquí.';
        contenido.appendChild(titulo);
        contenido.appendChild(descripcion);
        vacio.appendChild(icono);
        vacio.appendChild(contenido);
        document.querySelector('.trayectos-guardados').appendChild(vacio);
        if (listaTrayectos) listaTrayectos.remove();
        listaTrayectos = null;
    }

    async function quitarTrayecto(boton) {
        var id = boton.getAttribute('data-trayecto-id');
        boton.disabled = true;
        try {
            var respuesta = await fetch(apiUrl('/api/favoritos/trayectos/' + encodeURIComponent(id)), {
                method: 'DELETE',
                credentials: 'same-origin',
                headers: headers()
            });
            if (!respuesta.ok) {
                var error = await respuesta.json().catch(function () { return {}; });
                throw new Error(error.message || 'No se pudo quitar el trayecto guardado.');
            }
            var elemento = boton.closest('.trayecto-guardado');
            if (elemento) {
                elemento.classList.add('is-removing');
                window.setTimeout(function () {
                    elemento.remove();
                    actualizarTrayectosVacios();
                }, 220);
            }
            mensaje('Se quitó el trayecto de tus favoritos.');
        } catch (error) {
            boton.disabled = false;
            mensaje(error.message || 'No se pudo quitar el trayecto. Inténtalo nuevamente.', true);
        }
    }

    function actualizarContador() {
        var contador = document.getElementById('avisosFavoritosNoLeidos');
        if (!contador) return;
        var cantidad = Number.parseInt(contador.textContent, 10);
        if (Number.isFinite(cantidad) && cantidad > 1) {
            contador.textContent = String(cantidad - 1);
        } else {
            contador.remove();
        }
    }

    async function marcarAvisoLeido(boton) {
        var id = boton.getAttribute('data-aviso-id');
        boton.disabled = true;
        try {
            var respuesta = await fetch(apiUrl('/api/favoritos/notificaciones/' + encodeURIComponent(id) + '/leida'), {
                method: 'PATCH',
                credentials: 'same-origin',
                headers: headers()
            });
            if (!respuesta.ok) {
                var error = await respuesta.json().catch(function () { return {}; });
                throw new Error(error.message || 'No se pudo actualizar el aviso.');
            }
            var elemento = boton.closest('.aviso-item');
            if (elemento) {
                elemento.classList.add('aviso-item--leido');
                var estado = elemento.querySelector('.aviso-item__estado');
                if (estado) estado.remove();
                boton.remove();
            }
            actualizarContador();
        } catch (error) {
            boton.disabled = false;
            mensaje(error.message || 'No se pudo actualizar el aviso. Inténtalo nuevamente.', true);
        }
    }

    document.addEventListener('click', function (evento) {
        var quitarTrayectoBoton = evento.target.closest('[data-trayecto-id]');
        if (quitarTrayectoBoton) {
            quitarTrayecto(quitarTrayectoBoton);
            return;
        }
        var quitar = evento.target.closest('[data-favorito-id]');
        if (quitar) {
            quitarFavorito(quitar);
            return;
        }
        var leer = evento.target.closest('[data-aviso-id]');
        if (leer) marcarAvisoLeido(leer);
    });

    if (lista) actualizarListaVacia();
})();
