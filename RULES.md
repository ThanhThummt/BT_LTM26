# Antigravity Project Rules — TCP/IP Bidirectional Database Synchronization

## 1. Project goal

Build a small educational Java application for the Computer Networks course.

**Project title:** Build a program to synchronize two databases on two computers using TCP/IP.

The application must synchronize student records in both directions:

- Computer A can send changes to Computer B.
- Computer B can send changes to Computer A.
- Each computer has its own local MySQL database.
- The two computers communicate through Java TCP sockets.

The main learning objectives are:
1. Understand TCP client/server communication.
2. Understand how Java sends and receives application messages over a socket.
3. Connect Java to MySQL using JDBC.
4. Apply received data to the local database.
5. Understand basic bidirectional synchronization and duplicate-message prevention.

This is a **course exercise, not a production system or graduation project**. Prefer the simplest implementation that demonstrates these concepts correctly.

## 2. Non-negotiable scope limits

Keep the implementation small and easy for a student to explain.

- Use Java.
- Use TCP sockets (`ServerSocket` and `Socket`) directly.
- Use MySQL databases managed locally with XAMPP/phpMyAdmin.
- Use JDBC for database access.
- Start with a console interface. Add a small Swing interface only after the console version works, and only if requested.
- Use one simple table of student records.
- Support INSERT, UPDATE, and DELETE synchronization in both directions.
- Provide a simple manual **Sync now** operation. Do not implement continuous real-time synchronization unless explicitly requested later.
- Use one TCP listening port, configurable in a settings file or command-line arguments.
- The same Java application should run on both computers. Only local database settings, listening port, and peer IP address may differ.

Do NOT add:
- Spring Boot, Hibernate, JPA, microservices, REST APIs, message brokers, Docker, Kubernetes, cloud services, or distributed database products.
- User accounts, roles, authentication systems, elaborate dashboards, or unrelated business features.
- Complex thread pools, plugin systems, dependency injection frameworks, or excessive design patterns.
- Unnecessary abstractions or large numbers of classes.

If a feature is not required to demonstrate TCP/IP database synchronization, do not implement it without asking first.

## 3. Required technology

- Java: use the Java version already installed in the project environment. Do not upgrade the JDK without asking.
- Networking: `java.net.ServerSocket`, `java.net.Socket`.
- Database: MySQL.
- Database access: JDBC with MySQL Connector/J.
- Data format: simple JSON messages. Use a small JSON library only if needed; do not build a complicated serialization framework.
- Build system: follow the existing project setup. If no build system exists, prefer a simple Eclipse-compatible Java project. Do not introduce Maven or Gradle without a clear need.

Keep dependencies minimal and document how to add the MySQL JDBC driver and any JSON library.

## 4. Two-computer architecture

Both computers run the same application.

**Computer A**
- Local database: `student_db`
- TCP server: listens on the configured port.
- TCP client: connects to Computer B when the user starts synchronization.
- Local changes are stored in the local database and its outgoing change log.

**Computer B**
- Local database: `student_db`
- TCP server: listens on the configured port.
- TCP client: connects to Computer A when the user starts synchronization.
- Local changes are stored in the local database and its outgoing change log.

Each computer can act as both TCP server and TCP client. Do not describe one computer as permanently master and the other as permanently slave.

Use a simple flow:

1. A user adds, edits, or deletes a student locally.
2. The application records that local change as a pending outgoing event.
3. When the user selects **Sync now**, the application connects to the peer over TCP.
4. It sends pending events.
5. The peer validates and applies each event to its own MySQL database.
6. The peer replies with success or failure for each event.
7. The sender marks an event as sent only after receiving a successful acknowledgement.
8. The receiver must not create a new outgoing event for a change that came from the peer.

The same process works in the opposite direction.

For the first version, synchronization may be initiated manually from either computer. Do not attempt simultaneous bidirectional connections unless needed. A simple request/response exchange is sufficient.

## 5. Simple database design

Use the same schema on both computers.

### 5.1 Student table

Create `student_db` and a `students` table with fields similar to:

- `id`: integer primary key, chosen by the user or generated consistently.
- `name`: student name.
- `email`: email address.
- `major`: major or department.

Keep the schema small. Do not add unrelated fields.

### 5.2 Change log / outgoing events

Use a small change-log table to remember local changes that still need to be sent. A suitable design may include:

- `event_id`: globally unique event identifier (UUID), primary key.
- `student_id`: affected student ID.
- `operation`: `INSERT`, `UPDATE`, or `DELETE`.
- `payload`: student data for INSERT/UPDATE; may be empty for DELETE.
- `created_at`: event creation time.
- `is_sent`: whether the peer acknowledged this event.

Use a tombstone event for DELETE. Do not rely on the deleted student row still existing.

### 5.3 Received-event tracking

Use a small `received_events` table containing `event_id` values already applied by this computer.

Before applying an incoming event:
1. Check whether its `event_id` has already been received.
2. If yes, do not apply it again; return a successful duplicate acknowledgement.
3. If no, apply the database operation and record the event ID in one database transaction.

This makes message processing idempotent and prevents duplicate application if an acknowledgement is lost and the sender retries.

Keep this mechanism understandable. Explain it with comments and in the README.

## 6. TCP communication protocol

Use TCP, not UDP.

TCP is a byte stream, so the program must define message boundaries. For this small project, use **one JSON message per line** (newline-delimited JSON). Read with `BufferedReader.readLine()` and write with `BufferedWriter`, followed by a newline and `flush()`.

Do not assume one `read()` call receives one complete message.

Example event:

```json
{
  "type": "CHANGE",
  "eventId": "a-unique-uuid",
  "operation": "INSERT",
  "studentId": 1,
  "student": {
    "name": "Nguyen Van An",
    "email": "an@example.com",
    "major": "Information Technology"
  }
}
```

For DELETE, `student` can be null or omitted, but `studentId` and `eventId` are required.

Example acknowledgement:

```json
{
  "type": "ACK",
  "eventId": "a-unique-uuid",
  "status": "OK",
  "message": "Applied"
}
```

For an error, return `status: "ERROR"` and a short useful message.

Protocol rules:
- Include a message `type`, such as `CHANGE` and `ACK`.
- Include a unique `eventId` for every change.
- Validate required fields and allowed operations.
- Set reasonable socket connect/read timeouts.
- Close sockets and streams using try-with-resources.
- Do not send SQL statements over the network. Send data and operations only.
- Use `PreparedStatement`; never concatenate user input into SQL.
- Do not log database passwords or other secrets.

## 7. Conflict handling — keep it deliberately simple

This is an educational exercise, so do not build a sophisticated conflict-resolution engine.

Use and document one simple rule:

- Each student record has a `updated_at` timestamp, or an equivalent version field.
- When an incoming update is newer than the local record, apply it.
- When it is older, keep the local record.
- If timestamps are equal and the records differ, use a clearly documented deterministic tie-break rule, or report a conflict and skip automatic overwrite.

For DELETE, use a deletion event/tombstone with its timestamp so an older UPDATE does not silently recreate a deleted record.

Do not claim that timestamps provide perfect conflict resolution across computers. Mention that clock differences can affect this simple rule. The primary demo should avoid editing the same student on both computers at exactly the same time.

## 8. Prevent synchronization loops

This requirement is essential.

When a computer receives and applies a remote event:
- Update its local `students` table.
- Record the event in `received_events`.
- Do NOT add that remote event to its outgoing change log.

Only changes made locally by the user should create new outgoing events.

Do not implement synchronization by repeatedly copying all rows back and forth. Synchronize only pending change events.

## 9. Error handling

Handle common classroom-demo errors clearly:

- Peer IP address is invalid.
- Peer computer is unreachable.
- TCP port is blocked or not listening.
- MySQL is stopped or connection settings are incorrect.
- Incoming JSON is invalid or incomplete.
- Operation is unsupported.
- Database update fails.
- Peer disconnects during synchronization.
- An event is sent again after a lost acknowledgement.

If the peer is unavailable, keep outgoing events pending. Do not mark them as sent.

Display concise messages such as:
- `Connected to peer`
- `Connection failed`
- `Sent event ...`
- `Received event ...`
- `Sync completed`
- `Some events are still pending`

Do not silently discard failed events.

## 10. Suggested simple source structure

Use a small structure. Adjust it to the existing repository rather than creating duplicate projects or files.

```text
DatabaseSync/
├── src/
│   ├── Main.java
│   ├── config/
│   │   └── AppConfig.java
│   ├── database/
│   │   └── DatabaseConnection.java
│   ├── model/
│   │   └── Student.java
│   ├── network/
│   │   ├── TcpServer.java
│   │   └── TcpClient.java
│   └── sync/
│       └── SyncManager.java
├── sql/
│   └── student_db.sql
├── config.example.properties
└── README.md
```

Do not create a class for every tiny operation. Keep code readable and suitable for a beginner to explain.

## 11. Implementation order

Work incrementally. Do not generate the entire application in one step.

**Stage 1 — Inspect and prepare**
- Inspect the current workspace before editing.
- Report existing files and project setup.
- Do not overwrite or delete existing user files.
- Create the minimal project structure.

**Stage 2 — Database**
- Write the SQL setup script.
- Create the same schema on both computers.
- Test JDBC connection locally on each computer.
- Test local INSERT, UPDATE, and DELETE.

**Stage 3 — TCP fundamentals**
- First create a minimal TCP server and client that exchange a text message.
- Run them on two computers.
- Verify IP address, port, firewall, connection, sending, and receiving.
- Only after this works, add JSON messages.

**Stage 4 — One-way event transfer**
- Send one student-change event from A to B.
- Receive, validate, apply it to B's database, and return an ACK.
- Test INSERT, UPDATE, and DELETE.

**Stage 5 — Bidirectional synchronization**
- Run the same application on both computers.
- Allow either side to initiate synchronization.
- Add pending-event handling and received-event duplicate detection.
- Prevent remote events from being re-added to the outgoing log.

**Stage 6 — Test and document**
- Test both directions.
- Test peer offline and reconnect.
- Test duplicate event delivery.
- Prepare a short demo checklist and README.

At the end of each stage:
1. List files created or changed.
2. Explain the code in beginner-friendly Vietnamese.
3. Give exact commands or Eclipse steps to run it.
4. Give expected output.
5. Run available tests or provide a concrete manual test.
6. Stop and wait for the user to confirm before moving to the next stage.

## 12. Two-computer demo requirements

The final README must explain how to run the program on two physical computers.

Include:
- How to find each computer's LAN IP address (`ipconfig` on Windows).
- How to start MySQL/XAMPP on both computers.
- How to import `sql/student_db.sql` on both computers.
- How to configure each computer's local database connection.
- How to configure the peer IP and TCP port.
- How to allow the Java application through Windows Firewall or open the selected TCP port.
- Which computer starts the TCP listener and how the other connects.
- How either computer can initiate a synchronization.
- Expected results for INSERT, UPDATE, and DELETE.
- How to demonstrate a disconnected peer and later retry synchronization.

Never assume both computers have the same IP address. Never use `localhost` as the peer address: `localhost` refers to the current computer. `localhost` is acceptable only for that computer's own MySQL connection.

## 13. Testing checklist

The implementation is not complete until these basic tests pass:

1. Both computers can connect to their own MySQL database.
2. A can establish a TCP connection to B.
3. B can receive a complete message from A.
4. A local INSERT on A appears in B after synchronization.
5. A local UPDATE on B appears in A after synchronization.
6. A local DELETE on A is reflected on B.
7. Re-sending the same event does not apply it twice.
8. A remote event does not create an endless synchronization loop.
9. If the peer is offline, pending events remain pending.
10. After reconnecting, pending events can be synchronized.

## 14. Code quality and teaching requirements

- Write straightforward Java suitable for a student learning networking.
- Use meaningful class, method, and variable names.
- Add comments explaining socket operations, message framing, JDBC operations, ACK handling, and duplicate prevention.
- Explain important code in Vietnamese.
- Avoid unexplained advanced syntax and unnecessary abstractions.
- Keep database credentials in a local configuration file that is excluded from Git. Provide only a `config.example.properties` with placeholder values.
- Do not commit passwords, API keys, or personal IP information.
- Do not state that a test passed unless it was actually run and its result was observed.
- If a requirement is ambiguous, ask before making a large architectural decision.
- If a requested feature would significantly increase complexity, explain the trade-off and ask before implementing it.

## 15. Final deliverables

The final submission should contain only:
- Java source code.
- SQL setup script.
- Example configuration file.
- README with setup, run, protocol, and two-computer demo instructions.
- A few screenshots or terminal outputs proving the demo.

The final application must clearly demonstrate that two separate MySQL databases on two separate computers can exchange INSERT, UPDATE, and DELETE changes over TCP/IP in both directions.

**Priority:** correct networking fundamentals, understandable code, reliable small-scale synchronization, and a successful two-computer demo — not feature count or production-level complexity.
