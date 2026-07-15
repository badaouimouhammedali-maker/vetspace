# VetSpace Backend

## Prerequisites

- Java 21
- Docker Desktop
- Maven wrapper included in this repo

## Quick start

1. Copy the environment template:
   - `cp ../.env.example ../.env`
2. Start local services:
   - `docker compose up -d`
3. Run the app:
   - `./mvnw spring-boot:run -Dspring-boot.run.profiles=dev`

## Useful commands

- `./mvnw verify`
- `./mvnw test`
- `curl http://localhost:8080/api/ping`
- `curl http://localhost:8080/actuator/health`

## Notes

- Swagger UI is available in the `dev` profile.
- Security is temporarily permissive for local development and is marked for future auth work.
