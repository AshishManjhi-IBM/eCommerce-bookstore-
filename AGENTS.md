# AGENTS.md

This file provides guidance to agents when working with code in this repository.

## Layout
- `backend/`: Spring Boot 3.3 / Java 17 / Maven app. One package per domain service under `com.bookstore`: `member`, `store`, `catalog`, `order`, `payment`, `shipping`, plus `security`, `common`, `config` (seeder).
- `docs/`: `data-model.md` and `openapi.yaml`. Keep them in sync when changing endpoints or tables.
- `refDocs/`: capstone spec (`AI_Specialist_Capstone_Instructions.md`, wireframe and architecture PNGs, and the `.pptx.pptx` deck, double extension is real; read it with `office_read`).
- `.github/workflows/ci.yml`: runs `mvn verify` in `backend/` on push and PR. `.github/pull_request_template.md` is the PR checklist.

## Commands (run in `backend/`)
- Build: `mvn clean install`. Run: `mvn spring-boot:run` (needs PostgreSQL, see `README.md`).
- All tests: `mvn test`. Single test: `mvn test -Dtest=BookstoreApiTests#fullPurchaseJourneyThenCancelWithinWindow`.
- Tests use H2 in PostgreSQL mode via `@ActiveProfiles("test")` (`src/test/resources/application-test.properties`) with the real Flyway migrations, so SQL must stay portable between H2 and PostgreSQL.
- This machine has JDK 21 at `C:\Program Files\Eclipse Adoptium\jdk-21.0.13.9-hotspot` (not on PATH, set `JAVA_HOME`) and Maven at `%USERPROFILE%\tools\apache-maven-3.9.9`.

## Conventions and gotchas
- Schema is owned by Flyway (`src/main/resources/db/migration`); `ddl-auto=none`. Add a new `V#__*.sql` for schema changes, never edit applied ones.
- Table names `users` and `orders` (reserved words); the order entity is `CustomerOrder` (JPQL entity name `CustomerOrder`).
- Domain services are packages in one app (modular monolith). Cross-module calls go through services/repositories, e.g. `MemberService -> BasketService`.
- Roles: `CUSTOMER`, `ADMIN`. Route rules are in `security/SecurityConfig`; entitlement (inactive products hidden from non-admins) is enforced in `CatalogService`.
- Guests use `X-Guest-Token` for baskets; checkout needs a JWT.
- Never persist or log card numbers or CVV. Only `card_last4` is stored. `PaymentRequest.toString()` is overridden for this reason.
- Declined payments throw `ApiException` 402 inside the checkout transaction so the order, stock and basket changes roll back.
- Gift point value, earn rate, tax rate and free-shipping threshold are in `application.properties` (`app.*`).

## Domain scope
Journeys from the deck: login, catalogue by category/brand, product with tentative delivery date and related reads, basket, address, payment (credit/debit/UPI/wallet), gift points, confirmation, order history with Buy Again and recommendations, cancel within 48 hours. Extras from the architecture diagram and wireframes are kept simple: store/catalog/policy admin, coupons, wallet, refunds, returns, wishlist, writers, reviews.
