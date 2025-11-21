# mdistributions - Marketing Email Sender

A robust backend service built with **Scala 3**, **Cats Effect**, and **Tapir** for managing marketing email campaigns. This application allows users to register, upload recipient lists via Excel, and orchestrate email sending via SMTP.

As of now, this project is a work in progress and serves as a learning exercise in building functional Scala applications with modern libraries and best practices. A frontend interface will soon come to complement this backend service.

## 🏗 Architecture

The application follows a clean, functional architecture separating the HTTP API from the background email processing logic.

```mermaid
graph TD
    subgraph "Client Layer"
        User["User / Browser"]
        Swagger[Swagger UI]
    end

    subgraph "Application Server (Scala 3 + Cats Effect)"
        API["HTTP API (Tapir/Http4s)"]
        
        subgraph "Services"
            Auth[JwtService]
            US[UserService]
            ES[EmailService]
        end

        subgraph "Background Process"
            Worker["EmailWorker (FS2 Stream)"]
        end

        subgraph "Data Access"
            UR[UserRepository]
            ER[EmailRepository]
        end
        
        Mailer[SmtpMailer]
    end

    subgraph "Infrastructure (Docker)"
        DB[(PostgreSQL)]
        SMTP["SMTP Server (Gmail / LocalStack)"]
    end

    User -->|HTTP Requests| API
    Swagger -->|Docs & Test| API
    
    API -->|Validate Token| Auth
    API -->|Register/Login| US
    API -->|Upload Excel| ES
    
    US --> UR
    ES --> ER
    
    UR -->|JDBC| DB
    ER -->|JDBC| DB
    
    Worker -->|Poll Pending Emails| ER
    Worker -->|Send Email| Mailer
    Mailer -->|SMTP Protocol| SMTP
````

## 📋 Features

  * **User Management**: Secure registration and login with password hashing (BCrypt) and JWT Authentication.
  * **Campaign Management**: Create email campaigns by uploading **Excel (.xlsx)** files containing recipient lists.
  * **Background Workers**: Asynchronous email processing using `fs2` streams to handle sending without blocking the API.
  * **Interactive Documentation**: Auto-generated Swagger UI for API exploration.
  * **Robust Architecture**: Built using functional programming principles and clean architecture layers.

## 🛠 Technology Stack

  * **Language**: Scala 3
  * **Effect System**: Cats Effect 3
  * **HTTP Server**: Http4s (via Tapir)
  * **API Definition**: Tapir (Endpoint-as-code)
  * **Database**: PostgreSQL
  * **Database Access**: Doobie (JDBC wrapper)
  * **Migrations**: Flyway
  * **Configuration**: PureConfig
  * **Testing**: ScalaTest & TestContainers (Docker integration tests)
  * **Build Tool**: sbt (with `sbtx` wrapper)

-----

## 🚀 Quick Start

Follow these steps to get the application running from scratch.

### 1\. Prerequisites

  * **Java JDK 11+** (JDK 21 Recommended)
  * **Docker & Docker Compose** (Required for the database)
  * **Git**
  * **sbt** (Optional, `sbtx` wrapper included)
  * **Gmail Account** (for SMTP)

### 2\. Clone the Repository

```bash
git clone https://github.com/ahkoklol/email-sender-scala-tapir-sbt-cats.git
cd mdistributions-back-cats
```

### 3\. Start Infrastructure

Start the PostgreSQL database and LocalStack (SMTP mock) using Docker Compose.

```bash
docker-compose up -d
```

*Services started:*

  * PostgreSQL: `localhost:5433`
  * LocalStack: `localhost:4566`

### 4\. Compile

Use the included wrapper script to ensure the correct sbt version is used.

```bash
./sbtx compile
```

### 5\. Run the Application

```bash
./sbtx run
```

*Database migrations will run automatically on startup.*

### 6\. Access the App

Once the logs show `Server started...`, open your browser:
👉 **http://localhost:8080/docs**

-----

## ⚙️ Configuration

The application uses `application.conf` for default settings. You can override these with environment variables for production or testing.

| Variable | Description | Default (Dev) |
| :--- | :--- | :--- |
| `HTTP_PORT` | Port for the API server | `8080` |
| `JWT_SECRET` | Secret key for signing tokens | `change-me-to...` |
| `SMTP_USER` | SMTP Username | `default-dev-user` |
| `SMTP_PASS` | SMTP Password | `default-dev-pass` |
| `SMTP_HOST` | SMTP Server Host | `smtp.gmail.com` |

### 📧 Google SMTP Setup

To send real emails using Gmail, you cannot simply use your login password. You must enable **2-Step Verification** and generate an **App Password**.

1.  Go to your **Google Account Settings** \> **Security**.
2.  Enable **2-Step Verification**.
3.  Search for **"App Passwords"** in the account search bar.
4.  Create a new app password (e.g., name it "EmailSender").
5.  Use your Gmail address as `SMTP_USER` and the generated 16-character App Password as `SMTP_PASS`.

**📺 Video Guide:**
For a step-by-step visual walkthrough on setting this up, watch this video:
[**How To Set Up Gmail SMTP Server - Full Guide**](https://www.youtube.com/watch?v=ZfEK3WP73eY)

### 💻 Setting Environment Variables

You can configure the application using either a `.env` file or by setting variables in your terminal.

#### Option 1: Using a `.env` file

Create a file named `.env` in the root of the `mdistributions-back-cats` directory:

```ini
SMTP_USER=your-email@gmail.com
SMTP_PASS=your-app-password
JWT_SECRET=your-secure-secret
```

#### Option 2: Command Line

You can export variables directly in your terminal before running the app.

**macOS / Linux (Bash/Zsh)**

```bash
export SMTP_USER="your-email@gmail.com"
export SMTP_PASS="your-app-password"
./sbtx run
```

**Windows (Command Prompt)**

```cmd
set SMTP_USER=your-email@gmail.com
set SMTP_PASS=your-app-password
sbtx run
```

**Windows (PowerShell)**

```powershell
$env:SMTP_USER="your-email@gmail.com"
$env:SMTP_PASS="your-app-password"
.\sbtx run
```

-----

## 📖 Usage Guide (Testing with Swagger)

Since there is no frontend yet, you can test the full email campaign flow directly via the Swagger UI.

### 1\. Register a User

1.  Expand the **`POST /users/register`** endpoint.
2.  Click **Try it out**.
3.  Enter a valid JSON body (e.g., `{"email": "test@test.com", "password": "password", "firstName": "John"}`).
4.  Click **Execute**. You should receive a `201 Created` response.

### 2\. Login & Get Token

1.  Expand the **`POST /users/login`** endpoint.
2.  Click **Try it out**.
3.  Enter the email and password you just registered.
4.  Click **Execute**.
5.  In the response body, **copy the `token` string** (without the quotes).

### 3\. Authorize

1.  Scroll to the very top of the Swagger page.
2.  Click the **Authorize** button.
3.  Paste your token into the value box (e.g., `eyJhbGci...`).
4.  Click **Authorize**, then **Close**.

### 4\. Send an Email Campaign

1.  Prepare a simple Excel file (`.xlsx`) with email addresses in the **first column**.
2.  Expand the **`POST /emails`** endpoint.
3.  Click **Try it out**.
4.  Fill in the `subject` and `body`.
5.  Upload your Excel file in the `file` field.
6.  Click **Execute**.

### 5\. Check Inbox

The background worker runs every **10 seconds**.

1.  **Wait** a few moments.
2.  Check the inbox of the recipient email addresses (or your LocalStack logs if running locally).


## 🧪 Testing

This project uses **TestContainers** to run integration tests against a real, ephemeral PostgreSQL database inside Docker.

```bash
./sbtx test
```

*Note: Docker must be running for tests to pass.*

-----

## 📂 Project Structure

```text
src/main/scala/com/ahkoklol
├── config        # App configuration & DB Transactor
├── domain        # Data models (User, Email) & Error types
├── integrations  # External service clients (SmtpMailer)
├── repositories  # Database access (Doobie SQL)
├── services      # Business logic (Auth, Excel parsing)
├── utils         # Utilities (ExcelParser)
├── workers       # Background processes (EmailWorker)
├── Endpoints.scala # Tapir API definitions
└── Main.scala    # Application entry point
```

## 📝 License

MIT License - Copyright (c) 2025 Wayne WAN CHOW WAH