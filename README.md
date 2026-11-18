# Distribución de pedidos para heladerías — Grupo 119 (3K3B, UTN FRC)

Backend de microservicios (Java 21, Spring Boot 3.3, Spring Cloud Gateway, PostgreSQL, Keycloak).

| Módulo | Puerto | Estado |
|---|---|---|
| `api-gateway` | 8080 | implementado |
| `ms-pedidos` | 8082 | implementado |
| `ms-camioneros` | 8083 | implementado |
| `ms-logistica` | 8084 | implementado (planificación, hojas de ruta y cierre) |
| PostgreSQL | 5432 | docker-compose |
| Keycloak | 8081 | docker-compose |
| OSRM (opcional local) | 5000 | `docker-compose.osrm.yml` / `--profile osrm` |

## Arranque

```bash
# 1) Infraestructura (PostgreSQL + Keycloak con el realm importado)
docker compose up -d

# 1b) (Opcional) Levantar también OSRM local una vez procesado osrm-data/
docker compose -f docker-compose.osrm.yml up -d
# o integrado: docker compose --profile osrm up -d

# 2a) Microservicios en local (una terminal por servicio)
mvn -pl ms-pedidos spring-boot:run
mvn -pl ms-camioneros spring-boot:run
mvn -pl ms-logistica spring-boot:run
mvn -pl api-gateway spring-boot:run

# 2b) ...o todo en contenedores
docker compose --profile apps up -d --build
```

Los valores por defecto de `application.yml` coinciden con `.env`, así que en local no hace falta exportar variables.
Variables soportadas: `DB_URL`, `DB_USER`, `DB_PASSWORD`, `KEYCLOAK_ISSUER_URI`, `KEYCLOAK_JWK_SET_URI`, `SERVER_PORT`
(y `MS_PEDIDOS_URL`, `MS_CAMIONEROS_URL`, `MS_LOGISTICA_URL` en el gateway).
Variables propias de `ms-logistica`: ver la sección *ms-logistica* más abajo.

> `KEYCLOAK_ISSUER_URI` debe ser la URL con la que el cliente llega a Keycloak (`http://localhost:8081/...`), porque
> es lo que figura en el claim `iss`. Dentro de Docker las claves se leen de `http://keycloak:8080/...` (`KEYCLOAK_JWK_SET_URI`).

## Usuarios de prueba (realm `heladeria`)

| Usuario | Password | Rol |
|---|---|---|
| `admin` | `admin123` | Administrador |
| `camionero` | `camionero123` | Camionero |
| cliente `ms-logistica` (client credentials, secret `ms-logistica-secret`) | — | ServicioInterno |

Token (cliente público `heladeria-api`):

```bash
curl -s -X POST http://localhost:8081/realms/heladeria/protocol/openid-connect/token \
  -d grant_type=password -d client_id=heladeria-api -d username=admin -d password=admin123
```

Swagger UI unificado: <http://localhost:8080/swagger-ui.html> (botón *Authorize* → pegar el `access_token`).

## Endpoints y roles (según el contrato)

El gateway aplica estos permisos y cada microservicio los vuelve a validar. Todo lo demás bajo `/v1/**` queda denegado.
Sin token / token inválido → **401**; token válido sin el rol → **403**.

| Servicio | Método | Endpoint | Roles |
|---|---|---|---|
| ms-pedidos | POST | `/v1/pedidos` | Administrador |
| ms-pedidos | GET | `/v1/pedidos/regiones/{idRegion}/cola` | Administrador, ServicioInterno |
| ms-pedidos | GET | `/v1/pedidos/{id}` | Administrador, Camionero |
| ms-pedidos | PUT | `/v1/pedidos/{id}/estado` | Administrador, ServicioInterno |
| ms-pedidos | PUT | `/v1/pedidos/{id}/reencolado` *(interno, no expuesto en gateway)* | ServicioInterno |
| ms-pedidos | GET | `/v1/regiones` | Administrador |
| ms-camioneros | POST | `/v1/camioneros` | Administrador |
| ms-camioneros | GET | `/v1/camioneros/siguiente` | ServicioInterno |
| ms-camioneros | PUT | `/v1/camioneros/{id}/estado` | Administrador, ServicioInterno |
| ms-camioneros | GET | `/v1/camiones/{id}` | ServicioInterno |
| ms-logistica | POST | `/v1/viajes/planificar/regiones/{idRegion}` | Administrador |
| ms-logistica | GET | `/v1/viajes` | Administrador |
| ms-logistica | GET | `/v1/viajes/{id}` | Administrador, Camionero |
| ms-logistica | PUT | `/v1/viajes/{id}/cierre` | Administrador |

## Reglas implementadas

- **RN-01** (ms-pedidos): pedido de 40 a 80 tachos, si no → `400`.
- **RN-04** (ms-camioneros): `GET /v1/camioneros/siguiente` devuelve el `DISPONIBLE` con contrato activo y menor
  `ultima_actualizacion_cola` (FIFO global). Sin candidatos → `404`. Al volver a `DISPONIBLE` el camionero pasa al final de la fila.
- **Estados de pedido**: `PENDIENTE → ASIGNADO → EN_TRANSITO → ENTREGADO`. `ASIGNADO → PENDIENTE` es el rollback de la
  planificación. `NO_ENTREGADO` reencola: el pedido queda `PENDIENTE` conservando su posición original en la cola.
  Si una heladería tiene coordenadas inválidas durante la planificación (RN-05), el pedido se rechaza, sigue `PENDIENTE` y se manda al final de la cola regional (`PUT /v1/pedidos/{id}/reencolado`, actualizando `ultima_actualizacion_cola`).
  Transición inválida → `409`.
- **Estados de camionero**: `DISPONIBLE ⇄ EN_VIAJE`. Repetir el mismo estado → `409`.
- Capacidad de camión: 1 a 500 tachos. DNI y patente únicos (`409` si se repiten).

## Persistencia

Una sola base PostgreSQL con un esquema por servicio (`pedidos`, `camioneros`, `logistica`); las tablas las crea Hibernate
(`ddl-auto=update`). Al primer arranque, `ms-pedidos` carga 4 regiones (CAPITAL, NOROESTE, ESTE, SUR), ciudades y heladerías
**de ejemplo** (nombres/direcciones inventados, coordenadas aproximadas por ciudad): reemplazarlas por datos reales antes de
probar el ruteo con OSRM.

## ms-logistica (planificación, hojas de ruta y cierre)

Orquesta `ms-pedidos`, `ms-camioneros` y OSRM (cálculo de rutas). Su esquema propio es `logistica` (tablas `viaje` y `parada_viaje`);
`id_pedido`, `id_camionero` e `id_region` son referencias lógicas, sin FK ni JOIN hacia otros servicios.
Llama a los otros servicios **directamente** (no por el gateway) con un token *client credentials* del cliente `ms-logistica`
(rol `ServicioInterno`) y solo usa endpoints que ese rol puede invocar: `GET /v1/camioneros/siguiente`,
`GET /v1/pedidos/regiones/{id}/cola`, `PUT /v1/pedidos/{id}/estado`, `PUT /v1/pedidos/{id}/reencolado` y `PUT /v1/camioneros/{id}/estado`.

### Variables de entorno

| Variable | Default | Uso |
|---|---|---|
| `OSRM_URL` | `https://router.project-osrm.org` | Servidor OSRM. **No requiere API key ni tarjeta.** Por defecto, el servidor público de demostración (necesita internet y uso moderado); se puede apuntar a uno propio. Si no responde, planificar da `503`/`502`. |
| `MS_PEDIDOS_URL` / `MS_CAMIONEROS_URL` | `http://localhost:8082` / `:8083` | URLs directas de los servicios. |
| `KEYCLOAK_TOKEN_URI` | `http://localhost:8081/realms/heladeria/protocol/openid-connect/token` | Token de servicio. En Docker: `http://keycloak:8080/...`. |
| `KEYCLOAK_CLIENT_ID` / `KEYCLOAK_CLIENT_SECRET` | `ms-logistica` / `ms-logistica-secret` | Credenciales del cliente confidencial del realm. |
| `FABRICA_LAT` / `FABRICA_LNG` | `-31.44276586787743` / `-64.19411487348728` | Origen de la ruta: planta en la **UTN FRC** (Ciudad Universitaria, Córdoba). |
| `PISO_MINIMO_TACHOS` | `280` | Piso acumulado para despachar. |
| `TIEMPO_DESCARGA_MIN` | `20` | Minutos de descarga por parada. |
| `LIMITE_VIAJE_SEGUNDOS` | `36000` | Límite de cadena de frío (10 h). |
| `DESCARGA_INCLUYE_ULTIMA_PARADA` | `true` | Lectura de RN-03 (ver abajo). |
| `INCLUIR_VUELTA_A_FABRICA` | `false` | Si la ruta/tiempo cuentan el regreso a la fábrica. |

### Algoritmo (`POST /v1/viajes/planificar/regiones/{idRegion}`)

1. Próximo camionero FIFO (RN-04). Si no hay → `409`, sin cambios en ningún servicio.
2. Cola PENDIENTE de la región, en orden FIFO estricto (sin reordenar: TSP fuera de alcance).
3. Por cada pedido N, en memoria:
   - **RN-05 (coordenadas inválidas)**: si la heladería no tiene coordenadas válidas (`null`, fuera de rango o `0,0`), el pedido se **rechaza y se salta**: no suma tachos ni tiempo, no entra al viaje ni a la URL, no corta el armado y se continúa con el siguiente pedido.
   - **RN-02**: si `acumulado + tachos > capacidad` se corta; si no, se consulta OSRM con las paradas incluidas más N y **RN-03** si `conducción + descargas > 36.000 s` se corta. El pedido N nunca se toca en `ms-pedidos` (queda PENDIENTE) y el viaje se cierra con los anteriores incluidos. Se hace **una llamada a OSRM por pedido candidato** válido (impacto en cuota y latencia).
4. Si el acumulado final es menor al piso de 280 tachos → `422` (mencionando en el detalle los rechazados por RN-05 si los hubo), sin tocar el camionero ni los pedidos válidos; los pedidos rechazados por RN-05 igual se mandan al final de la cola (best-effort).
5. Se guarda el viaje `PLANIFICADO` con sus paradas y luego en `ms-pedidos` cada pedido incluido pasa a `ASIGNADO` y a `EN_TRANSITO`, y el camionero a `EN_VIAJE`.
   Si falla una llamada remota se compensa (best-effort): `ASIGNADO → PENDIENTE`, `EN_TRANSITO → NO_ENTREGADO` (que `ms-pedidos` reencola como PENDIENTE),
   camionero a `DISPONIBLE` y el viaje se elimina. La respuesta es `502/503` con el detalle.
6. Confirmado el viaje, los pedidos rechazados por RN-05 se mandan al final de la cola regional (`PUT /v1/pedidos/{id}/reencolado`, best-effort: si falla se registra un `warn` y no se rompe la planificación).
7. **RN-05**: `url_google_maps = https://www.google.com/maps/dir//lat,lng/lat,lng/...` (origen vacío → "Tu ubicación").

Errores de OSRM → `502` (respuesta inválida) o `503` (sin conexión o timeout), sin estado a medias.

### Decisión sobre la fórmula de RN-03 (confirmada por el equipo)

El contrato dice en RN-03 `Tiempo Total = Tiempo Viaje + (N-1) * 20 min`, pero su resumen y la guía de desarrollo v12 hablan de
"20 minutos de descarga por cada parada" (es decir `N * 20`).

- **Implementado por defecto (decisión confirmada): `N * 20 min`** (respaldado por dos fuentes y más conservador para la cadena de frío).
- **Lectura alternativa** (fórmula literal de RN-03): `DESCARGA_INCLUYE_ULTIMA_PARADA=false` → `(N-1) * 20 min`.

### Cierre (`PUT /v1/viajes/{id}/cierre`)

Body: `{"resultados":[{"idPedido":10,"estado":"ENTREGADO"},{"idPedido":11,"estado":"NO_ENTREGADO"}]}`. Deben informarse exactamente
las paradas del viaje (`400` si faltan, sobran o se repiten) y el viaje debe estar `PLANIFICADO` (`409` si ya está `FINALIZADO`).
Actualiza cada pedido (NO_ENTREGADO lo reencola), pone al camionero `DISPONIBLE` (pasa al final de la cola FIFO) y finaliza el viaje.
*Limitación conocida (aceptada por el equipo):* si `ms-pedidos` falla a mitad del cierre, los pedidos ya actualizados no se revierten (no hay transición válida
hacia atrás desde ENTREGADO / NO_ENTREGADO) y reintentar dará `409` en ellos; el estado local del viaje se guarda recién al final, se reporta el error y hay que resolverlo a mano.

## Cómo probar el flujo completo

**Modo híbrido** (infra en Docker, servicios desde el IDE o con `mvn spring-boot:run`) o **modo integración** (todo en Docker):

```bash
docker compose --profile apps up -d --build
```

Importar en Postman `postman/Logistica-Helados-TPI.postman_collection.json` y `postman/Local-Env.postman_environment.json` y correr la
colección completa con el Runner (01 → 01b → 02 → 03 → 04 → 05). Cubre ESC-01 a ESC-06 y CV-01 a CV-07 con `pm.test`. Las planificaciones reales requieren que `ms-logistica` llegue a OSRM (internet si se usa el servidor público).

**La colección es independiente del `DataSeeder`:** la carpeta `01b` llama a los endpoints de soporte a pruebas
`POST /v1/viajes/admin/reset`, `POST /v1/pedidos/admin/reset?resembrar=false` y `POST /v1/camioneros/admin/reset?resembrar=false`
(solo rol `Administrador`), que vacían viajes, colas regionales y cola FIFO global **conservando regiones, ciudades y heladerías**.
Así los escenarios son deterministas aunque cambien los datos de demo, y la colección se puede correr varias veces seguidas sin
`docker compose down -v`. La carpeta `05` (opcional) llama a los mismos endpoints con `resembrar=true` y deja otra vez cargados los
pedidos y camioneros de demo para seguir probando en Swagger. Los endpoints se deshabilitan con `DATOS_PRUEBA_RESET_HABILITADO=false`.

## Tests

```bash
mvn test
```

- `PedidoServiceTest` (transiciones de estado y RN-01), `CamioneroServiceTest` (cola FIFO y estados): JUnit 5 + Mockito, sin Spring ni base.
- `PlanificadorServiceTest`, `ViajeServiceTest`, `OsrmRoutesClientTest` (ms-logistica): planificador con clientes y OSRM mockeados.
- `ViajeControllerSecurityTest` (ms-logistica) y `SecurityConfigTest` (api-gateway): `@WebMvcTest` / `@WebFluxTest` + `spring-security-test` (401/403/200 por rol) sin base de datos.

## Divergencias entre contrato, guía v12 y proyecto

La guía es genérica; el proyecto sigue el contrato y el código existente. Diferencias detectadas:

| Tema | Guía v12 | Proyecto (vigente) |
|---|---|---|
| Puertos | pedidos 8083, camioneros 8082, logística 8081, Keycloak 8080 | gateway 8080, Keycloak 8081, pedidos 8082, camioneros 8083, logística 8084 |
| Roles | `ROLE_ADMIN_LOGISTICA`, `ROLE_CHOFER` | `Administrador`, `Camionero`, `ServicioInterno` |
| Base de datos | 3 bases separadas | 1 PostgreSQL con un esquema por servicio (decisión confirmada con la cátedra) |
| Ruteo | Google Maps (`MAPS_API_KEY`) | OSRM (sin API key, `OSRM_URL`), alternativa indicada por la cátedra porque Google exige tarjeta. El enlace `url_google_maps` se sigue generando (es solo un link) |
| Numeración | "RN-04" = piso de 280 tachos | RN-04 = asignación FIFO; el piso es una regla adicional configurable |
| Dockerfile | uno por módulo | uno genérico con `ARG MODULE` |
| RN-03 | — | ver sección anterior |
