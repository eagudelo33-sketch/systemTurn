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
- PostgreSQL (producción), Spring Data JPA y Flyway
- H2 en archivo (solo desarrollo local, perfil `dev`)
- Maven
- Vitest (pruebas del frontend) y Testcontainers (pruebas del backend contra PostgreSQL)

## Ejecutar el proyecto

### Backend

El backend guarda los turnos en **PostgreSQL**. Para desarrollar sin Docker existe el perfil `dev`, que usa una base H2 en archivo. Cada día la fila empieza vacía y la numeración vuelve a `T-001` (zona horaria `America/Bogota`).

#### Desarrollo local (H2, sin Docker)

```powershell
cd backend\turnos-api
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=dev"
```

El backend se ejecuta en `http://localhost:8080`. La base se guarda en `./data/turnos` (carpeta ignorada por git), así que los turnos sobreviven a los reinicios. Para usar otra ruta, define `TURNOS_DB_PATH` antes de iniciar (sin extensión y con `/`):

```powershell
$env:TURNOS_DB_PATH = "C:/datos/turnos"
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=dev"
```

Para cargar la fila de ejemplo (11 turnos con fecha de hoy) agrega el perfil `demo` **junto con** `dev`:

```powershell
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=dev,demo"
```

Sin `demo` la base arranca vacía. Con `demo`, si hoy ya hay turnos no se vuelven a cargar.

> Los datos de H2 de versiones anteriores no se migran a PostgreSQL (eran datos de prueba).

#### Con PostgreSQL

Sin el perfil `dev`, el backend exige una base PostgreSQL configurada por variables de entorno. No hay ninguna URL, usuario ni contraseña en el repositorio.

```powershell
$env:SPRING_DATASOURCE_URL = "jdbc:postgresql://localhost:5432/turnos"
$env:SPRING_DATASOURCE_USERNAME = "turnos"
$env:SPRING_DATASOURCE_PASSWORD = "<tu-contraseña>"
.\mvnw.cmd spring-boot:run
```

Flyway crea el esquema (`V1`) al arrancar y Hibernate solo lo valida.

#### Variables de entorno

| Variable | Valor por defecto | Para qué sirve |
|----------|-------------------|----------------|
| `SPRING_DATASOURCE_URL` | sin valor (obligatoria fuera de `dev`) | URL JDBC de PostgreSQL. Para TLS (`sslmode`) y `socketTimeout` agrégalos a la URL |
| `SPRING_DATASOURCE_USERNAME` | sin valor | Usuario de la base de datos |
| `SPRING_DATASOURCE_PASSWORD` | sin valor | Contraseña de la base de datos |
| `TURNOS_DB_POOL_SIZE` | `10` | Tamaño máximo del pool de conexiones. Tamaño × número de instancias debe quedar por debajo de `max_connections` de la base |
| `TURNOS_DB_CONNECT_RETRIES` | `10` | Reintentos de Flyway al conectar durante el arranque |
| `TURNOS_DB_PATH` | `./data/turnos` | Ruta del archivo H2. **Solo** con el perfil `dev` |
| `turnos.timezone` (`TURNOS_TIMEZONE`) | `America/Bogota` | Zona horaria que define "hoy" para la fila |
| `TURNOS_ALLOWED_ORIGINS` | `http://localhost:4200,http://localhost:5173,http://127.0.0.1:5173` | Orígenes CORS permitidos, separados por coma |
| `SPRING_PROFILES_ACTIVE` | sin valor | Perfiles activos (`dev`, `demo`, `test`). No uses `test` ni `demo` en producción |

#### Protecciones al arrancar

El backend se niega a arrancar (con un mensaje que dice qué corregir) en estos casos:

| Situación | Resultado |
|-----------|-----------|
| No hay `SPRING_DATASOURCE_URL` y no está el perfil `dev` | Falla: no se crea ninguna base embebida en silencio |
| La URL es de H2 (`jdbc:h2:`) y no está activo `dev` ni `test` | Falla |
| El perfil `demo` está activo sin `dev` | Falla: `demo` solo se permite con `dev` |

#### Salud y apagado

- Solo se expone el endpoint `health`. Para el balanceador (ALB) usa `/actuator/health/readiness`; `/actuator/health/liveness` es la sonda de vida. Ninguna de las dos incluye la base de datos, así que siguen en `UP` aunque la base esté caída. `/actuator/health` sí refleja el estado de la base, para quien opera el servicio.
- El apagado es ordenado (`server.shutdown=graceful`, hasta 25 s por fase): las solicitudes en curso terminan antes de cerrar.

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

Si la base de datos no responde, la API contesta rápido (el pool espera como máximo 5 s) con `503 SERVICIO_NO_DISPONIBLE` y un mensaje en español, sin detalles internos. `turnosService.js` lo convierte en un error con ese mensaje (y `code` `SERVICIO_NO_DISPONIBLE`, sin marcarlo como actualizable): actualizar no lo arregla, hay que esperar a que el servicio vuelva. El manejo en las vistas queda para el cambio `client-identity`.

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

Las pruebas del backend (147) incluyen un grupo que corre contra un PostgreSQL real con Testcontainers (clases `*PostgresTest`, imagen `postgres:17.10`). Ese grupo necesita Docker:

| Situación | Comando | Resultado |
|-----------|---------|-----------|
| Docker disponible | `.\mvnw.cmd test` | Corre todo, incluido PostgreSQL |
| Sin Docker (desarrollo) | `.\mvnw.cmd test` | Las pruebas de PostgreSQL se omiten; el resto pasa sin Docker |
| Sin Docker, exigiéndolo (CI) | `.\mvnw.cmd test "-DrequireDocker=true"` | **Falla** con un mensaje que menciona `requireDocker`, en lugar de omitir |

Estos comandos ejecutan las pruebas, validan el código y comprueban que la aplicación pueda compilarse correctamente.


<img width="1536" height="1024" alt="imagen" src="https://github.com/user-attachments/assets/ceab91b4-187f-4e29-a54a-0593e1299aa2" />



<img width="1337" height="852" alt="imagen" src="https://github.com/user-attachments/assets/f5d759e3-9746-480a-9523-f1c1d1c686c6" />
