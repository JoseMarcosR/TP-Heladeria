# Preguntas de Defensa por Criterio — Rúbrica General TPI 2026
**Proyecto:** Distribución de pedidos para heladerías (Grupo 119 — 3K3B, UTN FRC)
---

## 1. `ARQ-01` — Arquitectura y diseño de microservicios (12 pts)
1. ¿Por qué decidieron dividir el sistema específicamente en estos tres microservicios de negocio (`ms-pedidos`, `ms-camioneros` y `ms-logistica`) más el `api-gateway`, y qué responsabilidad de dominio justifica que `ms-logistica` esté separado de `ms-pedidos`?
2. Si `ms-logistica` necesita saber los datos de un pedido y de un camionero para armar un viaje, ¿por qué en su tabla `viaje` y `parada_viaje` guardan solo los IDs (`id_pedido`, `id_camionero`, `id_region`) como referencias lógicas sin usar claves foráneas (`FOREIGN KEY`) ni hacer un `JOIN` en SQL?
3. ¿Qué ventaja arquitectónica les da tener un `api-gateway` adelante de los tres microservicios en vez de que el cliente (Swagger o Postman) llame directamente a los puertos `8082`, `8083` y `8084`?

---

## 2. `SEG-01` — Seguridad y control de acceso (8 pts)
1. ¿Cuál es la diferencia en su sistema entre un error `401 Unauthorized` y un `403 Forbidden`, y en qué componente se valida el token JWT (solo en el `api-gateway`, solo en cada microservicio, o en ambos)?
2. Tienen tres roles (`Administrador`, `Camionero` y `ServicioInterno`): ¿cómo se autentica un usuario humano (Admin o Camionero) frente a Keycloak a diferencia de cómo se autentica `ms-logistica` para hablar con `ms-pedidos` y `ms-camioneros`?
3. ¿Por qué el endpoint `GET /v1/camioneros/siguiente` o `PUT /v1/pedidos/{id}/reencolado` tienen restringido el acceso únicamente al rol `ServicioInterno` y qué pasaría si un `Administrador` intentara llamarlos?

---

## 3. `TEC-01` — Calidad técnica e implementación (10 pts)
1. Dentro de cualquiera de sus microservicios tienen las capas `controller`, `service`, `repository`, `model` y `dto`: ¿por qué el `Controller` recibe y devuelve objetos `DTO` (`Request`/`Response`) en lugar de recibir o devolver directamente las entidades `@Entity` de la base de datos?
2. ¿Cómo manejan los errores en el backend para que, cuando se viola una regla de negocio o no existe un ID, la API devuelva un JSON prolijo con el código HTTP exacto (`400`, `404`, `409`, `422`) sin llenar los `Controller` de bloques `try / catch`?
3. ¿Qué diferencia hay entre los tests unitarios que hicieron con Mockito (como `PlanificadorServiceTest`) y una prueba de integración, y por qué fue útil mockear a OSRM y a los clientes HTTP para testear el algoritmo de planificación?

---

## 4. `INF-01` — Persistencia, reutilización y despliegue (10 pts)
1. En una arquitectura de microservicios cada servicio debe ser dueño de sus datos: ¿cómo garantizaron ese aislamiento de datos usando un único motor contenedor de PostgreSQL en Docker Compose?
2. ¿Cómo hacen para que el sistema sea reproducible en cualquier computadora sin tener que tocar el código fuente si cambian las URLs de Keycloak, de la base de datos o el servidor de OSRM entre el entorno local y Docker?
3. Si levantan el proyecto desde cero en una máquina limpia (`docker compose down -v`), ¿cómo se crean los esquemas, las tablas, el realm de Keycloak y los datos iniciales para que la demo funcione de inmediato sin cargar SQL a mano?

---

## 5. `NEG-01` — Proceso de negocio central del MVP (15 pts)
1. Explíquenme de punta a punta cuál es el proceso central de negocio del sistema: ¿cuál es el disparador, qué transformaciones ocurren en el medio entre los tres servicios y cuál es el resultado final útil para la fábrica de helados?
2. ¿Por qué el sistema no es simplemente "tres ABM/CRUD de pedidos, camioneros y viajes", sino un proceso coordinado, y qué estados atraviesa un pedido desde que una heladería lo solicita hasta que el camión vuelve a la fábrica?
3. Cuando termina el recorrido físico del camión y se ejecuta el cierre del viaje (`PUT /v1/viajes/{id}/cierre`), ¿qué efectos secundarios dispara ese cierre sobre las colas de `ms-pedidos` y `ms-camioneros` para el próximo ciclo?

---

## 6. `NEG-02` — Reglas de negocio y procesamiento (10 pts)
1. ¿Cómo conviven las cuatro colas regionales FIFO de pedidos con la única cola global FIFO de camioneros, y por qué decidieron persistir el orden de las colas en base de datos mediante timestamps (`ultimaActualizacionCola`) en lugar de usar una cola en memoria RAM de Java?
2. Durante la planificación de una región, ¿qué condiciones exactas hacen que un pedido quede afuera del viaje (corte por capacidad RN-02 o por límite de 10 horas de cadena de frío RN-03), y qué pasa si la suma total de tachos que entraron en el camión no llega al piso mínimo de 280 tachos?
3. ¿Por qué un pedido no entregado en el cierre (`NO_ENTREGADO`) o que no entró en el camión conserva su primer lugar en la cola regional, mientras que un pedido con coordenadas inválidas (RN-05) se manda al final de la cola?

---

## 7. `INT-01` — Integración y colaboración entre servicios (12 pts)
1. Cuando `ms-logistica` planifica un viaje, ¿se comunica con `ms-pedidos` y `ms-camioneros` pasando por el `api-gateway` o los llama directamente, y cómo hace con OSRM para calcular los tiempos reales de conducción?
2. En un sistema distribuido no hay una única transacción de base de datos (`@Transactional`) que abarque a los tres microservicios: ¿qué pasa si `ms-logistica` ya puso los pedidos en `ASIGNADO` / `EN_TRANSITO` en `ms-pedidos`, pero justo se cae `ms-camioneros` al intentar pasar al chofer a `EN_VIAJE`?
3. ¿Qué ocurre si el servidor externo de OSRM se cae o tarda demasiado en responder (timeout) en medio de una planificación: qué código HTTP devuelve su API y en qué estado quedan los pedidos y el camionero?

---

## 8. `ALC-01` — Cumplimiento del alcance comprometido (8 pts)
1. Respecto al contrato y la guía original de la cátedra, ustedes documentaron algunas decisiones acordadas (como usar OSRM en vez de la API paga de Google Maps, o usar 1 instancia PostgreSQL con 3 esquemas en vez de 3 servidores PostgreSQL): ¿por qué tomaron esas decisiones y cómo cumplen igualmente con el objetivo funcional?
2. En la regla RN-03 del cálculo de las 10 horas había dos interpretaciones posibles sobre los 20 minutos de descarga (`N * 20 min` vs `(N-1) * 20 min`): ¿cuál adoptaron por defecto, por qué, y cómo contemplaron la otra opción?
3. ¿Quedó alguna limitación conocida en el alcance actual (por ejemplo, qué pasa si falla la red justo a la mitad del cierre manual de un viaje) y por qué el equipo decidió aceptarla así?

---

## 9. `DES-01` — Complejidad y desafío asumido (5 pts)
1. ¿Cuál fue el mayor desafío técnico del trabajo práctico al pasar de pensar en un monolito a tener tres microservicios separados que deben mantener consistencia entre sí?
2. Al integrar Keycloak con Docker y el entorno local, tuvieron el desafío de que el usuario pide el token a `localhost:8081` pero dentro de Docker los contenedores ven a Keycloak como `keycloak:8080`: ¿por qué fallaba la validación del token JWT y cómo lo destrabaron separando `issuer-uri` de `jwk-set-uri`?
3. En el algoritmo de planificación, ustedes consultan a OSRM una vez por cada pedido candidato que van sumando al camión: ¿qué compromiso (*trade-off*) asumieron ahí entre exactitud del cálculo de las 10 horas versus latencia/cantidad de llamadas a OSRM?

---

## 10. `PRE-01` — Presentación, demostración y defensa (5 pts)
1. Si tuvieran que mostrarme en vivo en menos de 3 minutos que la regla de capacidad del camión (RN-02) o el piso mínimo de 280 tachos funcionan de verdad, ¿qué secuencia exacta de endpoints ejecutarían ahora mismo?
2. Si le pregunto a un integrante del equipo que no haya programado puntualmente `ms-logistica`, ¿me puede explicar con sus palabras cómo sabe `ms-logistica` cuál es el próximo camionero que tiene que salir?
3. Muéstrenme en vivo (en Swagger, Postman o en la base de datos) qué cambia antes y después de cerrar un viaje cuando uno de los pedidos se reporta como `NO_ENTREGADO`.

---

## 11. `API-01` — Colección de llamadas a endpoints y pruebas (5 pts)
1. ¿Cómo está organizada su colección de Postman para que un docente pueda probar tanto el camino feliz como los casos de error (`400`, `401`, `403`, `409`, `422`) sin tener que copiar y pegar tokens ni IDs manualmente entre requests?
2. ¿Qué validaciones automáticas (`pm.test`) incluyeron dentro de los scripts de Postman para comprobar que un endpoint no solo devolvió `200 OK` o `201 Created`, sino que realmente cumplió la regla de negocio esperada?
3. ¿Qué diferencia de propósito tienen en su proyecto el Swagger UI unificado en el puerto `8080` del `api-gateway` frente a la colección exportada de Postman?

---

## 12. `DOC-01` — Apreciación docente global (5 pts)
1. ¿Cómo se dividieron el trabajo entre los integrantes del grupo para que todos entendieran la arquitectura completa y no quedara cada uno aislado solo en su microservicio?
2. ¿Qué cambio o evolución importante tuvo el proyecto desde la primera versión que plantearon hasta la versión final que están entregando hoy (por ejemplo, en el manejo de las colas, el reencolado por coordenadas inválidas o las semillas de datos)?
3. Si hoy tuvieran dos semanas más para evolucionar este MVP hacia un sistema de producción real, ¿qué sería lo primero que mejorarían en cuanto a resiliencia, colas o transacciones distribuidas?