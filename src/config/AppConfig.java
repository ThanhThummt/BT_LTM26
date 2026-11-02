package config;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Đọc cấu hình từ file config.properties (hoặc config.example.properties nếu chưa có config.properties).
 * Cho phép linh hoạt thay đổi IP, port, database giữa hai máy tính.
 */
public class AppConfig {
    private static AppConfig instance;
    private final Properties properties = new Properties();

    private AppConfig() {
        loadConfig();
    }

    public static synchronized AppConfig getInstance() {
        if (instance == null) {
            instance = new AppConfig();
        }
        return instance;
    }

    private void loadConfig() {
        File configFile = new File("config.properties");
        if (!configFile.exists()) {
            configFile = new File("config.example.properties");
        }

        if (configFile.exists()) {
            try (InputStream input = new FileInputStream(configFile)) {
                properties.load(input);
                System.out.println("[Config] Da nap cau hinh tu file: " + configFile.getName());
            } catch (IOException e) {
                System.err.println("[Config] Loi khi doc file cau hinh: " + e.getMessage());
            }
        } else {
            System.out.println("[Config] Khong tim thay file cau hinh, su dung gia tri mac dinh.");
        }
    }

    public String getDbHost() {
        return properties.getProperty("db.host", "localhost");
    }

    public int getDbPort() {
        return Integer.parseInt(properties.getProperty("db.port", "3306"));
    }

    public String getDbName() {
        return properties.getProperty("db.name", "student_db");
    }

    public String getDbUser() {
        return properties.getProperty("db.user", "root");
    }

    public String getDbPassword() {
        return properties.getProperty("db.password", "");
    }

    public String getDbUrl() {
        return String.format("jdbc:mysql://%s:%d/%s?useUnicode=true&characterEncoding=UTF-8&serverTimezone=UTC",
                getDbHost(), getDbPort(), getDbName());
    }

    public int getServerPort() {
        return Integer.parseInt(properties.getProperty("server.port", "8888"));
    }

    public String getPeerHost() {
        return properties.getProperty("peer.host", "192.168.1.100");
    }

    public void setPeerHost(String peerHost) {
        properties.setProperty("peer.host", peerHost);
    }

    public int getPeerPort() {
        return Integer.parseInt(properties.getProperty("peer.port", "8888"));
    }

    public void setPeerPort(int peerPort) {
        properties.setProperty("peer.port", String.valueOf(peerPort));
    }

    public int getNetworkTimeout() {
        return Integer.parseInt(properties.getProperty("network.timeout", "5000"));
    }
}
