# Venuity Java/MySQL

Venuity is a play-area wallet and check-in manager. This directory contains the
Java/Spring Boot server, the browser UI served by that server, the SQLite-to-MySQL
migration tool, and the retained Swing foundation. The supported release is one
runnable JAR: `venuity-server/target/venuity.jar`.

The browser UI is the supported client. The Python/Django and React applications
remain in the repository as the behavioural reference, and `venuity-client` is
kept only until the Phase 9 cleanup.

## Stack

- Java 25 (LTS), Spring Boot 3.5.16, Spring Security, Spring Data JPA
- MySQL 8.4 with Flyway migrations
- Static HTML/CSS/JavaScript SPA served from `venuity-server/src/main/resources/static`
- JWT access/refresh tokens; BCrypt passwords
- Maven Wrapper; Testcontainers for integration tests
- Docker Compose for MySQL and an optional server container

## Prerequisites

- JDK 25 (`java -version`)
- Docker Desktop or Docker Engine (MySQL and Testcontainers)
- Node.js 20+ only for the static JavaScript syntax check

## Start the complete application (one command)

After copying `.env.example` to `.env` and setting its passwords and
`JWT_SECRET`, run this from the repository root:

```powershell
.\java\run.ps1
```

The command starts (or waits for) MySQL, builds the runnable JAR when sources
have changed, and starts Venuity. It needs Docker Desktop and Java 25. Open
<http://localhost:8080> once it has started. Stop the server with `Ctrl+C`; the
MySQL container remains available for the next run.

## Manual build and start

```powershell
cd java
Copy-Item .env.example .env
# Edit .env and replace the placeholder passwords and JWT_SECRET.
docker compose up -d mysql
.\mvnw.cmd -B -DskipTests package
```

Load the same values into the current PowerShell session, then start the server:

```powershell
Get-Content .env | ForEach-Object {
  if ($_ -match '^\s*([A-Za-z_][A-Za-z0-9_]*)\s*=\s*(.*)\s*$') {
    Set-Item -Path ("Env:" + $matches[1]) -Value $matches[2]
  }
}
java -jar .\venuity-server\target\venuity.jar
```

On bash/WSL:

```bash
set -a
. ./.env
set +a
java -jar venuity-server/target/venuity.jar
```

Open <http://localhost:8080>. The login page uses the branding settings. If
`BOOTSTRAP_ADMIN_USER` and `BOOTSTRAP_ADMIN_PASSWORD` are set, the server creates
that ADMIN account on first start when it does not already exist.

For a production-like local run, add `--spring.profiles.active=prod` to enable
graceful shutdown and INFO logging. The Compose `server` service sets this
profile automatically.

Useful URLs:

- UI: <http://localhost:8080/>
- Health: <http://localhost:8080/api/health>
- OpenAPI UI: <http://localhost:8080/swagger-ui.html>

## Run the server in Docker

The `server` service uses the `app` profile so a MySQL-only start stays cheap:

```powershell
docker compose --profile app up -d --build
docker compose logs -f server
```

Set `JWT_SECRET` to at least 32 random bytes and set a non-empty
`BOOTSTRAP_ADMIN_PASSWORD` in `.env` before the first start. The Compose fallback
JWT secret is for local development only.

## Tests and checks

Full build and test suite (Docker must be running for Testcontainers):

```powershell
.\mvnw.cmd -B verify
```

Static JavaScript syntax check:

```powershell
Get-ChildItem .\venuity-server\src\main\resources\static\js -Recurse -Filter *.js |
  ForEach-Object { Get-Content -Raw $_.FullName | node --input-type=module --check }
```

Server-only package without tests. This produces the single distributable
`venuity-server/target/venuity.jar`:

```powershell
.\mvnw.cmd -pl venuity-server -am -DskipTests package
```

## Data migration

Build the migrator:

```powershell
.\mvnw.cmd -pl venuity-migrate -am -DskipTests package
```

Run a dry run first. The password is passed through an environment variable so it
does not appear in shell history:

```powershell
$env:VENUITY_DB_PASSWORD = "<the MYSQL_PASSWORD value>"
java -jar .\venuity-migrate\target\venuity-migrate-1.0.0-SNAPSHOT.jar `
  --sqlite ..\legacy\playarea.db `
  --mysql-url "jdbc:mysql://localhost:3306/venuity?connectionTimeZone=UTC" `
  --mysql-user venuity `
  --mysql-password-env VENUITY_DB_PASSWORD `
  --source-timezone Asia/Kolkata `
  --dry-run `
  --report ..\docs\migration\DRY_RUN_REPORT.md
```

Remove `--dry-run` to commit. The migrator refuses a non-empty target unless
`--force-empty-check-off` is supplied; that flag deletes existing target data
transactionally. Always keep the Markdown report and reconcile balances before a
real cutover.

## Configuration

| Variable | Default | Purpose |
|---|---|---|
| `DB_HOST` | `localhost` | MySQL host |
| `DB_PORT` | `3306` | MySQL port |
| `DB_NAME` | `venuity` | Database name |
| `DB_USER` | `venuity` | Database user |
| `DB_PASSWORD` | `venuity` | Database password |
| `DB_POOL_SIZE` | `20` | HikariCP maximum pool size |
| `SERVER_PORT` | `8080` | HTTP port |
| `JWT_SECRET` | development-only value | JWT signing key; must be at least 32 bytes |
| `JWT_ACCESS_MINUTES` | `480` | Access-token lifetime |
| `JWT_REFRESH_DAYS` | `7` | Refresh-token lifetime |
| `BOOTSTRAP_ADMIN_USER` | empty | Username created on first start if set |
| `BOOTSTRAP_ADMIN_PASSWORD` | empty | Password for the bootstrap ADMIN |
| `BOOTSTRAP_ADMIN_FORCE_RESET` | `true` (dev) / `false` (prod) | When true, overwrites existing bootstrap admin password on startup |

Do not commit `.env`. Use `.env.example` as the template.

## Project layout

| Path | Contents |
|---|---|
| `venuity-common` | Shared money and error-code utilities |
| `venuity-server` | Spring Boot API and static browser UI; produces `target/venuity.jar` |
| `venuity-migrate` | SQLite-to-MySQL migration CLI |
| `venuity-client` | Retained Swing foundation; not the supported UI |
| `docs/ARCHITECTURE.md` | Components, data model and request flows |
| `docs/OPERATIONS.md` | Backups, restore, JWT rotation and deployment |
| `docs/QA_CHECKLIST.md` | Verification checklist and known gaps |
| `../docs/migration/` | Migration plan, inventory, decisions and progress log |

## Security notes

- Set a unique `JWT_SECRET` of at least 32 random bytes outside local development.
- Terminate TLS at a reverse proxy; the application itself serves plain HTTP.
- Refresh tokens are tracked persistently in the `user_session` table with SHA-256 token hashing; token invalidation is enforced immediately via `token_version` on password changes and session revocation.
- The migration tool reads SQLite read-only and never copies production data into
  the repository.
- The physical camera path is not covered by automated tests; manual check-in is.

## Known gaps

- The load/performance gate and dependency-vulnerability scan in the migration
  plan have not been completed.
- Physical QR-camera scanning needs hardware verification.
