package sync;

import config.AppConfig;
import database.StudentDAO;
import model.SyncEvent;
import network.TcpClient;

import java.sql.SQLException;
import java.util.List;

/**
 * Trình quản lý quá trình đồng bộ dữ liệu.
 */
public class SyncManager {
    private final StudentDAO studentDAO;

    public SyncManager(StudentDAO studentDAO) {
        this.studentDAO = studentDAO;
    }

    /**
     * Thực hiện thao tác đồng bộ "Sync now" từ máy này sang máy đối tác.
     */
    public int performSync() {
        return performSync(null);
    }

    /**
     * Thực hiện đồng bộ có thông báo cho SyncListener.
     */
    public int performSync(network.SyncListener listener) {
        System.out.println("\n========== BAT DAU DONG BO (SYNC NOW) ==========");
        int success = 0;
        try {
            List<SyncEvent> pendingEvents = studentDAO.getPendingOutgoingEvents();
            if (pendingEvents.isEmpty()) {
                System.out.println("[SyncManager] Khong co su kien nao can dong bo. Co so du lieu da o trang thai dong nhat.");
                if (listener != null) {
                    listener.onLog(java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss")),
                            "HỆ THỐNG", "SYNC", "Không có sự kiện nào cần đồng bộ. CSDL đã đồng nhất.");
                }
                return 0;
            }

            System.out.printf("[SyncManager] Tim thay %d su kien dang cho gui (Pending).\n", pendingEvents.size());

            AppConfig config = AppConfig.getInstance();
            TcpClient client = new TcpClient(config.getPeerHost(), config.getPeerPort(), config.getNetworkTimeout());

            success = client.sendPendingEvents(pendingEvents, studentDAO, listener);

            System.out.printf("[SyncManager] Ket qua dong bo: Thanh cong %d / %d su kien.\n", success, pendingEvents.size());
            if (success < pendingEvents.size()) {
                System.out.println("[SyncManager] Canh bao: Con lai cac su kien chua duoc dong bo (Pending). Se tiep tuc gui vao lan dong bo sau.");
            } else {
                System.out.println("[SyncManager] Dong bo hoan tat 100%!");
            }

        } catch (SQLException e) {
            System.err.println("[SyncManager] Loi truy van co so du lieu khi dong bo: " + e.getMessage());
            if (listener != null) {
                listener.onLog(java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss")),
                        "LỖI", "DATABASE", "Lỗi truy vấn CSDL khi đồng bộ: " + e.getMessage());
            }
        }
        System.out.println("================================================");
        return success;
    }
}
