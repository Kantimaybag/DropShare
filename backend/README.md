# DropShare Backend • Java 21 & Spring Boot 3

Production-ready backend for DropShare, providing secure, ephemeral, OTP-protected file and text sharing.

## Quick Start (Dev Profile with H2)

```powershell
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

- API Base: `http://localhost:8080/api/v1`
- Swagger UI: `http://localhost:8080/swagger-ui.html`
- OpenAPI Docs: `http://localhost:8080/api-docs`
- H2 Console: `http://localhost:8080/h2-console` (JDBC URL: `jdbc:h2:file:./data/dropshare`, user: `sa`)

## Production Run with MySQL

```powershell
mvn clean package -DskipTests
java -jar target/sharedrop-backend-1.0.0.jar
```

Refer to root `README.md` for complete database schema, environment variables, and cURL examples.
