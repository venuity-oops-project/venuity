# Dependency Vulnerability Scan

Date: 2026-10-28

## Scope

Resolved Maven dependencies for all four modules, including runtime and test
scopes. The scan covered 148 unique Maven artifacts after dependency resolution.

## Method

1. Resolve the dependency list with Maven:

   ```powershell
   $env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot"
   $env:Path = "$env:JAVA_HOME\bin;$env:Path"
   .\mvnw.cmd -B -ntp dependency:list "-DoutputFile=target/dependency-list-all.txt"
   ```

2. Query the [OSV API](https://api.osv.dev/v1/querybatch) for each resolved
   `group:artifact` and version using the `Maven` ecosystem.
3. Review every returned advisory against the application's actual usage.

## Dependency baseline

The scan was run after upgrading the build from Spring Boot 3.3.5 to 3.5.16 and
applying security overrides for components still pinned to vulnerable versions
by the Spring Boot BOM:

| Component | Version |
|---|---|
| Spring Boot | 3.5.16 |
| Spring Framework | 6.2.19 |
| Spring Security | 6.5.11 |
| Hibernate ORM | 6.6.53.Final |
| Tomcat Embed | 10.1.59 |
| Jackson | 2.21.7 |
| Log4j | 2.25.5 |
| MySQL Connector/J | 9.7.0 |
| Flyway | 11.7.2 |
| springdoc | 2.8.17 |
| JJWT | 0.12.7 |
| SQLite JDBC | 3.53.4.0 |

The upgrades cleared all reported high and critical issues in Tomcat, Jackson,
Log4j, Spring Security, Spring Data and the remaining runtime dependencies.

## Result

Two critical Spring Framework 6.2.19 advisories remain. Both are accepted
because the affected code paths are not present in this application:

| Advisory | Severity | Fixed version | Assessment |
|---|---|---|---|
| `GHSA-j9f9-w8pj-32f8` | Critical | 7.0.9 | Server-Sent Event stream corruption while rendering fragments. Venuity exposes a REST API and a static SPA; it does not use `SseEmitter`, server-side fragment rendering or a view resolver. Not reachable. |
| `GHSA-pc63-qcmh-9cmg` | Critical | 7.0.9 | Improper path limitation in `XsltView`. Venuity does not configure XSLT views or any `ViewResolver`; responses are JSON or static files. Not reachable. |

No other high or critical advisories were returned for the resolved dependency
set. The full scan result is therefore: **no reachable high/critical issues;
two non-reachable Spring Framework advisories accepted.**

## Follow-up

- Re-run this scan before each release; advisories are time-sensitive.
- Reassess the accepted Spring Framework advisories when the Spring Boot 4.x /
  Spring Framework 7 upgrade is scheduled. Spring Boot 3.5 remains on Spring
  Framework 6.2, where the fixes are not backported.
- Dependabot is configured for Maven and GitHub Actions and will surface new
  dependency releases.
