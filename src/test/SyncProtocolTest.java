package test;

import model.AckMessage;
import model.Student;
import model.SyncEvent;
import sync.JsonUtils;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Kiểm thử tự động giao thức TCP, tuần tự hóa JSON, thông điệp ACK và phân định dòng.
 */
public class SyncProtocolTest {

    public static void main(String[] args) {
        System.out.println("========== BAT DAU KIEM THU GIAO THUC VA TCP ==========");
        int passed = 0;
        int total = 0;

        // Test 1: Tuần tự hóa và giải mã Student
        total++;
        if (testStudentJson()) {
            System.out.println("[PASS] Test 1: Chuyen doi JSON cho doi tuong Student thanh cong.");
            passed++;
        } else {
            System.err.println("[FAIL] Test 1: Chuyen doi JSON cho Student that bai!");
        }

        // Test 2: Tuần tự hóa và giải mã SyncEvent (bao gồm ký tự tiếng Việt)
        total++;
        if (testSyncEventJson()) {
            System.out.println("[PASS] Test 2: Chuyen doi JSON cho SyncEvent (Unicode ho va ten) thanh cong.");
            passed++;
        } else {
            System.err.println("[FAIL] Test 2: Chuyen doi JSON cho SyncEvent that bai!");
        }

        // Test 3: Tuần tự hóa và giải mã AckMessage
        total++;
        if (testAckJson()) {
            System.out.println("[PASS] Test 3: Chuyen doi JSON cho AckMessage thanh cong.");
            passed++;
        } else {
            System.err.println("[FAIL] Test 3: Chuyen doi JSON cho AckMessage that bai!");
        }

        // Test 4: Giao thức truyền tin qua TCP Socket thực tế (ServerSocket <-> Socket)
        total++;
        if (testTcpSocketCommunication()) {
            System.out.println("[PASS] Test 4: Ket noi TCP, truyen nhan JSON qua Socket va tra ve ACK thanh cong.");
            passed++;
        } else {
            System.err.println("[FAIL] Test 4: Ket noi TCP Socket that bai!");
        }

        System.out.printf("\nKET QUA KIEM THU: %d/%d test cases PASSED!\n", passed, total);
        System.out.println("=======================================================");

        if (passed != total) {
            System.exit(1);
        }
    }

    private static boolean testStudentJson() {
        Student s1 = new Student(101, "Nguyen Van An", "an@example.com", "Cong nghe thong tin", 1700000000000L);
        String json = JsonUtils.studentToJson(s1);
        Student s2 = JsonUtils.parseStudent(json);
        return s2 != null && s2.getId() == 101 && s1.getName().equals(s2.getName()) && s1.getMajor().equals(s2.getMajor());
    }

    private static boolean testSyncEventJson() {
        Student s = new Student(202, "Trần Thị Bình", "binh@example.com", "An toàn thông tin", 1700000050000L);
        String eventId = UUID.randomUUID().toString();
        SyncEvent ev1 = new SyncEvent(eventId, "INSERT", 202, s, 1700000050000L);

        String jsonLine = JsonUtils.eventToJson(ev1);
        // Đảm bảo không chứa ký tự xuống dòng bên trong để giữ đúng newline-delimited JSON
        if (jsonLine.contains("\n") || jsonLine.contains("\r")) {
            return false;
        }

        SyncEvent ev2 = JsonUtils.parseEvent(jsonLine);
        return ev2 != null && eventId.equals(ev2.getEventId())
                && "INSERT".equals(ev2.getOperation())
                && ev2.getStudentId() == 202
                && ev2.getStudent() != null
                && "Trần Thị Bình".equals(ev2.getStudent().getName());
    }

    private static boolean testAckJson() {
        AckMessage ack1 = new AckMessage("test-uuid-1234", "OK", "Applied successfully");
        String json = JsonUtils.ackToJson(ack1);
        AckMessage ack2 = JsonUtils.parseAck(json);
        return ack2 != null && "test-uuid-1234".equals(ack2.getEventId())
                && ack2.isSuccess()
                && "Applied successfully".equals(ack2.getMessage());
    }

    private static boolean testTcpSocketCommunication() {
        final int testPort = 19876;
        final String testEventId = UUID.randomUUID().toString();

        Thread serverThread = new Thread(() -> {
            try (ServerSocket server = new ServerSocket(testPort)) {
                Socket client = server.accept();
                BufferedReader reader = new BufferedReader(new InputStreamReader(client.getInputStream(), StandardCharsets.UTF_8));
                BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(client.getOutputStream(), StandardCharsets.UTF_8));

                String line = reader.readLine();
                SyncEvent receivedEvent = JsonUtils.parseEvent(line);

                AckMessage ack;
                if (receivedEvent != null && testEventId.equals(receivedEvent.getEventId())) {
                    ack = new AckMessage(testEventId, "OK", "Test Applied");
                } else {
                    ack = new AckMessage("UNKNOWN", "ERROR", "Mismatch");
                }

                writer.write(JsonUtils.ackToJson(ack));
                writer.newLine();
                writer.flush();

                client.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        serverThread.start();

        // Client test
        try {
            Thread.sleep(200); // Đợi server mở port
            try (Socket socket = new Socket("127.0.0.1", testPort);
                 BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
                 BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))) {

                SyncEvent event = new SyncEvent(testEventId, "UPDATE", 303, null, System.currentTimeMillis());
                writer.write(JsonUtils.eventToJson(event));
                writer.newLine();
                writer.flush();

                String ackLine = reader.readLine();
                AckMessage ack = JsonUtils.parseAck(ackLine);
                return ack != null && ack.isSuccess() && testEventId.equals(ack.getEventId());
            }
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
}
