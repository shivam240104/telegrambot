# 🤖 Telegram Quiz Bot

A **production-ready Telegram Quiz Bot** built with **Java, Spring Boot, PostgreSQL, Docker**, and deployed on **Render Cloud**. The project demonstrates real-world backend engineering practices including **role-based access control, fault tolerance, CI/CD automation, containerization, and cloud deployment**.

---

## 🚀 Live Demo

* **Bot:** (https://t.me/Sin221_bot)
* **Cloud Deployment:** (https://telegrambot-kh0o.onrender.com

)

---

## 🧩 Features

### 👤 User Features

* Participate in quizzes
* Receive instant feedback on answers
* View quiz results
* Safe validation for invalid inputs and session handling

### 🛡️ Admin Features (Limited)

* Create and manage quizzes
* Add and edit questions
* Activate / deactivate quizzes
* Cannot promote other admins (security boundary)

### 👑 Super Admin Features

* All admin capabilities
* Promote new admins
* Full control over quiz management and bot configuration

> This hierarchical role model prevents misuse and ensures secure privilege boundaries.

---

## 🔐 Role-Based Access Control (RBAC)

| Role        | Permissions                  |
| ----------- | ---------------------------- |
| User        | Take quizzes only            |
| Admin       | Manage quizzes and questions |
| Super Admin | Manage admins + full access  |

Authorization rules are enforced at the service layer to prevent unauthorized actions.

---

## ⚙️ Error Handling & Reliability

The bot is designed to remain stable even during failures:

* ✅ Centralized exception handling using custom exceptions
* ✅ Graceful degradation with **Resilience4j Circuit Breaker**
* ✅ Validation checks for invalid quiz flows
* ✅ Safe session cleanup to avoid memory leaks
* ✅ User-friendly error messages instead of crashes
* ✅ Database connection recovery handling

This ensures high availability and predictable behavior in production.

---

## 🏗️ System Architecture

```
User (Telegram)
   │
   ▼
Telegram Bot API
   │
   ▼
Spring Boot Application (Docker)
   │
   ├── Service Layer
   ├── Repository Layer (JPA)
   └── Security / Validation
   │
   ▼
PostgreSQL Database (Render)
```

* Stateless backend for horizontal scalability
* Persistent data stored in PostgreSQL
* Environment-based configuration

---

## 🐳 Docker Architecture

* Multi-stage Docker build
* Lightweight runtime image
* Containerized app ensures consistent deployments

### Run Locally

```bash
docker compose up --build
```

---

## 🌍 Deployment

* Hosted on **Render Cloud**
* Docker image built automatically
* Environment variables configured securely
* PostgreSQL provisioned as managed database

---

## 🔄 CI/CD Pipeline

* GitHub Actions pipeline
* Automatic build on push
* Docker image validation
* Prevents broken deployments

---

## 🧪 Tech Stack

| Layer            | Technology      |
| ---------------- | --------------- |
| Language         | Java 21         |
| Framework        | Spring Boot     |
| Database         | PostgreSQL      |
| ORM              | Spring Data JPA |
| Resilience       | Resilience4j    |
| Containerization | Docker          |
| CI/CD            | GitHub Actions  |
| Cloud            | Render          |

---

## 🔑 Environment Variables

Create a `.env` file locally:

```
BOT_TOKEN=your_bot_token
BOT_USERNAME=your_bot_username
DB_URL=jdbc:postgresql://host:port/db
DB_USERNAME=your_db_user
DB_PASSWORD=your_db_password
```

> Never commit real secrets to GitHub.

---

## 📂 Project Structure

```
src/
 ├── controller/
 ├── service/
 ├── repository/
 ├── model/
 ├── config/
 └── exception/
Dockerfile
docker-compose.yml
pom.xml
```

---

## 🧠 Key Learnings

* Designing secure role-based systems
* Building fault-tolerant services
* Docker networking and image optimization
* Cloud deployment pipelines
* Debugging production failures
* Secrets management best practices

---

## 📸 Screenshots & Demo


### 📝 Quiz Flow
![Quiz Flow](screenshots/quiz-flow.png)

### 🛡️ Admin Controls
![Admin Panel](screenshots/admin-panel.png)



---

## 📈 Future Enhancements

* Leaderboards
* Timer-based quizzes
* Admin dashboard UI
* Analytics and reporting
* Webhooks monitoring

---

## 🤝 Contributing

Pull requests are welcome. For major changes, please open an issue first.

---

## 📄 License

MIT License
