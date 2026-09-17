# Engineering notes — Library System MS

This is a 2021 microservices learning project. It has three independently runnable Spring Boot applications and two MySQL databases.

The useful part is not the amount of code. It is seeing the architectural consequences of splitting a very small product across process boundaries.

## 1. System topology

```text
browser
  │
  ▼
user-web-app :8080
  │
  ├── HTTP -> user-service-db :8082 -> user_ms_service
  │
  └── HTTP -> book-service-db :8084 -> book_ms_service
```

The browser talks only to the JSP web app. The web app acts as an orchestration/presentation layer and uses `RestTemplate` for synchronous service calls.

## 2. Service ownership

The user service owns:

```text
user
├── id
├── name
├── password hash
├── age
└── address
```

The book service owns:

```text
book
├── book_id
├── book_name
└── book_author
```

The web application owns neither database. That is the core microservice idea being explored here: the owning service is the API boundary for its data.

## 3. Why the original login flow was unsafe

The historical user service exposed authentication as:

```text
GET /users/{name}/{password}
```

That makes the password part of a URL. URLs frequently appear in:

- access logs
- reverse-proxy logs
- browser/network tooling
- tracing systems
- error reports

Credentials should be request data, not resource identifiers.

The current endpoint is:

```text
POST /users/authenticate
Content-Type: application/json
```

with a body containing name/password.

## 4. Password storage

The 2021 implementation queried MySQL for a row matching both name and plaintext password.

The current service uses BCrypt for newly created and updated credentials.

For old development databases, authentication has a compatibility migration:

```text
load user by name
      │
      ├── stored value is BCrypt -> verify hash
      │
      └── stored value is legacy plaintext
              │
              ├── does not match -> reject
              └── matches -> replace with BCrypt hash
```

That allows an existing local demo database to move forward without needing a one-off password migration script that knows every user's plaintext password.

It is intentionally one-way: once migrated, the service no longer needs plaintext for that user.

## 5. Passwords are write-only JSON

The `User.password` field uses Jackson's write-only property mode.

That means the service can accept a password in create/update input while user response serialization does not send the stored hash back to clients.

A password hash is not a secret in exactly the same way as a plaintext password, but there is still no reason to expose it in normal API responses.

## 6. Authentication scope

This is still a small educational authentication mechanism.

A successful `/users/authenticate` call proves credentials at that moment, but the project does not issue:

```text
session cookie
JWT
OAuth token
refresh token
```

The JSP web layer also does not implement a durable authenticated session/authorization model.

A production rewrite needs identity/session design rather than simply calling the authentication endpoint before rendering a dashboard.

## 7. User API

Current user operations are conceptually:

```text
POST   /users/authenticate
POST   /users
PUT    /users/{id}
DELETE /users?name=...
```

The controller now returns HTTP status codes instead of using `null` as the main not-found/auth-failed signal.

Examples:

```text
400 bad input
401 invalid credentials
404 missing user
201 created
204 authenticated/deleted
```

## 8. Book API

The original book service embedded the search value directly in path segments:

```text
/books/name/{book_name}
/books/author/{book_author}
```

The current service exposes:

```text
GET /books
GET /books/search?name=...
GET /books/search?author=...
```

For human-entered titles/authors, query parameters are a cleaner representation and avoid manual path concatenation/encoding in the web app.

The project still performs exact matching because that is what the original repository implemented. It does not silently pretend to have fuzzy/full-text search.

## 9. Web app as synchronous orchestrator

The JSP application uses `RestTemplate` synchronously:

```text
request arrives
   │
   ▼
web controller
   │
   ▼
HTTP call to service
   │
   ▼
wait for response
   │
   ▼
render JSP
```

This is easy to understand, but it couples web-page latency/availability to downstream services.

If the book service is unavailable, book pages cannot complete normally even though the web app itself is running.

That is one of the earliest distributed-system lessons this project demonstrates.

## 10. Service URLs are configuration

The original web controllers contained literal localhost service URLs.

Current configuration uses:

```properties
services.user.base-url=${USER_SERVICE_URL:http://localhost:8082}
services.book.base-url=${BOOK_SERVICE_URL:http://localhost:8084}
```

There is no service registry. For three processes, environment-driven endpoints are enough to make the dependency visible and portable.

## 11. Database configuration

Each data service owns its connection settings.

User service:

```text
USER_DB_URL
USER_DB_USER
USER_DB_PASSWORD
```

Book service:

```text
BOOK_DB_URL
BOOK_DB_USER
BOOK_DB_PASSWORD
```

Passwords default to empty rather than committing a local credential.

Again, this is configuration hygiene. Environment variables are not a complete production secret-management system by themselves.

## 12. Database schemas

The repository includes simple SQL creation scripts.

The user table now gives password hashes enough space and uses a unique username constraint:

```text
user
├── id PK
├── name UNIQUE
├── password varchar(255)
├── age
└── address
```

The book table remains intentionally small:

```text
book
├── book_id PK
├── book_name
└── book_author
```

No borrowing/loan domain exists in this repository. This project is about the service split, not a full library circulation system.

## 13. Error behavior in the web app

The original web controllers assumed service calls succeeded.

Current calls catch downstream REST failures and return a useful message to the JSP model instead of letting a normal unavailable/not-found case become an unhandled exception.

The book search distinguishes a `404` from general service failure:

```text
404 -> no matching book
other RestClientException -> service unavailable/error
```

The login flow similarly distinguishes invalid credentials from general user-service failure.

## 14. Missing timeout/circuit-breaker layer

`RestTemplate` remains deliberately close to the original project and does not yet configure explicit connect/read timeouts or resilience policies.

For a production service-to-service call, I would add:

```text
connect timeout
read timeout
structured retries only where safe
circuit breaker / bulkhead if needed
request correlation IDs
metrics
```

Without timeouts, synchronous calls can occupy request threads longer than intended when a dependency stalls.

## 15. Why three services may be too many

This application is tiny. Splitting it into three deployable applications creates:

```text
3 processes
2 databases
2 network dependencies from the web app
3 build/deploy units
multiple configuration surfaces
```

for a domain that could comfortably fit in one application.

That does not make the project a failure. It makes the trade-off visible.

A modular monolith would likely be a better current design for the same feature set.

## 16. What microservices actually buy

The split does provide real boundaries:

- user persistence can change without book persistence
- the web layer never needs DB credentials
- each service can own its API contract
- processes can theoretically be deployed independently
- service failure domains are visible

Those benefits become valuable when independent ownership/scaling/deployment matters. They are overhead when it does not.

## 17. Data consistency

There are no cross-service transactions because the current features do not create entities spanning user and book databases.

If the system added borrowing, the architecture would have to answer questions such as:

```text
who owns a Loan?
how is a user referenced across services?
how is book availability reserved atomically?
what happens if one service is unavailable mid-workflow?
```

That is where the microservice split stops being merely organizational and starts shaping the domain model.

## 18. Versioning and contracts

The web app currently knows concrete endpoint paths and model shapes.

There is no explicit API versioning or generated client.

For a larger system I would use contract tests/OpenAPI and version deliberately rather than relying on three projects changing together in one Git repository.

## 19. Old stack, intentionally visible

The projects use:

```text
Java 8
Spring Boot 2.5.4
JSP
RestTemplate
javax.* APIs
```

I did not rewrite everything to current Spring/Jakarta just to make the repository look newer.

The portfolio value is understanding the service architecture and fixing the high-value boundary/security problems while preserving the project's era.

## 20. Build strategy

Each module has its own Maven project/wrapper.

CI compiles/packages each service independently:

```text
user-service-db
book-service-db
user-web-app
```

The build does not require a live MySQL server because CI skips runtime database integration tests; its purpose is to verify that the three codebases still compile after cleanup.

A stronger next step would add repository/controller tests with an isolated test database.

## 21. Production rebuild

If this genuinely needed independent services today, a more complete shape would look like:

```text
browser
  │
  ▼
web/API gateway
  │
  ├── auth/user service
  ├── catalog service
  └── circulation service
       │
       ├── typed APIs/events
       ├── tracing/metrics
       └── service-owned persistence
```

Authentication would use a proper session/token system and authorization would be enforced server-side.

Database schema changes would use migrations rather than standalone SQL text files.

## 22. The main lesson

The repo began with the idea that “microservices” meant putting responsibilities in different Spring Boot applications.

The more useful understanding is what comes after that split:

> **every process boundary becomes a network boundary, every data split becomes an ownership decision, and every independent service adds operational work.**

That is why I keep this project public. It is a small, concrete example of learning that architecture is mostly about trade-offs, not labels.
