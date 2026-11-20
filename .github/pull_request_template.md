## Summary
Spring Boot backend for the e-bookstore capstone: login, catalogue (category, brand, search, filters), product page with
tentative delivery date and related reads, basket (guest and user), checkout with address, mock payment, gift points,
order history with Buy Again and recommendations, and cancel within 48 hours.

## API documentation
- OpenAPI spec: [`docs/openapi.yaml`](../docs/openapi.yaml) (also served at `/swagger-ui.html` when running)
- Data model: [`docs/data-model.md`](../docs/data-model.md)

## How to run
See [`README.md`](../README.md). Needs PostgreSQL; Flyway creates the schema on first start.

## Testing
- [ ] `mvn clean install` passes in `backend/` (15 integration tests on H2 in PostgreSQL mode)
- [ ] Started against PostgreSQL and exercised the endpoints in Insomnia
- [ ] Verified rows are saved in the database

## Checklist
- [ ] No secrets committed (DB password and JWT secret come from `DB_PASSWORD` / `JWT_SECRET`)
- [ ] Card number and CVV are never stored (only last 4 digits)
- [ ] `docs/openapi.yaml` matches the controllers
