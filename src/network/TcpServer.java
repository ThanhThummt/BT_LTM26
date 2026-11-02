package network;

import config.AppConfig;
import database.StudentDAO;
import model.AckMessage;
import model.SyncEvent;
import sync.JsonUtils;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * TCP Server chạy ngầm để lắng nghe các kết nối đồng bộ từ máy đối tác.
 */
public class TcpServer implements Runnable {
    private final int port;
    private final StudentDAO studentDAO;
    private volatile boolean running = true;
    private ServerSocket serverSocket;
    private SyncListener listener;

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");

    public TcpServer(int port, StudentDAO studentDAO) {
        this.port = port;
        this.studentDAO = studentDAO;
    }

    public void setListener(SyncListener listener) {
        this.listener = listener;
    }

    private String getNow() {
        return LocalTime.now().format(TIME_FMT);
    }

    @Override
    public void run() {
        try {
            serverSocket = new ServerSocket(port);
            System.out.printf("[TCP Server] Dang lang nghe ket noi dong bo tai cong %d...\n", port);
            if (listener != null) {
                listener.onServerStatus(true, "Server đang chạy tại port " + port);
                listener.onLog(getNow(), "HỆ THỐNG", "SERVER", "TCP Server đã khởi động tại cổng " + port);
            }

            while (running) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    handleClient(clientSocket);
                } catch (IOException e) {
                    if (!running) {
                        break;
                    }
                    System.err.println("[TCP Server] Loi khi chap nhan ket noi: " + e.getMessage());
                }
            }
        } catch (IOException e) {
            System.err.printf("[TCP Server] Khong the khoi tao ServerSocket tren cong %d: %s\n", port, e.getMessage());
            if (listener != null) {
                listener.onServerStatus(false, "Lỗi: " + e.getMessage());
                listener.onLog(getNow(), "LỖI", "SERVER", "Không thể mở cổng " + port + ": " + e.getMessage());
            }
        } finally {
            stop();
        }
    }

    private void handleClient(Socket socket) {
        String remoteAddress = socket.getRemoteSocketAddress().toString();
        System.out.println("\n[TCP Server] Nhan ket noi tu: " + remoteAddress);
        if (listener != null) {
            listener.onLog(getNow(), "KẾT NỐI", "TCP", "Nhận kết nối từ đối tác: " + remoteAddress);
        }

        try (socket;
             BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
             BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8))) {

            socket.setSoTimeout(AppConfig.getInstance().getNetworkTimeout());

            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;
                if ("QUIT".equalsIgnoreCase(line)) break;

                // 1. Phân tích thông điệp sự kiện
                SyncEvent event = JsonUtils.parseEvent(line);
                AckMessage ack;

                if (event != null && "CHANGE".equalsIgnoreCase(event.getType())) {
                    System.out.printf("[TCP Server] Nhan su kien: ID=%s, Thao tac=%s, SVID=%d\n",
                            event.getEventId(), event.getOperation(), event.getStudentId());

                    try {
                        // 2. Áp dụng sự kiện vào MySQL địa phương (Không ghi vào outgoing log!)
                        boolean applied = studentDAO.applyRemoteEvent(event);
                        if (applied) {
                            ack = new AckMessage(event.getEventId(), "OK", "Applied successfully");
                            if (listener != null) {
                                listener.onRemoteDataApplied(event);
                            }
                        } else {
                            ack = new AckMessage(event.getEventId(), "ERROR", "Could not apply event");
                        }
                    } catch (Exception ex) {
                        System.err.println("[TCP Server] Loi khi ap dung su kien: " + ex.getMessage());
                        ack = new AckMessage(event.getEventId(), "ERROR", ex.getMessage());
                    }

                    if (listener != null) {
                        listener.onLog(getNow(), "NHẬN", event.getOperation(),
                                String.format("Nhận sự kiện [%s] SV ID %d -> Phản hồi ACK %s (%s)",
                                        event.getOperation(), event.getStudentId(), ack.getStatus(), ack.getMessage()));
                    }
                } else {
                    ack = new AckMessage("UNKNOWN", "ERROR", "Invalid message format");
                    if (listener != null) {
                        listener.onLog(getNow(), "LỖI", "FORMAT", "Nhận thông điệp không đúng định dạng JSON CHANGE");
                    }
                }

                // 3. Trả về thông điệp phản hồi ACK (1 dòng JSON kết thúc bằng \n)
                String ackJson = JsonUtils.ackToJson(ack);
                writer.write(ackJson);
                writer.newLine();
                writer.flush();
            }
        } catch (IOException e) {
            System.err.printf("[TCP Server] Mat ket noi voi %s: %s\n", remoteAddress, e.getMessage());
            if (listener != null) {
                listener.onLog(getNow(), "NGẮT", "TCP", "Đã ngắt kết nối với " + remoteAddress + " (" + e.getMessage() + ")");
            }
        }
    }

    public void stop() {
        running = false;
        if (serverSocket != null && !serverSocket.isClosed()) {
            try {
                serverSocket.close();
            } catch (IOException ignored) {
            }
        }
        if (listener != null) {
            listener.onServerStatus(false, "Server đã dừng");
            listener.onLog(getNow(), "HỆ THỐNG", "SERVER", "TCP Server đã dừng.");
        }
    }
}
