# SystemTurn Project

Sistema de gestión de turnos con un backend en Spring Boot y una interfaz web en React.

## Estructura principal

- `backend/turnos-api`: API REST desarrollada con Java y Spring Boot.
- `frontend/frontend-react/react-client`: aplicación web desarrollada con React y Vite.

## Vista del asesor

La vista del asesor permite:

- Consultar los turnos registrados.
- Ver el siguiente turno pendiente.
- Llamar al siguiente turno.
- Consultar la hora de solicitud y atención.
- Buscar turnos por número.
- Filtrar turnos por estado.
- Mostrar estados de carga, error y fila vacía.

La interfaz está en:

```text
frontend/frontend-react/react-client/src/views/AsesorView.jsx
```

La comunicación con el backend está centralizada en:

```text
frontend/frontend-react/react-client/src/services/turnosService.js
```

## Tecnologías

- React y Vite
- Java y Spring Boot
- H2 (base de datos en archivo), Spring Data JPA y Flyway
- Maven
- Vitest (pruebas del frontend)

## Ejecutar el proyecto

### Backend

En una terminal:

```powershell
cd backend\turnos-api
.\mvnw.cmd spring-boot:run
```

El backend se ejecuta en `http://localhost:8080`. Los turnos se guardan en una base de datos H2 en archivo (por defecto `./data/turnos`, carpeta ignorada por git), así que sobreviven a los reinicios. Cada día la fila empieza vacía y la numeración vuelve a `T-001` (zona horaria `America/Bogota`).

Para guardar la base de datos en otra ruta, define `TURNOS_DB_PATH` antes de iniciar (sin extensión y con `/`):

```powershell
$env:TURNOS_DB_PATH = "C:/datos/turnos"
.\mvnw.cmd spring-boot:run
```

Para cargar la fila de ejemplo (11 turnos con fecha de hoy) usa el perfil `demo`:

```powershell
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=demo"
```

Sin ese perfil la base de datos arranca vacía. Con el perfil, si hoy ya hay turnos no se vuelven a cargar.

### Frontend

En otra terminal:

```powershell
cd frontend\frontend-react\react-client
npm install
npm run dev
```

La aplicación se abre normalmente en `http://localhost:5173`.

## Comunicación entre frontend y backend

El frontend utiliza `fetch` para consumir estos endpoints (base `/api/v1/turnos`):

```text
POST  /api/v1/turnos                 Solicitar un turno (201)
PATCH /api/v1/turnos/{id}/cancelar   Cancelar un turno pendiente del día
GET   /api/v1/turnos/dashboard       Métricas, siguiente turno y lista del día
PATCH /api/v1/turnos/siguiente       Llamar al siguiente turno
```

Errores: `404 FILA_VACIA` / `TURNO_NO_ENCONTRADO`, `400 OPERACION_INVALIDA` y `409 CONFLICTO` (el turno cambió mientras se procesaba; hay que actualizar e intentar de nuevo).

Vite redirige las solicitudes que comienzan con `/api` hacia Spring Boot en el puerto `8080`.

El backend decide cuál es el siguiente turno. React muestra la información y actualiza la pantalla después de cada operación.

La descripción detallada (reglas de negocio, contrato, configuración y brechas conocidas) está en la carpeta [`docs/`](docs/README.md), en especial [`docs/01-current-state.md`](docs/01-current-state.md).

## Validación

Frontend, desde `frontend\frontend-react\react-client`:

```powershell
npm test
npm run lint
npm run build
```

Backend, desde `backend\turnos-api`:

```powershell
.\mvnw.cmd test
```

Estos comandos ejecutan las pruebas, validan el código y comprueban que la aplicación pueda compilarse correctamente.


<img width="1536" height="1024" alt="imagen" src="https://github.com/user-attachments/assets/ceab91b4-187f-4e29-a54a-0593e1299aa2" />



<img width="1337" height="852" alt="imagen" src="https://github.com/user-attachments/assets/f5d759e3-9746-480a-9523-f1c1d1c686c6" />
