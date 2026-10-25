-- One database per service (no service reads another service's database).
-- Local development credentials only; real ones come from Secrets Manager.
CREATE USER user_service WITH PASSWORD 'user_service';
CREATE DATABASE user_service OWNER user_service;

CREATE USER keycloak WITH PASSWORD 'keycloak';
CREATE DATABASE keycloak OWNER keycloak;
