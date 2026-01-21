# 📘 Telegram Quiz Bot

## Project Overview

A Spring Boot based Telegram bot that allows users to take quizzes,
answer questions, and track scores. Admins can create quizzes and manage
questions. The system is Dockerized and deployed on cloud using Render.

## Technologies

-   Java 21
-   Spring Boot 3
-   PostgreSQL
-   Spring Data JPA
-   Telegram Bot API
-   Resilience4j
-   Maven
-   Docker & Docker Compose
-   Render Cloud
-   GitHub

## Architecture

User → Telegram → Spring Boot → PostgreSQL

## Features

### User

-   Take quiz
-   Answer questions
-   View score

### Admin

-   Create quiz
-   Add questions
-   Delete quizzes

### System

-   Circuit Breaker
-   Auto cleanup sessions
-   Secure secrets using environment variables
-   Docker deployment

## Run Locally

``` bash
mvn clean package -DskipTests
docker compose up --build
```

## Dockerfile

``` dockerfile
FROM eclipse-temurin:21-jdk
WORKDIR /app
COPY target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java","-jar","app.jar"]
```

## Security

-   Secrets stored in .env
-   .env ignored by git
-   Environment variables used in production

## Deployment

-   Push code to GitHub
-   Create Render Web Service
-   Add environment variables
-   Deploy using Docker

## Future Improvements

-   Leaderboard
-   Redis cache
-   Admin dashboard
-   Kubernetes deployment
