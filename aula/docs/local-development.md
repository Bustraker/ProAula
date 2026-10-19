# Desarrollo local

## Arranque rápido sin servicios externos

Requisitos: Git y JDK 21. Desde la carpeta `aula`, ejecuta:

```cmd
run-demo.cmd
```

El perfil `demo` usa una base H2 en memoria y crea su esquema al iniciar. No requiere
XAMPP, Firebase ni credenciales OAuth. Los datos de la base se pierden al detener la
aplicación. Abre `http://localhost:3465`.

Para habilitar un proveedor OAuth en este perfil, copia `.env.example` como `.env`
dentro de `aula` y completa únicamente las credenciales necesarias. El archivo se
carga al arrancar el perfil demo y está excluido de Git. Registra en el proveedor la
URL de callback local:
`http://localhost:3465/login/oauth2/code/github` (sustituye `github` por el proveedor
configurado).
Sin credenciales válidas, esos botones no aparecen en el formulario de acceso.

## XAMPP

El perfil `local` existente conserva la conexión MySQL a XAMPP. Inicia la base
`bustraker` y usa las instrucciones de [`production-setup.md`](production-setup.md)
para la carga de datos sintéticos. No se cambia el perfil `local` ni la configuración
de producción en Render.
