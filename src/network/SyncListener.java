package network;

import model.SyncEvent;

/**
 * Giao diện lắng nghe sự kiện mạng và đồng bộ để cập nhật giao diện Swing theo thời gian thực.
 */
public interface SyncListener {
    /**
     * Ghi nhật ký hoạt động (Log).
     * @param time Thời gian (HH:mm:ss)
     * @param direction Chiều truyền (GỬI / NHẬN / KẾT NỐI / HỆ THỐNG / LỖI)
     * @param operation Loại thao tác (INSERT / UPDATE / DELETE / ACK / CONNECT)
     * @param message Nội dung chi tiết
     */
    void onLog(String time, String direction, String operation, String message);

    /**
     * Kích hoạt khi có sự kiện từ máy đối tác được áp dụng thành công vào CSDL.
     * Dùng để cập nhật lại JTable sinh viên trên giao diện Swing.
     */
    void onRemoteDataApplied(SyncEvent event);

    /**
     * Cập nhật trạng thái của TCP Server (Đang chạy / Đã dừng).
     */
    void onServerStatus(boolean isRunning, String message);
}
