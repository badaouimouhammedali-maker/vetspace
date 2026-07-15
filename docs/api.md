# VetSpace API

## Overview

The backend exposes a small initial surface for health checks and local development.

## Endpoints

### Health
- `GET /api/ping`
  - Returns `{"status":"ok"}`
- `GET /actuator/health`
  - Returns application health status

### Swagger UI
- `GET /swagger-ui/index.html` (enabled in the `dev` profile)
- `GET /v3/api-docs` (enabled in the `dev` profile)

## Environment variables

The service reads configuration from environment variables and `.env` files. The main keys are:

- `DB_URL`, `DB_USER`, `DB_PASSWORD`
- `JWT_SECRET`
- `CORS_ALLOWED_ORIGINS`
- `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD`, `MAIL_FROM`
- `MEDIA_ENDPOINT`, `MEDIA_BUCKET`, `MEDIA_ACCESS_KEY`, `MEDIA_SECRET_KEY`, `MEDIA_PUBLIC_BASE_URL`
- `RECAPTCHA_SECRET`

## Local development

1. Start infrastructure services:
   - `docker compose up -d`
2. Run the backend:
   - `cd backend`
   - `./mvnw spring-boot:run -Dspring-boot.run.profiles=dev`
3. Test the endpoints:
   - `curl http://localhost:8080/api/ping`
   - `curl http://localhost:8080/actuator/health`
