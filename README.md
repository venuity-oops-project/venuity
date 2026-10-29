# Venuity - PlayArea Manager

## ⚡ Run the Java Application (Single Command)

The primary supported distribution is the **Java 25 Single-JAR application** containing the Spring Boot backend, embedded static browser UI, and Flyway database migrations.

To run the application with a single command from the project root:

- **PowerShell (Windows):**
  ```powershell
  .\run.ps1
  ```
- **Command Prompt / Double-Click (Windows):**
  ```cmd
  run.bat
  ```
- **Bash / Linux / macOS / WSL:**
  ```bash
  ./run.sh
  ```
- **NPM:**
  ```bash
  npm start
  ```

This single command automatically:
1. Prepares local configuration (`java/.env`) with ready-to-run defaults if missing.
2. Starts and waits for the MySQL 8.4 database container (`docker compose up -d --wait mysql`).
3. Verifies that **Java 25** is installed and active on `PATH`.
4. Builds the single executable JAR (`venuity.jar`) with the Maven Wrapper if not already compiled or when code has changed.
5. Launches the application and serves the web UI at <http://localhost:8080> (Default admin login: `admin` / `admin`).

Direct single-JAR run (if MySQL is running):
```bash
java -jar venuity.jar
# or
java -jar java/venuity-server/target/venuity.jar
```

Detailed architecture and development guides are available in [`java/README.md`](java/README.md) and [`PROJECT_BRIEF.MD`](PROJECT_BRIEF.MD).

---

## ☁️ Deploying to the Cloud

Venuity is ready for free-tier cloud deployment using a multi-stage Docker container or standalone single-JAR:

- **Free Managed MySQL:** Works seamlessly with Aiven Free MySQL 8.4 (automatic SSL required, port mapping, `defaultdb` support).
- **Free Container Hosting:** Deploy to Render as a Docker Web Service using the provided [`render.yaml`](render.yaml) blueprint or Dockerfile.
- **Oracle Cloud Always Free VM:** Deploy with `systemd` and automatic HTTPS via Caddy reverse proxy using templates in [`deploy/`](deploy/).

👉 **Complete Step-by-Step Guide:** See [**`docs/DEPLOYMENT.md`**](docs/DEPLOYMENT.md) for full instructions, environment variable tables, migration guide, and backup procedures.

> [!WARNING]
> This project is under **active development**. The deployed website may not always reflect the latest code in this repository. For the most accurate and up-to-date implementation, please refer to the source code here rather than the live demo.

A premium, cross-platform application designed for PlayArea businesses to manage digital customer check-ins, wallet top-ups, and point-of-sale functionality securely using QR code scanning capabilities. 

Built with a modernized tech stack:
*   **Java 25 (LTS):** Spring Boot 3.5.16, Spring Security, Spring Data JPA, MySQL 8.4 LTS, Flyway
*   **Frontend (Java Single-JAR):** Embedded Glassmorphism SPA served from `src/main/resources/static/`
*   **Legacy Reference:** Django REST Framework in `backend/`, React in `frontend/`, Tauri in `src-tauri/`

---

## 🛠️ System Architecture

1. **`java/venuity-server/`:** Spring Boot 3.5.16 backend, REST API, Flyway database migrations, and bundled SPA client. Built as the single executable `venuity.jar`.
2. **`java/venuity-common/`:** Shared records, domain models, and DTOs.
3. **`java/venuity-migrate/`:** SQLite-to-MySQL data migration CLI tool.
4. **`java/venuity-client/`:** Retained Swing desktop foundation (superseded by embedded web client per ADR-013).
5. **`backend/` & `frontend/`:** Legacy Python/Django backend and React frontend retained as behavioural references until Phase 9 cutover.

---

## 📖 Usage Flow
1. **Initialize Settings:** Start by going to **Settings**. Set your Business Name, Currency Symbol, and standard Check-in Fee.
2. **Issue Customers:** Head to **Customers** to generate accounts (with automatic QR pass generation).
3. **Recharge Wallets:** Head to **Recharge** to add money to customer accounts (supports QR auto-fill).
4. **Scan Passes:** Open **Check-in**, point the camera at a customer's QR code, and watch the system automatically debit their wallet.
5. **Insights:** Track your revenue and visitor counts through the **Analytics** dashboard featuring interactive charts.
