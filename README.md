# Trulo Meetup Tracker (Spring Boot Backend)

This repository contains the **Java 11 / Spring Boot** backend for the Trulo Meetup Tracker. It serves a React frontend and provides RESTful APIs for user authentication and meetup management, along with real-time WebSocket location tracking.

> **Note:** This project is a final year college submission built collaboratively by our team.

## Features

- **Spring Boot 2.7.x:** Robust and scalable core framework.
- **Spring Security & JWT:** Stateless authentication securing API routes.
- **Spring Data MongoDB:** Maps complex collections (like nested Participants and Locations) to Java Objects.
- **Netty-SocketIO:** Provides a Java implementation of the WebSocket server, allowing the React frontend's Socket client to communicate smoothly.
- **Seamless Frontend Integration:** Fully tested End-to-End with the React application.

## Tech Stack

- **Java 11**
- **Spring Boot 2.7.18**
- **Spring Security**
- **Spring Data MongoDB**
- **JJWT (Java JWT)**
- **Netty-SocketIO**
- **Lombok**
- **Maven**

## Prerequisites

- **Java 11 SDK**
- **Maven 3.6+**
- **MongoDB** running locally on default port `27017`

## Setup and Installation

1. **Clone the repository:**
   ```bash
   git clone <your-github-repo-url>
   cd trulo-spring-backend
   ```

2. **Configure MongoDB:**
   Ensure MongoDB is running locally. The application is configured to connect to `mongodb://localhost:27017/trulo-spring`. You can change this in `src/main/resources/application.properties`.

3. **Build the project:**
   ```bash
   mvn clean install
   ```

4. **Run the application:**
   ```bash
   mvn spring-boot:run
   ```

   The backend will start on **port 5000** for HTTP requests, and the Netty Socket.IO server will start on **port 5001**.

## Team Contributions

This final year college project was divided among our team members to ensure a timely and robust delivery:
- **Backend Architecture (Primary Focus):** Handled the core backend development from scratch using Spring Boot, including REST API design, MongoDB `@Document` mapping, and Spring Security JWT implementation.
- **Frontend Integration:** The rest of the team managed the React frontend components, ensuring seamless communication with the Spring backend logic.
- **Real-time WebSockets:** Collaboratively engineered the real-time `netty-socketio` logic to sync locations flawlessly and resolved complex cross-origin authentication requirements.
