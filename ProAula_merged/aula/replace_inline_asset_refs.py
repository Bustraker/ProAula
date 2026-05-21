from pathlib import Path
import re
root = Path('aula/src/main/resources/templates')
map = {
    'index.html': {
        'style': '<link rel="stylesheet" th:href="@{/css/index.css}">',
        'script': '<script th:src="@{/js/index.js}"></script>',
        'style_re': re.compile(r'<style>.*?</style>', re.DOTALL),
        'script_re': re.compile(r'<script id="adminScript">.*?</script>', re.DOTALL),
    },
    'dashboard-usuario.html': {
        'script': '<script th:src="@{/js/dashboard_usuario.js}"></script>',
        'script_re': re.compile(r'<script>.*?</script>', re.DOTALL),
    },
    'admin-login.html': {
        'script': '<script th:src="@{/js/admin_login.js}"></script>',
        'script_re': re.compile(r'<script>.*?</script>', re.DOTALL),
    },
    'inicio-de-sesion-mejorado.html': {
        'script': '<script th:src="@{/js/inicio_sesion_mejorado.js}"></script>',
        'script_re': re.compile(r'<script>.*?</script>', re.DOTALL),
    },
    'perfil-usuario.html': {
        'script': '<script th:src="@{/js/perfil.js}"></script>',
        'script_re': re.compile(r'<script>.*?</script>', re.DOTALL),
    },
    'registro-mejorado.html': {
        'script': '<script th:src="@{/js/registro_mejorado.js}"></script>',
        'script_re': re.compile(r'<script>.*?</script>', re.DOTALL),
    },
    'Admin/editar_ruta.html': {
        'script': '<script th:src="@{/js/editar_ruta.js}"></script>',
        'script_re': re.compile(r'<script>.*?</script>', re.DOTALL),
    },
    'Admin/index2.html': {
        'style': '<link rel="stylesheet" th:href="@{/css/admin_index2.css}">',
        'style_re': re.compile(r'<style>.*?</style>', re.DOTALL),
        'script': '<script th:src="@{/js/admin_index2.js}"></script>',
        'script_re': re.compile(r'<script>.*?</script>', re.DOTALL),
    },
    'Admin/reportes.html': {
        'style': '<link rel="stylesheet" th:href="@{/css/reportes.css}">',
        'style_re': re.compile(r'<style>.*?</style>', re.DOTALL),
    },
    'Admin/lista_mensajes.html': {
        'script': '<script th:src="@{/js/lista_mensajes.js}"></script>',
        'script_re': re.compile(r'<script>.*?</script>', re.DOTALL),
    },
    'Usuario/consultas.html': {
        'script': '<script th:src="@{/js/consultas.js}"></script>',
        'script_re': re.compile(r'<script>.*?</script>', re.DOTALL),
    },
    'Usuario/viajar.html': {
        'style': '<link rel="stylesheet" th:href="@{/css/viajar.css}">',
        'style_re': re.compile(r'<style>.*?</style>', re.DOTALL),
        'script': '    <script src="https://unpkg.com/leaflet/dist/leaflet.js"></script>\n    <script th:src="@{/js/viajar.js}"></script>',
        'script_re': re.compile(r'<script src="https://unpkg.com/leaflet/dist/leaflet\.js"></script>\s*<script>.*?</script>', re.DOTALL),
    },
    'detalle-ruta.html': {
        'script': '<script th:src="@{/js/detalle_ruta.js}"></script>',
        'script_re': re.compile(r'<script>.*?</script>', re.DOTALL),
    },
    'error/400.html': {
        'style': '<link rel="stylesheet" th:href="@{/css/error.css}">',
        'style_re': re.compile(r'<style>.*?</style>', re.DOTALL),
        'script': '<script th:src="@{/js/error.js}"></script>',
        'script_re': re.compile(r'<script>.*?</script>', re.DOTALL),
        'body_data': {'redirect_url': '/', 'redirect_timeout': '10000'},
    },
    'error/401.html': {
        'style': '<link rel="stylesheet" th:href="@{/css/error.css}">',
        'style_re': re.compile(r'<style>.*?</style>', re.DOTALL),
        'script': '<script th:src="@{/js/error.js}"></script>',
        'script_re': re.compile(r'<script>.*?</script>', re.DOTALL),
        'body_data': {'redirect_url': '/inicio-de-sesion-mejorado', 'redirect_timeout': '5000'},
    },
    'error/403.html': {
        'style': '<link rel="stylesheet" th:href="@{/css/error.css}">',
        'style_re': re.compile(r'<style>.*?</style>', re.DOTALL),
        'script': '<script th:src="@{/js/error.js}"></script>',
        'script_re': re.compile(r'<script>.*?</script>', re.DOTALL),
        'body_data': {'redirect_url': '/', 'redirect_timeout': '5000'},
    },
    'error/404.html': {
        'style': '<link rel="stylesheet" th:href="@{/css/error.css}">',
        'style_re': re.compile(r'<style>.*?</style>', re.DOTALL),
        'script': '<script th:src="@{/js/error.js}"></script>',
        'script_re': re.compile(r'<script>.*?</script>', re.DOTALL),
        'body_data': {'redirect_url': '/', 'redirect_timeout': '5000'},
    },
    'error/500.html': {
        'style': '<link rel="stylesheet" th:href="@{/css/error.css}">',
        'style_re': re.compile(r'<style>.*?</style>', re.DOTALL),
        'script': '<script th:src="@{/js/error.js}"></script>',
        'script_re': re.compile(r'<script>.*?</script>', re.DOTALL),
        'body_data': {'redirect_url': '/', 'redirect_timeout': '5000'},
    },
}

for rel, opts in map.items():
    path = root / rel
    if not path.exists():
        print(f'MISSING {path}')
        continue
    text = path.read_text(encoding='utf-8')
    orig = text
    if 'style' in opts and 'style_re' in opts:
        text, n = opts['style_re'].subn(opts['style'], text, count=1)
        if n == 0:
            print(f'STYLE NOT FOUND {rel}')
    if 'body_data' in opts:
        def repl(m):
            attrs = opts['body_data']
            return f'<body{m.group(1)} data-redirect-url="{attrs["redirect_url"]}" data-redirect-timeout="{attrs["redirect_timeout"]}">'
        text, n = re.subn(r'<body(\s*?)>', repl, text, count=1)
        if n == 0:
            print(f'BODY NOT FOUND {rel}')
    if 'script' in opts and 'script_re' in opts:
        text, n = opts['script_re'].subn(opts['script'], text, count=1)
        if n == 0:
            print(f'SCRIPT NOT FOUND {rel}')
    if text != orig:
        path.write_text(text, encoding='utf-8')
        print(f'UPDATED {rel}')
print('DONE')
