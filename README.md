# FileServer

A socket-based, cross-platform LAN file server built from scratch in Java — no frameworks, no HTTP, raw TCP sockets only.

---

## What it does

FileServer works like a company internal file server — one machine runs the server, everyone else on the same network connects as clients and can securely upload, download, and manage files.

- Authenticated multi-user access over TCP/IP
- Upload, download, list, delete files over LAN
- File sharing via time-limited tokens
- Role-based access — ADMIN and USER roles
- Handles 10 simultaneous clients via thread pool
- File locking prevents concurrent write corruption
- Cross-platform — runs on Windows and Linux

---

## Architecture

```
┌─────────────────────────────────────────────────────┐
│                    LAN Network                      │
│                                                     │
│  ┌──────────────────┐         ┌──────────────────┐  │
│  │   ServerApp      │◄───────►│   ClientApp      │  │
│  │  (JavaFX GUI)    │  TCP    │  (JavaFX GUI)    │  │
│  │                  │ :5000   │                  │  │
│  │  FileServer.java │         │  FileClient.java │  │
│  │  ClientHandler   │         │  StressTest.java │  │
│  │  AuthService     │         └──────────────────┘  │
│  │  FileService     │                               │
│  │  UserStore       │                               │
│  │                  │                               │
│  │  fileserver.db   │                               │
│  │  storage/        │                               │
│  └──────────────────┘                               │
└─────────────────────────────────────────────────────┘
```

## Tech Stack

| Component | Technology |
|---|---|
| Language | Java 17 |
| Networking | java.net.ServerSocket, Socket |
| File I/O | BufferedInputStream, DataOutputStream |
| Concurrency | ExecutorService, FileLock |
| Security | SHA-256, path traversal protection |
| Database | SQLite via JDBC |
| GUI | JavaFX 17 |
| Build | Maven |
| Version Control | Git + GitHub |
| Frameworks | None — zero frameworks for core logic |

---

## Project Structure

```
src/main/java/com/fileserver/
├── server/
│   ├── FileServer.java          Terminal server (legacy)
│   ├── ServerApp.java           JavaFX server GUI
│   ├── ClientHandler.java       Per-client thread handler
│   ├── AuthService.java         Authentication logic
│   ├── FileService.java         File operations engine
│   └── UserStore.java           SQLite user management
└── client/
    ├── FileClient.java          Terminal client (legacy)
    ├── ClientApp.java           JavaFX client GUI
    └── StressTest.java          Concurrent load test
```

## Custom Protocol

All commands travel over TCP using DataOutputStream.writeUTF().
File bytes travel as raw binary in 8KB chunks.

| Command | Response | Description |
|---|---|---|
| AUTH\|username\|password | OK\|Welcome name | First packet — must authenticate before anything |
| LIST | FILES\|file1,file2 | List files in your storage folder |
| UPLOAD\|filename\|size | READY then DONE | Stream file bytes to server |
| DOWNLOAD\|filename | SIZE\|bytes then raw bytes | Server streams file to client |
| DELETE\|filename | OK or ERROR | Delete file with path traversal protection |
| REGISTER\|username\|password | OK or ERROR | Admin only — create new user |
| LISTUSERS | USERS\|user1,user2 | Admin only — list all users |
| SHARE\|filename | OK\|Token: xyz | Generate 24hr share token |
| ACCESS\|token | SIZE\|bytes then file | Download shared file via token |
| TOKENINFO\|token | INFO\|owner\|file\|expiry | Check token details |
| REVOKE\|token | OK or ERROR | Invalidate a share token |
| QUIT | OK\|Goodbye | Clean disconnect |

---
## Role-Based Access

### ADMIN
- Login from any machine on the LAN
- Register new users — `REGISTER|username|password`
- List all registered users — `LISTUSERS`
- See all users' files — `LIST` shows `username/filename`
- Download any user's file — `DOWNLOAD|username/filename`
- Delete any user's file — `DELETE|username/filename`
- Upload, download, delete own files
- Share files and manage tokens
- Revoke any user's share token

### USER
- Login from any machine on the LAN
- Upload files to own storage folder
- Download own files
- Delete own files
- List own files only
- Share own files — generates 24hr token
- Access shared files from other users via token
- Check token info before downloading
- Cannot see other users' files
- Cannot register new users
- Cannot list users
- Cannot access admin commands
- 
## How to Run

### Prerequisites
- Java 17 installed on both machines
- Both machines on the same LAN (Wi-Fi or Ethernet)

### Run the Server

**JavaFX GUI version:**
Run ServerApp.java from IntelliJ with VM options:
--module-path "path/to/javafx/lib" --add-modules javafx.controls,javafx.fxml

**Terminal version:**
```bash
java -cp "target/classes:sqlite-jdbc.jar:slf4j-api.jar" com.fileserver.server.FileServer
```

Click **Start Server**. Note the LAN IP displayed — share it with clients.

Default admin account: `admin` / `admin123`

### Run the Client

**JavaFX GUI version:**

Run ClientApp.java from IntelliJ with same VM options

Enter server LAN IP, username, and password. Click Connect.

### Run Stress Test

Run StressTest.java — requires FileServer running and stress1-stress10 users registered

---

## Security

- Passwords stored as SHA-256 hashes — plain text never stored
- First packet must be AUTH — no commands without authentication
- Path traversal protection — users cannot escape their storage folder
- User sandboxing — each user has their own isolated directory
- Role-based access — ADMIN controls user management
- Share tokens expire after 24 hours automatically

---

## Known Limitations

| Limitation | Planned Fix |
|---|---|
| No TLS encryption | Wrap Socket in SSLSocket |
| SHA-256 for passwords | Migrate to bcrypt or Argon2 |
| No resumable uploads | Chunked protocol with byte offsets |
| LAN only | Port forwarding + dynamic DNS for internet access |
| No file versioning | Timestamp-based version history |

---

## Cross-Platform Test Results

| Component | Platform | Status |
|---|---|---|
| FileServer (terminal) | WSL Ubuntu — Java 17 | ✅ Verified |
| ClientApp (JavaFX) | Windows 11 — Java 17 | ✅ Verified |
| File Upload | Windows client → Linux server | ✅ Verified |
| File Download | Linux server → Windows client | ✅ Verified |
| LAN IP routing | 192.168.100.111 | ✅ Verified |

---

## Stress Test Results

| Metric | Result |
|---|---|
| Simultaneous clients | 10 |
| Successful clients | 10 / 10 |
| Failed clients | 0 |
| File size per client | 97,890 bytes |
| Byte verification | Exact match — zero corruption |

---

## Developer

**Shashank Zarikar**
Computer Science
SGGS Institute of Engineering and Technology
