# PayPulse Backend

PayPulse is now a backend-only project built around a Spring Boot API in `backend/`. The old React frontend and Node/Express backend have been removed.

## What is included

- Spring Boot 3 API for auth, wallet, transfer, top-up, and webhook routes
- PostgreSQL-backed transactional persistence
- Redis-backed wallet cache with in-memory fallback when Redis is unavailable
- JWT auth, OTP verification, rate limiting, idempotency handling, and cached wallet reads

## Run locally

```bash
cd backend
mvn spring-boot:run
```

The API starts on `http://localhost:3000` by default.

Set `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, and optionally `REDIS_HOST` / `REDIS_PORT` before starting the app, or use `docker-compose.yml`.

Useful endpoints:

- `GET /`
- `GET /api`
- `POST /api/auth/signup`
- `POST /api/auth/login`
- `GET /api/wallet`
- `POST /api/transaction/transfer`
- `POST /api/wallet/topup`
- `POST /api/webhook/payment`

## Test

```bash
cd backend
mvn test
```
