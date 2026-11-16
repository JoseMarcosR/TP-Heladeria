-- Una única base PostgreSQL con aislamiento lógico: un esquema por microservicio.
-- Las tablas las crea cada servicio con Hibernate (ddl-auto=update).
CREATE SCHEMA IF NOT EXISTS pedidos;
CREATE SCHEMA IF NOT EXISTS camioneros;
CREATE SCHEMA IF NOT EXISTS logistica;
