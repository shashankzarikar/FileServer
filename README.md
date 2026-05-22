# FileServer

A socket-based, cross-platform file server built in Java from scratch.

Built as part of my Zoho Nagpur internship project — demonstrates core Java networking concepts without any frameworks.

## What it does
- Authenticated multi-user file access over TCP/IP
- Upload, download, list, and delete files over a network
- Handles multiple clients simultaneously using a thread pool
- Custom protocol over raw Java Sockets — no Spring Boot, no HTTP

## Tech Stack
- Java 17
- java.net.ServerSocket / Socket
- java.nio.channels.FileLock
- ExecutorService (thread pool)
- JavaFX (desktop GUI)
- SQLite (user credentials)
- Maven

## Project Structure
src/
└── main/java/com/fileserver/
├── server/       ← Server-side code
└── client/       ← Client-side code

## Cross-Platform Test Results

| Component | Platform | Status |
|---|---|---|
| FileServer (terminal) | WSL Ubuntu 22.04 — Java 17 | ✅ Verified |
| ClientApp (JavaFX GUI) | Windows 11 — Java 17 | ✅ Verified |
| File Upload | Windows → Linux server | ✅ Verified |
| File Download | Linux server → Windows | ✅ Verified |
| Authentication | Cross-platform | ✅ Verified |

Server IP during test: 172.28.113.194 (WSL)
Client IP during test: 192.168.100.111 (Windows)