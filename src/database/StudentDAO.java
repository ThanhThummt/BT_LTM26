package database;

import model.Student;
import model.SyncEvent;
import sync.JsonUtils;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Lớp DAO xử lý dữ liệu sinh viên, sự kiện gửi đi và sự kiện nhận về.
 * Tuân thủ nghiêm ngặt quy tắc transaction và không tạo vòng lặp đồng bộ.
 */
public class StudentDAO {

    // 1. Lấy danh sách toàn bộ sinh viên cục bộ
    public List<Student> getAllStudents() throws SQLException {
        List<Student> list = new ArrayList<>();
        String sql = "SELECT id, name, email, major, updated_at FROM students ORDER BY id ASC";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(new Student(
                        rs.getInt("id"),
                        rs.getString("name"),
                        rs.getString("email"),
                        rs.getString("major"),
                        rs.getLong("updated_at")
                ));
            }
        }
        return list;
    }

    // 2. Tìm kiếm sinh viên theo ID
    public Student getStudentById(int id) throws SQLException {
        String sql = "SELECT id, name, email, major, updated_at FROM students WHERE id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new Student(
                            rs.getInt("id"),
                            rs.getString("name"),
                            rs.getString("email"),
                            rs.getString("major"),
                            rs.getLong("updated_at")
                    );
                }
            }
        }
        return null;
    }

    // 3. Thêm sinh viên cục bộ: Đồng thời lưu sinh viên và tạo Outgoing Event trong 1 Transaction
    public boolean addStudentLocal(Student student) throws SQLException {
        String insertStudentSql = "INSERT INTO students (id, name, email, major, updated_at) VALUES (?, ?, ?, ?, ?)";
        String insertEventSql = "INSERT INTO outgoing_events (event_id, student_id, operation, payload, created_at, is_sent) VALUES (?, ?, ?, ?, ?, 0)";

        String eventId = UUID.randomUUID().toString();
        long now = System.currentTimeMillis();
        student.setUpdatedAt(now);
        String payload = JsonUtils.studentToJson(student);

        try (Connection conn = DatabaseConnection.getConnection()) {
            conn.setAutoCommit(false);
            try {
                // Thêm vào bảng students
                try (PreparedStatement ps1 = conn.prepareStatement(insertStudentSql)) {
                    ps1.setInt(1, student.getId());
                    ps1.setString(2, student.getName());
                    ps1.setString(3, student.getEmail());
                    ps1.setString(4, student.getMajor());
                    ps1.setLong(5, student.getUpdatedAt());
                    ps1.executeUpdate();
                }

                // Ghi nhận sự kiện chờ gửi vào outgoing_events
                try (PreparedStatement ps2 = conn.prepareStatement(insertEventSql)) {
                    ps2.setString(1, eventId);
                    ps2.setInt(2, student.getId());
                    ps2.setString(3, "INSERT");
                    ps2.setString(4, payload);
                    ps2.setLong(5, now);
                    ps2.executeUpdate();
                }

                conn.commit();
                return true;
            } catch (SQLException ex) {
                conn.rollback();
                throw ex;
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }

    // 4. Sửa sinh viên cục bộ: Đồng thời cập nhật bảng students và tạo Outgoing Event trong 1 Transaction
    public boolean updateStudentLocal(Student student) throws SQLException {
        String updateStudentSql = "UPDATE students SET name = ?, email = ?, major = ?, updated_at = ? WHERE id = ?";
        String insertEventSql = "INSERT INTO outgoing_events (event_id, student_id, operation, payload, created_at, is_sent) VALUES (?, ?, ?, ?, ?, 0)";

        String eventId = UUID.randomUUID().toString();
        long now = System.currentTimeMillis();
        student.setUpdatedAt(now);
        String payload = JsonUtils.studentToJson(student);

        try (Connection conn = DatabaseConnection.getConnection()) {
            conn.setAutoCommit(false);
            try {
                int rows;
                try (PreparedStatement ps1 = conn.prepareStatement(updateStudentSql)) {
                    ps1.setString(1, student.getName());
                    ps1.setString(2, student.getEmail());
                    ps1.setString(3, student.getMajor());
                    ps1.setLong(4, student.getUpdatedAt());
                    ps1.setInt(5, student.getId());
                    rows = ps1.executeUpdate();
                }

                if (rows > 0) {
                    try (PreparedStatement ps2 = conn.prepareStatement(insertEventSql)) {
                        ps2.setString(1, eventId);
                        ps2.setInt(2, student.getId());
                        ps2.setString(3, "UPDATE");
                        ps2.setString(4, payload);
                        ps2.setLong(5, now);
                        ps2.executeUpdate();
                    }
                    conn.commit();
                    return true;
                } else {
                    conn.rollback();
                    return false;
                }
            } catch (SQLException ex) {
                conn.rollback();
                throw ex;
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }

    // 5. Xóa sinh viên cục bộ: Đồng thời xóa khỏi bảng students và tạo Tombstone Outgoing Event trong 1 Transaction
    public boolean deleteStudentLocal(int id) throws SQLException {
        String deleteStudentSql = "DELETE FROM students WHERE id = ?";
        String insertEventSql = "INSERT INTO outgoing_events (event_id, student_id, operation, payload, created_at, is_sent) VALUES (?, ?, ?, ?, ?, 0)";

        String eventId = UUID.randomUUID().toString();
        long now = System.currentTimeMillis();

        try (Connection conn = DatabaseConnection.getConnection()) {
            conn.setAutoCommit(false);
            try {
                int rows;
                try (PreparedStatement ps1 = conn.prepareStatement(deleteStudentSql)) {
                    ps1.setInt(1, id);
                    rows = ps1.executeUpdate();
                }

                if (rows > 0) {
                    try (PreparedStatement ps2 = conn.prepareStatement(insertEventSql)) {
                        ps2.setString(1, eventId);
                        ps2.setInt(2, id);
                        ps2.setString(3, "DELETE");
                        ps2.setString(4, null); // Payload rỗng cho sự kiện xóa
                        ps2.setLong(5, now);
                        ps2.executeUpdate();
                    }
                    conn.commit();
                    return true;
                } else {
                    conn.rollback();
                    return false;
                }
            } catch (SQLException ex) {
                conn.rollback();
                throw ex;
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }

    // 6. Lấy danh sách các sự kiện cục bộ đang chờ gửi (is_sent = 0)
    public List<SyncEvent> getPendingOutgoingEvents() throws SQLException {
        List<SyncEvent> list = new ArrayList<>();
        String sql = "SELECT event_id, student_id, operation, payload, created_at FROM outgoing_events WHERE is_sent = 0 ORDER BY created_at ASC";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String eventId = rs.getString("event_id");
                int studentId = rs.getInt("student_id");
                String operation = rs.getString("operation");
                String payload = rs.getString("payload");
                long createdAt = rs.getLong("created_at");

                Student student = (payload != null && !payload.trim().isEmpty()) ? JsonUtils.parseStudent(payload) : null;
                list.add(new SyncEvent(eventId, operation, studentId, student, createdAt));
            }
        }
        return list;
    }

    // 7. Đánh dấu sự kiện đã gửi thành công sau khi nhận ACK từ máy đối tác
    public void markEventAsSent(String eventId) throws SQLException {
        String sql = "UPDATE outgoing_events SET is_sent = 1 WHERE event_id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, eventId);
            ps.executeUpdate();
        }
    }

    // Lấy số lượng sự kiện đang chờ gửi (Pending)
    public int getPendingEventCount() {
        String sql = "SELECT COUNT(*) FROM outgoing_events WHERE is_sent = 0";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) return rs.getInt(1);
        } catch (SQLException e) {
            System.err.println("[StudentDAO] Loi dem su kien pending: " + e.getMessage());
        }
        return 0;
    }

    // Lấy số lượng sự kiện đã đồng bộ thành công (Synced)
    public int getSyncedEventCount() {
        String sql = "SELECT COUNT(*) FROM outgoing_events WHERE is_sent = 1";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) return rs.getInt(1);
        } catch (SQLException e) {
            System.err.println("[StudentDAO] Loi dem su kien synced: " + e.getMessage());
        }
        return 0;
    }

    // 8. Áp dụng sự kiện nhận từ máy đối tác (Remote Event)
    // QUY TẮC BẮT BUỘC:
    // - Kiểm tra received_events để chống trùng lặp.
    // - Áp dụng thay đổi vào bảng students có so sánh mốc thời gian timestamp.
    // - Lưu event_id vào received_events.
    // - TUYỆT ĐỐI KHÔNG ghi vào outgoing_events để tránh vòng lặp đồng bộ.
    public boolean applyRemoteEvent(SyncEvent event) throws SQLException {
        String checkReceivedSql = "SELECT event_id FROM received_events WHERE event_id = ?";
        String insertReceivedSql = "INSERT INTO received_events (event_id, received_at) VALUES (?, ?)";

        try (Connection conn = DatabaseConnection.getConnection()) {
            conn.setAutoCommit(false);
            try {
                // 1. Kiểm tra sự kiện đã từng nhận chưa (Chống trùng lặp - Idempotent)
                try (PreparedStatement psCheck = conn.prepareStatement(checkReceivedSql)) {
                    psCheck.setString(1, event.getEventId());
                    try (ResultSet rs = psCheck.executeQuery()) {
                        if (rs.next()) {
                            // Đã xử lý sự kiện này trước đó, trả về thành công ngay mà không áp dụng lại
                            conn.commit();
                            return true;
                        }
                    }
                }

                // 2. Xử lý thao tác tương ứng trên bảng students
                String op = event.getOperation();
                int studentId = event.getStudentId();
                Student remoteStudent = event.getStudent();
                long eventTime = event.getTimestamp();

                // Lấy thông tin sinh viên hiện tại trong database (nếu có) để so sánh xung đột
                Student currentStudent = null;
                String selectCurrentSql = "SELECT id, name, email, major, updated_at FROM students WHERE id = ?";
                try (PreparedStatement psSelect = conn.prepareStatement(selectCurrentSql)) {
                    psSelect.setInt(1, studentId);
                    try (ResultSet rs = psSelect.executeQuery()) {
                        if (rs.next()) {
                            currentStudent = new Student(
                                    rs.getInt("id"),
                                    rs.getString("name"),
                                    rs.getString("email"),
                                    rs.getString("major"),
                                    rs.getLong("updated_at")
                            );
                        }
                    }
                }

                if ("INSERT".equalsIgnoreCase(op) || "UPDATE".equalsIgnoreCase(op)) {
                    if (currentStudent == null) {
                        // Chưa có sinh viên này -> INSERT
                        String insertSql = "INSERT INTO students (id, name, email, major, updated_at) VALUES (?, ?, ?, ?, ?)";
                        try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
                            ps.setInt(1, studentId);
                            ps.setString(2, remoteStudent != null ? remoteStudent.getName() : "");
                            ps.setString(3, remoteStudent != null ? remoteStudent.getEmail() : "");
                            ps.setString(4, remoteStudent != null ? remoteStudent.getMajor() : "");
                            ps.setLong(5, eventTime);
                            ps.executeUpdate();
                        }
                    } else {
                        // Đã có -> So sánh timestamp: Nếu sự kiện mới hơn hoặc bằng thì cập nhật
                        if (eventTime >= currentStudent.getUpdatedAt()) {
                            String updateSql = "UPDATE students SET name = ?, email = ?, major = ?, updated_at = ? WHERE id = ?";
                            try (PreparedStatement ps = conn.prepareStatement(updateSql)) {
                                ps.setString(1, remoteStudent != null ? remoteStudent.getName() : "");
                                ps.setString(2, remoteStudent != null ? remoteStudent.getEmail() : "");
                                ps.setString(3, remoteStudent != null ? remoteStudent.getMajor() : "");
                                ps.setLong(4, eventTime);
                                ps.setInt(5, studentId);
                                ps.executeUpdate();
                            }
                        } else {
                            System.out.println("[Conflict] Bo qua cap nhat cu hon cho SV ID " + studentId);
                        }
                    }
                } else if ("DELETE".equalsIgnoreCase(op)) {
                    if (currentStudent != null) {
                        // Chỉ xóa nếu sự kiện xóa mới hơn cập nhật hiện tại
                        if (eventTime >= currentStudent.getUpdatedAt()) {
                            String deleteSql = "DELETE FROM students WHERE id = ?";
                            try (PreparedStatement ps = conn.prepareStatement(deleteSql)) {
                                ps.setInt(1, studentId);
                                ps.executeUpdate();
                            }
                        } else {
                            System.out.println("[Conflict] Bo qua thao tac xoa vi co cap nhat moi hon cho SV ID " + studentId);
                        }
                    }
                }

                // 3. Ghi nhận event_id vào received_events
                try (PreparedStatement ps = conn.prepareStatement(insertReceivedSql)) {
                    ps.setString(1, event.getEventId());
                    ps.setLong(2, System.currentTimeMillis());
                    ps.executeUpdate();
                }

                conn.commit();
                return true;
            } catch (SQLException ex) {
                conn.rollback();
                throw ex;
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }
}
