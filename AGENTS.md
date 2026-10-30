# AGENTS.md

Notes for anyone (human or AI agent) working in this repo. Verified against the code as of the last commit.

## What this is

"ShopNest" product-catalog service: a **Spring Boot 4.1.1** REST API for products and categories, backed by
**MySQL + JPA/Hibernate**, with a Groq LLM call for marketing pitches and a hand-rolled token-check filter for auth.

- Build: Maven (`pom.xml`), `source/target` Java **17**
- Packaging: **WAR** (`spring-boot-tomcat` is `provided`) — runs embedded via `spring-boot:run`, or deployable to an external Tomcat
- Runtime observed: Java 21.0.8, Hibernate 7.4.5.Final, MySQL 9.5
- JSON is **Jackson 3** (`tools.jackson.databind.*`, not `com.fasterxml.jackson.databind.*`); `Optional` support is
  built into databind, so `Optional` fields deserialize without an extra module.

## Build / run / test

There is **no `mvn` on the PATH** — always use the wrapper:

```sh
./mvnw -B test        # full suite (needs a live MySQL, see below)
./mvnw spring-boot:run
./mvnw -B package
```

Run a single test class (works offline, no DB):

```sh
./mvnw -B test -Dtest=ProductMapperTest
```

### The test suite needs a real database

`EcommApplicationTests` is a `@SpringBootTest` that boots the full context, so it opens a Hikari connection to
`jdbc:mysql://localhost:3306/productcatalogservicetest` and runs Hibernate `ddl-auto=update`. It fails without MySQL
up. `ProductMapperTest` is a plain unit test and needs nothing.

Current state: **5 tests, all passing** (4 mapper + 1 context load).

Known benign warnings: `spring.jpa.open-in-view is enabled by default`, and Mockito self-attaching its inline-mock
agent (will break on future JDKs unless configured as a `-javaagent`).

## Configuration

| File | Notes |
|---|---|
| `src/main/resources/application.properties` | dev defaults; plaintext root password, `ddl-auto=update`, `show-sql=true`, port defaults to 8080 |
| `src/main/resources/application-prod.properties` | `shopnest_app` user, `DB_PASSWORD` env var, `show-sql=false`, `ddl-auto=validate` |
| `.env` | gitignored, loaded via `spring.config.import=optional:file:.env`; template in `.env.example` |

Required env vars: `GROQ_API_KEY` (for `PitchService`), `AUTH_SERVICE_URL` (defaults to `http://localhost:8081`),
`DB_PASSWORD` (prod profile only).

## Code layout

```
src/main/java/com/example/ecomm/
├── EcommApplication.java        @SpringBootApplication entry point
├── ServletInitializer.java      WAR bootstrap for external Tomcat
├── controllers/ProductController.java     all endpoints (no class-level @RequestMapping)
├── services/IProductService.java          interface
├── services/StorageProductService.java    @Service @Primary — the DB-backed impl actually injected
├── services/DummyJsonProductService.java  talks to dummyjson.com (used only by /products/import)
├── services/PitchService.java             Groq chat-completions call, degrades to a canned string on error
├── repository/ProductRepository.java      JpaRepository (no impl classes — Spring Data generates them)
├── repository/CategoryRepository.java     findByName
├── models/{BaseModel,Product,Category,State}.java   JPA entities; BaseModel has id + timestamps + state
├── mappers/ProductMapper.java             entity <-> DTO, plain @Component
├── dtos/                                  request/response shapes (ProductDto, CategoryDto, ...)
└── security/TokenCheckFilter.java         OncePerRequestFilter, auto-registered (no Spring Security on classpath)
```

Endpoints (all in `ProductController`): `GET /products`, `GET /products/{id}`, `POST /products`,
`PATCH /products/{id}`, `PUT /products/{id}`, `DELETE /products/{id}`, `POST /products/import`,
`GET /products/{id}/pitch`. Also `GET /actuator` (actuator starter).

### Partial updates (`PATCH /products/{id}`)

`ProductPatchDto` / `CategoryPatchDto` use `Optional<T>` fields to get three states, which a nullable field
cannot express:

| In the JSON body | Field value after deserialization | Effect |
|---|---|---|
| key missing | `null` | leave the stored value untouched |
| `"field": value` | `Optional.of(value)` | apply the value |
| `"field": null` | `Optional.empty()` | clear the stored value |

`StorageProductService#patchProduct` applies the scalars via `applyIfPresent`, and `patchCategory` resolves the
category by name (sent name, else the product's current category name), falling back to find-or-create the same way a
full replace does. `"category": null` detaches the category. `lastUpdatedAt` is always bumped.


## Conventions to follow

- Package-by-layer (`controllers`, `services`, `repository`, `models`, `dtos`, `mappers`), not by feature.
- Services are interface + impl; the DB impl is `@Primary`; the controller injects by `@Qualifier`.
- Entities extend `BaseModel` (`@MappedSuperclass`) for `id`, `createdAt`, `lastUpdatedAt`, `state`.
- **Deletes are soft**: `DELETE` sets `state = INACTIVE`; reads filter on `State.ACTIVE`.
- DTO ↔ entity conversion lives in `mappers/ProductMapper`; controllers never touch entities in responses.
- Lombok is used throughout (`@Getter/@Setter/@Builder/...`) — annotation processors are configured in `pom.xml`;
  keep the Lombok/configuration-processor `annotationProcessorPaths` blocks intact.


## Things to always do when you start a new session

- Address the user by Senpai.