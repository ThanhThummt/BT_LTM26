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
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * TCP Client kết nối tới máy đối tác để gửi các sự kiện thay đổi dữ liệu đang chờ (Pending).
 */
public class TcpClient {
    private final String peerHost;
    private final int peerPort;
    private final int timeout;

    public TcpClient(String peerHost, int peerPort, int timeout) {
        this.peerHost = peerHost;
        this.peerPort = peerPort;
        this.timeout = timeout;
    }

    /**
     * Gửi danh sách các sự kiện chờ đồng bộ sang máy đối tác qua TCP.
     * @return Số lượng sự kiện đã đồng bộ thành công và nhận ACK OK.
     */
    public int sendPendingEvents(List<SyncEvent> events, StudentDAO studentDAO) {
        return sendPendingEvents(events, studentDAO, null);
    }

    /**
     * Gửi danh sách các sự kiện chờ đồng bộ sang máy đối tác qua TCP có hỗ trợ báo cáo cho Listener.
     * @return Số lượng sự kiện đã đồng bộ thành công và nhận ACK OK.
     */
    public int sendPendingEvents(List<SyncEvent> events, StudentDAO studentDAO, SyncListener listener) {
        java.time.format.DateTimeFormatter timeFmt = java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss");
        String now = java.time.LocalTime.now().format(timeFmt);

        if (events == null || events.isEmpty()) {
            System.out.println("[TCP Client] Khong co su kien nao can dong bo.");
            if (listener != null) {
                listener.onLog(now, "HỆ THỐNG", "SYNC", "Không có sự kiện nào cần đồng bộ. CSDL đã đồng nhất.");
            }
            return 0;
        }

        System.out.printf("[TCP Client] Bat dau ket noi toi may doi tac (%s:%d)...\n", peerHost, peerPort);
        if (listener != null) {
            listener.onLog(now, "KẾT NỐI", "TCP", "Đang kết nối tới máy đối tác: " + peerHost + ":" + peerPort);
        }

        int successCount = 0;
        try (Socket socket = new Socket()) {
            // Thiet lap ket noi co timeout
            socket.connect(new InetSocketAddress(peerHost, peerPort), timeout);
            socket.setSoTimeout(timeout);

            now = java.time.LocalTime.now().format(timeFmt);
            System.out.println("[TCP Client] Ket noi thanh cong! Dang gui " + events.size() + " su kien...");
            if (listener != null) {
                listener.onLog(now, "KẾT NỐI", "TCP", "Kết nối thành công! Đang gửi " + events.size() + " sự kiện...");
            }

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
                 BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8))) {

                for (SyncEvent event : events) {
                    now = java.time.LocalTime.now().format(timeFmt);
                    // 1. Chuyen doi su kien sang dong JSON va gui qua TCP
                    String jsonLine = JsonUtils.eventToJson(event);
                    writer.write(jsonLine);
                    writer.newLine();
                    writer.flush();

                    System.out.printf("[TCP Client] Da gui su kien [%s] Thao tac=%s cho SV ID=%d\n",
                            event.getEventId(), event.getOperation(), event.getStudentId());

                    // 2. Cho nhan thong diep ACK tu may doi tac
                    String ackLine = reader.readLine();
                    if (ackLine == null) {
                        System.err.println("[TCP Client] May doi tac da ngat ket noi truoc khi tra loi ACK.");
                        if (listener != null) {
                            listener.onLog(now, "LỖI", "TCP", "Đối tác đã ngắt kết nối trước khi phản hồi ACK.");
                        }
                        break;
                    }

                    AckMessage ack = JsonUtils.parseAck(ackLine);
                    if (ack != null && ack.isSuccess() && event.getEventId().equals(ack.getEventId())) {
                        // 3. Chi danh dau is_sent = 1 khi nhan duoc ACK OK tu may doi tac
                        try {
                            studentDAO.markEventAsSent(event.getEventId());
                            successCount++;
                            System.out.printf("[TCP Client] Nhan ACK OK cho su kien [%s].\n", event.getEventId());
                            if (listener != null) {
                                listener.onLog(now, "GỬI", event.getOperation(),
                                        String.format("Gửi [%s] SV ID %d -> Nhận ACK OK: %s",
                                                event.getOperation(), event.getStudentId(), ack.getMessage()));
                            }
                        } catch (Exception ex) {
                            System.err.printf("[TCP Client] Loi khi cap nhat trang thai su kien vao DB: %s\n", ex.getMessage());
                        }
                    } else {
                        System.err.printf("[TCP Client] Nhan ACK LOI cho su kien [%s]: %s\n",
                                event.getEventId(), (ack != null ? ack.getMessage() : "Phan hoi khong hop le"));
                        if (listener != null) {
                            listener.onLog(now, "LỖI", event.getOperation(),
                                    String.format("Gửi [%s] SV ID %d -> Nhận ACK LỖI: %s",
                                            event.getOperation(), event.getStudentId(), (ack != null ? ack.getMessage() : "Không hợp lệ")));
                        }
                    }
                }

                // Gui tin hieu ket thuc phien
                writer.write("QUIT");
                writer.newLine();
                writer.flush();
            }

        } catch (SocketTimeoutException e) {
            now = java.time.LocalTime.now().format(timeFmt);
            System.err.println("[TCP Client] Qua thoi gian cho phan hoi tu may doi tac (Socket Timeout).");
            if (listener != null) {
                listener.onLog(now, "LỖI", "TIMEOUT", "Hết thời gian chờ phản hồi từ máy đối tác (Socket Timeout).");
            }
        } catch (IOException e) {
            now = java.time.LocalTime.now().format(timeFmt);
            System.err.printf("[TCP Client] Khong the ket noi toi may doi tac (%s:%d): %s\n", peerHost, peerPort, e.getMessage());
            System.err.println("[TCP Client] Luu y: Cac su kien van duoc luu o trang thai cho (Pending) de dong bo lai sau.");
            if (listener != null) {
                listener.onLog(now, "LỖI", "CONNECT",
                        String.format("Không thể kết nối tới %s:%d (%s). Sự kiện vẫn lưu Pending.", peerHost, peerPort, e.getMessage()));
            }
        }

        return successCount;
    }

    /**
     * Kiểm tra thử kết nối tới Server đối tác (Ping TCP).
     */
    public boolean testConnection() {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(peerHost, peerPort), Math.min(timeout, 3000));
            return true;
        } catch (IOException e) {
            return false;
        }
    }
}
