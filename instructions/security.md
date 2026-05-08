# Security rules

- JWT tokens for authentication (jjwt library)
- Python scripts run ONLY in GraalPython sandbox — never shell exec
- User input validated via Jakarta Validation (`@NotNull`, `@Size`, `@Valid`)
- Passwords hashed via BCrypt
- No SQL/NoSQL injection — use parameterized queries / Spring Data
- No sensitive data in logs (passwords, tokens, personal data)
- CORS restricted to frontend origin
- Uploaded files validated for type and size
