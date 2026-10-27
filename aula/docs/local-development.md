# Desarrollo local

## Arranque local con XAMPP

Requisitos: Git, JDK 21 y MySQL iniciado desde XAMPP. Crea la base de datos en
phpMyAdmin y, desde la carpeta `aula`, ejecuta:

```cmd
run-local.cmd
```

El perfil `local` conecta a `localhost:3306` como `root` sin contraseña y usa la base
`bustraker` de forma predeterminada. Para usar otro nombre, define `DB_NAME` con ese
nombre en `.env` o antes de ejecutar el script. Por ejemplo, cambia `DB_NAME` en
`.env` al nombre que creaste en phpMyAdmin. Para usar `.env`, cópialo primero desde
`.env.example`; está excluido de Git. La aplicación conserva MySQL; no usa una base
H2.
Abre `http://localhost:3465`.

Para habilitar un proveedor OAuth, copia `.env.example` como `.env` dentro de `aula`
y completa únicamente las credenciales necesarias. El archivo se carga en el perfil
`local` y está excluido de Git. Registra en el proveedor la URL de callback local:
`http://localhost:3465/login/oauth2/code/github` (sustituye `github` por el proveedor
configurado).
Sin credenciales válidas, esos botones no aparecen en el formulario de acceso.

Para la inicialización local y los datos sintéticos, consulta
[`production-setup.md`](production-setup.md). El perfil de producción y Render no
dependen de `DB_NAME` ni se modifican con este ajuste.
