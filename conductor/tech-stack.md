# Technology Stack

Last reviewed: 2026-08-21.

## Backend
- **Language:** Java 25
- **Framework:** Spring Boot 4.1.0 with Spring Modulith 2.1 for modular architecture.
- **Data access and migrations:** Spring JDBC/JPA as required by each module and Flyway migrations through V78.

## Frontend
- **Language:** TypeScript
- **Framework:** Next.js 16.2.11 and React 19.2.4.
- **Styling:** Tailwind CSS (v4) for utility-first styling.
- **Icons & Data Viz:** Lucide-React for iconography and Recharts for responsive, composable data visualization.
- **Testing:** Vitest/unit tooling and Playwright 1.61.1 for browser acceptance tests.

## Infrastructure & Data
- **Primary Database:** PostgreSQL 18.
- **Caching & Key-Value Store:** Redis 8.
- **Object Storage:** MinIO.
- **Environment:** Docker Compose for local development and orchestration.
- **Payments:** Stripe Java SDK and Stripe CLI container `stripe/stripe-cli:v1.50.3`; Mercado Pago remains a configurable provider path where enabled.
- **Email:** SMTP delivery with durable notification records; Zoho Mail provider configuration is planned, not yet implemented.
- **CI:** GitHub Actions validates backend, frontend, Compose and the pinned Stripe CLI image.
