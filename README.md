# ProAula

ProAula es una aplicación web desarrollada con Java y Spring Boot para gestionar información de transporte público, rutas y usuarios en el contexto de Cartagena. Su objetivo es permitir a los usuarios consultar rutas, buscar barrios y viajar por el sistema, mientras que el administrador puede administrar buses, rutas, usuarios y mensajes de contacto desde un panel de control.

## ¿Qué hace esta aplicación?

La plataforma combina un portal público con un área administrativa. El usuario puede registrarse e iniciar sesión para acceder a funciones personalizadas, mientras el administrador puede supervisar y gestionar el contenido del sistema.

## Funcionalidades principales

### 1. Autenticación y autorización
- Registro de usuarios con validación de datos.
- Inicio de sesión con credenciales locales.
- Soporte para inicio de sesión con OAuth2 (Google y GitHub).
- Roles diferenciados:
  - Usuario normal
  - Administrador
- Validación de código de administrador para cuentas del tipo admin.

### 2. Panel de usuario
- Dashboard personalizado para usuarios autenticados.
- Visualización de rutas populares y buses activos.
- Acceso a información relevante del perfil del usuario.
- Secciones para consultar rutas, historial y contacto.

### 3. Gestión de rutas
- Registro de nuevas rutas.
- Edición y eliminación de rutas existentes.
- Visualización de detalle de una ruta.
- Búsqueda de rutas por nombre.
- Filtro por barrio.
- Carga de barrios relacionados con cada ruta.

### 4. Gestión de buses
- Registro de buses asociados a rutas.
- Actualización de información de buses.
- Eliminación de buses.
- Consulta de buses activos y disponibles en el sistema.

### 5. Gestión de barrios y datos geográficos
- Integración con información geográfica de barrios.
- Uso de datos tipo shapefile para representar zonas y ubicaciones.
- Relación entre rutas y barrios para mejorar la búsqueda y la navegación.

### 6. Administración del sistema
- Panel principal del administrador.
- Conteo de usuarios, rutas, buses y mensajes recibidos.
- Gestión de usuarios.
- Gestión de mensajes de contacto enviados por usuarios o visitantes.
- Administración de la información central del sistema.

### 7. Mensajes de contacto
- Formulario de contacto para usuarios y visitantes.
- Registro de mensajes con nombre, apellido, teléfono, correo y comentario.
- Visualización de mensajes en el panel administrativo.

### 8. Interfaz web con Thymeleaf
- Vistas HTML renderizadas del lado del servidor con Thymeleaf.
- Secciones públicas y privadas.
- Pantallas para inicio, registro, rutas, perfil, contacto y administración.

## Tecnologías usadas

- Java 21
- Spring Boot 3.2.12
- Spring Web
- Spring Data JPA
- Spring Security
- OAuth2 Client
- Thymeleaf
- MySQL
- Maven
- JWT

## Estructura del proyecto

```text
ProAula/
├── aula/
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/
│   │   │   │   └── com/proaula/aula/
│   │   │   │       ├── Controller/
│   │   │   │       ├── Entity/
│   │   │   │       ├── Service/
│   │   │   │       ├── Repository/
│   │   │   │       ├── config/
│   │   │   │       └── dto/
│   │   │   └── resources/
│   │   │       ├── static/
│   │   │       ├── templates/
│   │   │       └── application.properties
│   │   └── test/
│   ├── pom.xml
│   ├── mvnw
│   ├── mvnw.cmd
│   └── Dockerfile
├── Barrios_Ctg/
├── ANALISIS_PROYECTO.md
├── GUIA_RAPIDA.md
├── RESUMEN_EJECUTIVO.md
├── REORGANIZACION_COMPLETADA.md
└── README.md
```

## Requisitos

- Java 21 o superior
- Maven 3.9+
- MySQL 8
- Git

## Configuración rápida

1. Crear una base de datos MySQL llamada `bustraker`.
2. Entrar a la carpeta del proyecto:

```bash
cd aula
```

3. Ejecutar la aplicación:

```bash
./mvnw spring-boot:run
```

En Windows:

```powershell
cd aula
mvnw.cmd spring-boot:run
```

4. Abrir la aplicación en el navegador:

```text
http://localhost:8080
```

## Variables de entorno importantes

El proyecto usa valores por defecto en `application.properties`, pero también admite configuraciones a través de variables de entorno.

Ejemplo:

```bash
export SPRING_DATASOURCE_URL=jdbc:mysql://127.0.0.1:3306/bustraker
export SPRING_DATASOURCE_USERNAME=root
export SPRING_DATASOURCE_PASSWORD=tu_password
export JWT_SECRET=tu_clave_secreta
export GOOGLE_CLIENT_ID=tu_client_id
export GOOGLE_CLIENT_SECRET=tu_client_secret
export GITHUB_CLIENT_ID=tu_client_id
export GITHUB_CLIENT_SECRET=tu_client_secret
```

## Casos de uso principales

### Para usuarios
- Registrarse en la plataforma.
- Iniciar sesión.
- Consultar rutas disponibles.
- Buscar una ruta por nombre o barrio.
- Ver detalle de una ruta.
- Enviar mensajes de contacto.

### Para administradores
- Revisar estadísticas generales del sistema.
- Agregar, editar y eliminar rutas.
- Administrar buses.
- Gestionar usuarios.
- Revisar y responder mensajes recibidos.

## Objetivo del proyecto

ProAula busca facilitar la administración de rutas de transporte y mejorar la experiencia de consulta para usuarios que necesitan información de movilidad en una zona geográfica específica. Además, ofrece una estructura de gestión completa para manejar la operación del sistema desde un entorno web centralizado.

## Nota

Este proyecto está orientado a una aplicación funcional de gestión de transporte con interfaz web, autenticación segura y administración centralizada. La organización de estilos y recursos fue mejorada en una etapa posterior para mejorar compatibilidad y mantenimiento del proyecto.
