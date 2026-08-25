# PayPulse Spring Boot Backend

This directory contains the Spring Boot version of the PayPulse backend.

## Stack

- Spring Boot 3.5
- Spring MVC
- Spring Data JPA
- PostgreSQL
- Redis
- JJWT

## Run

```bash
mvn spring-boot:run
```

Default port: `3000`

## Key environment variables

```env
PORT=3000
DB_URL=jdbc:postgresql://localhost:5432/paypulse
DB_USERNAME=postgres
DB_PASSWORD=postgres
REDIS_HOST=localhost
REDIS_PORT=6379
JWT_SECRET=dev-jwt-secret
OTP_SECRET=dev-otp-secret
PAYMENT_WEBHOOK_SECRET=dev-payment-webhook-secret
CORS_ORIGINS=*
MAINTENANCE_MODE=false
INITIAL_WALLET_BALANCE=500000
```

## API routes

- `POST /api/auth/signup`
- `POST /api/auth/login`
- `PATCH /api/auth/change-password`
- `PATCH /api/auth/change-pin`
- `POST /api/auth/send-otp`
- `POST /api/auth/send-signup-otp`
- `POST /api/auth/forgot-password`
- `POST /api/auth/reset-password`
- `POST /api/auth/verify-otp`
- `POST /api/auth/refresh`
- `POST /api/auth/logout`
- `GET /api/wallet`
- `GET /api/wallet/me`
- `GET /api/wallet/balance`
- `GET /api/wallet/transactions`
- `POST /api/wallet/topup`
- `GET /api/wallet/topup/{paymentId}`
- `POST /api/transaction/transfer`
- `POST /api/webhook/payment`
- `POST /webhook/payment`

## Verification

The backend currently includes integration tests that cover:

- signup
- JWT-protected wallet reads
- pin change
- transfer flow
- top-up creation
- webhook settlement

The test suite still uses an in-memory H2 datasource, and the cache falls back to local memory if Redis is unavailable, so `mvn test` works without a running PostgreSQL or Redis instance.
