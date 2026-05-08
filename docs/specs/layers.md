# Architectural layers of DocForge-Next

## Overview

Modular monolith with explicit separation of concerns. Every layer has strictly defined responsibilities. Modules communicate ONLY via `*Service` interfaces — never import another module's Repository.

## Backend layers (`com.docforge.{module}`)

### API Layer
- Package: `controller/`
- Responsibility: HTTP mapping, DTO validation, service calls
- Contract: REST API (`docs/architecture.md` section 4)

### Service Layer
- Package: `service/`
- Responsibility: orchestration, domain object calls, repository access
- Contract: `*Service.java` interfaces

### Domain Layer
- Package: `domain/`
- Responsibility: pure business logic (conditions, formatting, generation)

### Data Access Layer
- Package: `repository/`, `model/`
- Responsibility: PostgreSQL (JPA) and MongoDB access

### Integration Layer
- Package: `integration/`, `export/`
- Responsibility: GraalPython, Redis, DOCX/PDF export

## Frontend layers (`frontend/src/`)

### UI Layer
- Directory: `components/`, `pages/`
- Responsibility: React components, shadcn/ui (migrating from Ant Design)

### State Layer
- Directory: `store/`
- Responsibility: Zustand stores

### API Layer
- Directory: `api/`
- Responsibility: HTTP clients to backend

### Types Layer
- Directory: `types/`
- Responsibility: TypeScript type definitions

### Domain Layer
- Directory: `domain/`
- Responsibility: frontend business rules

## Module isolation rules

| Rule | Description |
|------|-------------|
| Module communication | ONLY via `*Service` interfaces |
| Repository import | NEVER import another module's Repository |
| Controller | thin: mapping → service → DTO |
| DTO | Java records (`*Request`, `*Response`, `*DTO`) |
| Exceptions | custom + `GlobalExceptionHandler` |
