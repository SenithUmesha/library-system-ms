# Library System — Microservices Edition 📚

> a 2021 Spring Boot experiment where a tiny library app became three separate processes because I wanted to understand what “microservices” actually felt like.

This project splits a small library system into a **user service**, a **book service**, and a **server-rendered web app** that talks to both over HTTP.

It is intentionally kept in its era: Java 8, Spring Boot 2.5, JSP, `RestTemplate`, MySQL and three independently runnable Maven projects. The current pass cleans the boundaries and security problems that are useful to fix without pretending this started life as a modern distributed platform.

`Java 8` · `Spring Boot 2.5` · `Spring MVC` · `Spring Data JPA` · `MySQL` · `JSP` · `RestTemplate`

## the shape

```text
                       browser
                          │
                          ▼
                  user-web-app :8080
                    Spring MVC + JSP
                    │             │
                    │ HTTP        │ HTTP
                    ▼             ▼
          user-service-db      book-service-db
               :8082               :8084
          Spring Data JPA      Spring Data JPA
               │                    │
               ▼                    ▼
        user_ms_service       book_ms_service
              MySQL                MySQL
```

Each service owns a small piece of data instead of the web app connecting directly to both databases.

That is the main reason this repository is still interesting: it is an early attempt at **service boundaries and data ownership**, not just another CRUD library page.

## what it does

### user side

- create a library user
- authenticate through the user service
- edit account details
- delete a user
- render login/dashboard flows in JSP

### book side

- list all books
- search a book by exact name
- search a book by exact author
- render search/list results through the web app

The underlying models are deliberately small:

```text
User
├── id
├── name
├── password hash
├── age
└── address

Book
├── book_id
├── book_name
└── book_author
```

## the interesting part: service-to-service calls

The web layer does not own JPA repositories.

Instead:

```text
JSP form
   │
   ▼
web controller
   │
   ▼
RestTemplate
   │
   ├── user service
   └── book service
          │
          ▼
     service-owned DB
```

That creates real distributed-system failure modes even in a tiny coursework app:

- a service can be down while the web app is up
- network calls can fail independently of rendering
- API contracts matter
- ports/configuration matter
- data is split by service ownership
- authentication should not leak credentials into URLs

Those lessons are more valuable now than the size of the app itself.

## security cleanup

The original login path called:

```text
GET /users/{name}/{password}
```

which puts a password directly in the URL path. URLs are commonly logged by servers, proxies and tooling, so credentials should never be transported that way.

The current flow uses:

```text
POST /users/authenticate
Content-Type: application/json

{
  "name": "...",
  "password": "..."
}
```

The user service also stores new/updated passwords as **BCrypt hashes** instead of normal strings. For compatibility with old local databases, a successful login against a legacy plaintext row migrates that password to BCrypt once.

User JSON responses mark the password field write-only so it is not serialized back to callers.

This is still an educational authentication system, not a full identity platform: there are no sessions/tokens, account lockouts, MFA or authorization policies.

## configuration is no longer tied to one laptop

The original services hard-coded local MySQL credentials and the web app hard-coded service URLs.

Current configuration supports environment variables:

```text
USER_DB_URL
USER_DB_USER
USER_DB_PASSWORD

BOOK_DB_URL
BOOK_DB_USER
BOOK_DB_PASSWORD

USER_SERVICE_URL
BOOK_SERVICE_URL
```

with local-development defaults for hosts/ports.

That makes the architecture explicit:

```text
configuration
   │
   ├── user service database
   ├── book service database
   └── service discovery-by-config
```

There is still no service registry or container orchestration. For three local processes, configuration is enough.

## API sketch

### user service — `:8082`

```text
POST   /users/authenticate
POST   /users
PUT    /users/{id}
DELETE /users?name={name}
```

### book service — `:8084`

```text
GET /books
GET /books/search?name={name}
GET /books/search?author={author}
```

The search endpoints now use query parameters rather than embedding arbitrary titles/authors into path segments.

## project layout

```text
library-system-ms/
├── user-service-db/
│   ├── pom.xml
│   └── src/main/java/.../userservicedb/
├── book-service-db/
│   ├── pom.xml
│   └── src/main/java/.../bookservicedb/
├── user-web-app/
│   ├── pom.xml
│   ├── src/main/java/.../userwebapp/
│   └── src/main/webapp/*.jsp
├── database/
│   ├── user_ms_service.txt
│   └── book_ms_service.txt
├── docs/
│   └── engineering.md
└── .github/workflows/ci.yml
```

Each Spring project keeps its own Maven wrapper, which is how the original repository was structured.

## local setup

Create the two MySQL databases/tables using the SQL in `database/`:

```text
user_ms_service
book_ms_service
```

Then configure database credentials. Example for a shell:

```bash
export USER_DB_USER=root
export USER_DB_PASSWORD=your-local-password
export BOOK_DB_USER=root
export BOOK_DB_PASSWORD=your-local-password
```

Run the services in this order (three terminals):

```bash
cd user-service-db
./mvnw spring-boot:run
```

```bash
cd book-service-db
./mvnw spring-boot:run
```

```bash
cd user-web-app
./mvnw spring-boot:run
```

Then open `http://localhost:8080/log-in`.

Default local service addresses are:

```text
user service  http://localhost:8082
book service  http://localhost:8084
```

They can be overridden with `USER_SERVICE_URL` / `BOOK_SERVICE_URL`.

## what this project is *not*

This repo does not demonstrate production microservice infrastructure.

There is no:

- API gateway
- OAuth/JWT authorization
- service discovery
- event bus
- distributed tracing
- centralized config server
- circuit breaker
- Docker/Kubernetes deployment
- independent CI/CD per service

And that is fine. The useful experiment here is much smaller: **split responsibilities into separately running HTTP services and feel the consequences.**

## if i rebuilt it today

I would first ask whether this app needs three deployable services at all.

For this scope, a modular monolith would probably be easier to operate:

```text
library app
├── users module
├── catalog module
└── web/API module
        │
        ▼
     one database
```

If independent service ownership were genuinely needed, I would keep the split but add proper authentication, typed API clients, request timeouts, migrations, observability, containerized local infrastructure and contract/integration tests.

That trade-off is one of the best things this old project teaches: **microservices move complexity; they do not make it disappear.**

More detail: [`docs/engineering.md`](docs/engineering.md)
