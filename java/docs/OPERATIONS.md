# Operations

## Configuration

Copy `.env.example` to `.env` and set strong values. The server reads the
following environment variables:

| Variable | Required | Notes |
|---|---|---|
| `DB_HOST` | yes | MySQL host; use `mysql` inside Compose |
| `DB_PORT` | yes | Default `3306` |
| `DB_NAME` | yes | Default `venuity` |
| `DB_USER` | yes | Default `venuity` |
| `DB_PASSWORD` | yes | Must match the MySQL user password |
| `JWT_SECRET` | yes | At least 32 random bytes; changing it invalidates all tokens |
| `BOOTSTRAP_ADMIN_USER` | no | Creates an ADMIN on first start if absent |
| `BOOTSTRAP_ADMIN_PASSWORD` | no | Required for the bootstrap user to be created |
| `SERVER_PORT` | no | Default `8080` |

Never commit `.env`. The repository `.gitignore` excludes it.

## Start and stop

Local MySQL only:

```powershell
docker compose up -d mysql
docker compose ps
docker compose logs -f mysql
```

Server on the host:

```powershell
Get-Content .env | ForEach-Object {
  if ($_ -match '^\s*([A-Za-z_][A-Za-z0-9_]*)\s*=\s*(.*)\s*$') {
    Set-Item -Path ("Env:" + $matches[1]) -Value $matches[2]
  }
}
java -jar .\venuity-server\target\venuity.jar
```

Server in Docker:

```powershell
docker compose --profile app up -d --build
docker compose logs -f server
```

Stop without deleting data:

```powershell
docker compose --profile app stop
```

Delete the local database volume only when you intend to lose local data:

```powershell
docker compose --profile app down -v
```

## Health and readiness

- `GET /api/health` returns the application health payload used by the SPA.
- `GET /actuator/health` returns the Spring Boot health endpoint.
- The Docker health check calls `/api/health`.
- If the server is up but the UI shows an offline banner, check the browser
  network tab, the server log, and `docker compose ps`.

## Backups

Use a consistent logical backup with `--single-transaction`:

```powershell
$stamp = Get-Date -Format "yyyyMMdd-HHmmss"
cmd /c "docker exec venuity-mysql mysqldump -u root -p%MYSQL_ROOT_PASSWORD% --single-transaction --routines --triggers venuity > backups\venuity-$stamp.sql"
```

Schedule this outside business hours, keep several generations, and copy the
files off the host. Test a restore regularly.

## Restore

Restore into a scratch database first, compare row counts and balances, then
restore into production during a freeze window:

```powershell
cmd /c "docker exec -i venuity-mysql mysql -u root -p%MYSQL_ROOT_PASSWORD% venuity < backups\venuity-YYYYMMDD-HHmmss.sql"
```

After restore, start the server and confirm Flyway validation, login, one known
customer balance, and one check-in.

## Rotate the JWT secret

1. Generate at least 32 random bytes, for example `openssl rand -base64 48`.
2. Update `JWT_SECRET` in the deployment secret store and `.env` for local use.
3. Restart the server. All existing access and refresh tokens become invalid.
4. Expect users to log in again. The in-memory refresh blacklist is also cleared.

Rotate the secret after suspected token exposure or when an operator leaves.

## Users

The only built-in account creation path is the bootstrap ADMIN environment
variables. The account is created only when it does not already exist. There is
currently no web UI or REST endpoint for creating additional users; this is a
known gap. Do not insert plaintext passwords into MySQL. Plan a reviewed admin
operation before adding more operators.

## Migration and cutover

1. Take a final SQLite backup and a MySQL backup.
2. Run `venuity-migrate` with `--dry-run` and keep the Markdown report.
3. Reconcile customer count, total balance, ledger count and check-in count.
4. Run without `--dry-run` during a freeze window.
5. Start the server, log in, find a known customer, recharge one unit, perform a
   check-in, and confirm analytics.
6. Keep the old system read-only and the SQLite backup for the agreed rollback
   window.

Use `--force-empty-check-off` only when replacing an intentionally disposable
target. It deletes existing target data in the same transaction as the import.

## Logs

- Host process: redirect stdout/stderr to a rotating file or use the service
  manager's journal.
- Docker: `docker compose logs -f server`.
- MySQL: `docker compose logs -f mysql`.
- Do not log tokens, passwords, full `.env` contents, or production customer
  data.

## TLS and network exposure

The Spring Boot application serves HTTP. Put it behind a reverse proxy that
terminates TLS, forwards to port 8080, and sets appropriate request-size and
timeout limits. Restrict MySQL port 3306 to the application network; the Compose
mapping is for local development only.

## Troubleshooting

| Symptom | Check |
|---|---|
| `JWT_SECRET must be at least 32 bytes` | Set a longer random secret |
| Flyway validation failure | Do not edit applied migrations; restore the matching schema or add a new migration |
| `Communications link failure` | MySQL is stopped, wrong host/port, or credentials do not match |
| Migration refuses non-empty target | Use a fresh database, or `--force-empty-check-off` after taking a backup |
| Login succeeds but API calls return 401 | Check token storage, clock skew, and whether the server restarted with a new secret |
| Camera scan does nothing | Use HTTPS or localhost for `getUserMedia`, grant camera permission, or use manual code entry |
