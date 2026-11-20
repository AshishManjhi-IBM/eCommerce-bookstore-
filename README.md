# E-Bookstore

Spring Boot backend for the capstone e-bookstore (Java 17, PostgreSQL, JWT).
The data model and the API contract are in [`docs/data-model.md`](docs/data-model.md) and [`docs/openapi.yaml`](docs/openapi.yaml).

## Layout
```
backend/   Spring Boot app (one package per domain service: member, store, catalog, order, payment, shipping)
docs/      data-model.md, openapi.yaml
refDocs/   capstone instructions, wireframes and architecture diagram
```

## Run locally
1. Install JDK 17+ and Maven, and PostgreSQL.
2. Create the database:
   ```sql
   CREATE DATABASE bookstore;
   ```
3. Set the connection in `backend/src/main/resources/application.properties` or with environment variables
   `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` (defaults: `jdbc:postgresql://localhost:5432/bookstore`, `postgres` / `postgres`).
4. Build and start:
   ```
   cd backend
   mvn clean install
   mvn spring-boot:run
   ```
   Flyway creates the tables on first start. A sample admin, store, 10 books, a `WELCOME10` coupon and shipping rates are seeded
   when the database is empty (`app.seed.enabled=true`).
5. Open Swagger UI at http://localhost:8080/swagger-ui.html.

Seeded admin: `admin@bookstore.com` / `Admin@123` (change it in `application.properties`).

## Tests
```
cd backend
mvn test                              # all tests
mvn test -Dtest=BookstoreApiTests#fullPurchaseJourneyThenCancelWithinWindow
```
Tests run on in-memory H2 (PostgreSQL mode) with the same Flyway migrations, so no database is needed.

## Try it (curl)
```
# register + login
curl -X POST localhost:8080/api/auth/register -H "Content-Type: application/json" \
  -d '{"firstName":"Asha","lastName":"Rao","email":"asha@test.com","password":"Password1"}'
TOKEN=$(curl -s -X POST localhost:8080/api/auth/login -H "Content-Type: application/json" \
  -d '{"email":"asha@test.com","password":"Password1"}' | jq -r .token)

curl "localhost:8080/api/products?q=hobbit&sort=price_asc"            # browse (no login needed)
curl -X POST localhost:8080/api/basket/items -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" -d '{"productId":6,"quantity":1}'
curl -X POST localhost:8080/api/checkout/quote -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" -d '{"state":"Karnataka"}'
curl -X POST localhost:8080/api/orders -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" -d '{
  "address":{"firstName":"Asha","lastName":"Rao","addressLine":"12 MG Road","email":"asha@test.com",
    "phone":"9876543210","city":"Bengaluru","pin":"560001","state":"Karnataka","country":"India"},
  "payment":{"method":"CREDIT_CARD","cardNumber":"4111111111111111","expiry":"12/45","cvv":"123"}}'
curl -X POST localhost:8080/api/orders/1/cancel -H "Authorization: Bearer $TOKEN"   # within 48 hours
```

## Behaviour notes
- **Guests** browse and fill a basket (`X-Guest-Token` returned by the basket API); login merges it; checkout needs login.
- **Entitlement**: ADMIN sees inactive books; everyone else sees active ones only.
- **Payment** is a mock gateway: only the last 4 card digits and the method are stored, never the number or CVV.
  Methods: `CREDIT_CARD`, `DEBIT_CARD`, `UPI`, `WALLET`. A card ending `0002` is always declined (for testing).
  A declined payment returns 402 and nothing is saved.
- **Gift points**: 1 point = Rs 1, earned at 1% of the amount paid (`app.gift-points.*`). Tax is `app.tax-rate`.
- **Delivery**: charge is the flat region rate, free from `app.shipping.free-threshold`. Delivery date = today + product
  `deliveryDays`, falling back to the region's days.
- **Cancel**: allowed while `PLACED` and within 48 hours; restores stock and gift points and refunds the payment.
