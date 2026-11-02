package database;

import config.AppConfig;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * Quản lý kết nối JDBC tới cơ sở dữ liệu MySQL cục bộ.
 */
public class DatabaseConnection {

    static {
        try {
            // Tải driver MySQL Connector/J
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            System.err.println("[DatabaseConnection] Khong tim thay MySQL JDBC Driver trong classpath: " + e.getMessage());
        }
    }

    public static Connection getConnection() throws SQLException {
        AppConfig config = AppConfig.getInstance();
        return DriverManager.getConnection(config.getDbUrl(), config.getDbUser(), config.getDbPassword());
    }

    public static boolean testConnection() {
        try (Connection conn = getConnection()) {
            return conn != null && !conn.isClosed();
        } catch (SQLException e) {
            System.err.println("[DatabaseConnection] Ket noi database that bai: " + e.getMessage());
            return false;
        }
    }
}
