import config.AppConfig;
import database.DatabaseConnection;
import database.StudentDAO;
import model.Student;
import model.SyncEvent;
import network.TcpServer;
import sync.SyncManager;

import java.sql.SQLException;
import java.util.List;
import java.util.Scanner;

/**
 * Lớp điều khiển chính của chương trình DatabaseSync (Giao diện Console).
 * Khởi động TCP Server chạy ngầm và cung cấp Menu tương tác cho người dùng.
 */
public class Main {
    private static final Scanner scanner = new Scanner(System.in);
    private static final StudentDAO studentDAO = new StudentDAO();
    private static final SyncManager syncManager = new SyncManager(studentDAO);
    private static TcpServer tcpServer;

    public static void main(String[] args) {
        // Nếu truyền tham số "--console" hoặc "-c", chạy chế độ dòng lệnh Console
        if (args.length > 0 && ("--console".equalsIgnoreCase(args[0]) || "-c".equalsIgnoreCase(args[0]))) {
            runConsoleMode();
            return;
        }

        // Mặc định khởi chạy giao diện đồ họa Java Swing (FlatLaf Dark Mode)
        try {
            ui.MainFrame.main(args);
        } catch (Throwable t) {
            System.err.println("[Main] Khong the khoi dong giao dien Swing, chuyen sang Console: " + t.getMessage());
            runConsoleMode();
        }
    }

    public static void runConsoleMode() {
        System.out.println("=================================================================");
        System.out.println("     DATABASE SYNC - DONG BO DU LIEU HAI CHIEU QUA TCP/IP        ");
        System.out.println("=================================================================");

        AppConfig config = AppConfig.getInstance();

        // 1. Kiem tra ket noi co so du lieu MySQL cuc bo
        System.out.print("[Khoi dong] Kiem tra ket noi MySQL cuc bo... ");
        if (DatabaseConnection.testConnection()) {
            System.out.println("THANH CONG!");
        } else {
            System.out.println("THAT BAI!");
            System.err.println("[Canh bao] Chua ket noi duoc MySQL.");
            System.err.println("-> Hay kiem tra XAMPP/MySQL da Start chua.");
            System.err.println("-> Hay dam bao da chay script sql/student_db.sql.");
            System.err.println("-> Kiem tra thong tin trong config.properties (hoac config.example.properties).");
        }

        // 2. Khoi dong TCP Server chay ngam
        int serverPort = config.getServerPort();
        tcpServer = new TcpServer(serverPort, studentDAO);
        Thread serverThread = new Thread(tcpServer, "TCP-Server-Thread");
        serverThread.setDaemon(true);
        serverThread.start();

        System.out.printf("[Thong tin] Cong lang nghe cuc bo (Server Port): %d\n", serverPort);
        System.out.printf("[Thong tin] May doi tac cau hinh (Peer Host:Port): %s:%d\n", config.getPeerHost(), config.getPeerPort());

        // 3. Vong lap Menu Console
        boolean running = true;
        while (running) {
            printMenu();
            System.out.print("Moi ban chon chuc nang (0-8): ");
            String choice = scanner.nextLine().trim();

            switch (choice) {
                case "1":
                    handleListStudents();
                    break;
                case "2":
                    handleAddStudent();
                    break;
                case "3":
                    handleUpdateStudent();
                    break;
                case "4":
                    handleDeleteStudent();
                    break;
                case "5":
                    handleViewPendingEvents();
                    break;
                case "6":
                    handleSyncNow();
                    break;
                case "7":
                    handleTestDbConnection();
                    break;
                case "8":
                    handleChangePeerConfig();
                    break;
                case "0":
                    System.out.println("Dang dung chuong trinh. Tam biet!");
                    tcpServer.stop();
                    running = false;
                    break;
                default:
                    System.out.println("Lua chon khong hop le. Vui long chon lai!");
            }
        }
    }

    private static void printMenu() {
        System.out.println("\n-------------------------- MENU CHINH --------------------------");
        System.out.println("1. Xem danh sach sinh vien hien co (Local DB)");
        System.out.println("2. Them sinh vien moi (INSERT)");
        System.out.println("3. Sua thong tin sinh vien (UPDATE)");
        System.out.println("4. Xoa sinh vien (DELETE)");
        System.out.println("5. Xem danh sach su kien cho dong bo (Pending Events)");
        System.out.println("6. Dong bo ngay sang may doi tac (SYNC NOW)");
        System.out.println("7. Kiem tra ket noi MySQL cuc bo");
        System.out.println("8. Cau hinh IP/Port may doi tac (Peer Settings)");
        System.out.println("0. Thoat");
        System.out.println("----------------------------------------------------------------");
    }

    private static void handleListStudents() {
        System.out.println("\n--- DANH SACH SINH VIEN (LOCAL DATABASE) ---");
        try {
            List<Student> students = studentDAO.getAllStudents();
            if (students.isEmpty()) {
                System.out.println("(Chua co sinh vien nao trong co so du lieu)");
            } else {
                System.out.printf("%-6s | %-25s | %-30s | %-20s | %-15s\n", "ID", "Ho va Ten", "Email", "Nganh hoc", "Updated At");
                System.out.println("---------------------------------------------------------------------------------------------------------");
                for (Student s : students) {
                    System.out.printf("%-6d | %-25s | %-30s | %-20s | %-15d\n",
                            s.getId(), s.getName(), s.getEmail(), s.getMajor(), s.getUpdatedAt());
                }
            }
        } catch (SQLException e) {
            System.err.println("Loi khi lay danh sach sinh vien: " + e.getMessage());
        }
    }

    private static void handleAddStudent() {
        System.out.println("\n--- THEM SINH VIEN MOI (INSERT) ---");
        try {
            System.out.print("Nhap Ma SV (so nguyen): ");
            int id = Integer.parseInt(scanner.nextLine().trim());

            Student existing = studentDAO.getStudentById(id);
            if (existing != null) {
                System.out.println("Loi: Ma SV nay da ton tai trong he thong! Vui long chon Ma SV khac.");
                return;
            }

            System.out.print("Nhap Ho va Ten: ");
            String name = scanner.nextLine().trim();

            System.out.print("Nhap Email: ");
            String email = scanner.nextLine().trim();

            System.out.print("Nhap Nganh hoc: ");
            String major = scanner.nextLine().trim();

            Student s = new Student(id, name, email, major, System.currentTimeMillis());
            boolean success = studentDAO.addStudentLocal(s);
            if (success) {
                System.out.println(">> Da them sinh vien thanh cong va tao su kien INSERT vao danh sach cho dong bo!");
            }
        } catch (NumberFormatException e) {
            System.err.println("Ma SV phai la mot so nguyen hop le!");
        } catch (SQLException e) {
            System.err.println("Loi CSDL khi them sinh vien: " + e.getMessage());
        }
    }

    private static void handleUpdateStudent() {
        System.out.println("\n--- SUA THONG TIN SINH VIEN (UPDATE) ---");
        try {
            System.out.print("Nhap Ma SV can sua: ");
            int id = Integer.parseInt(scanner.nextLine().trim());

            Student current = studentDAO.getStudentById(id);
            if (current == null) {
                System.out.println("Khong tim thay sinh vien voi Ma SV nay!");
                return;
            }

            System.out.printf("Nhap Ho va Ten moi (Hien tai: %s - Nhan Enter de giu nguyen): ", current.getName());
            String name = scanner.nextLine().trim();
            if (!name.isEmpty()) current.setName(name);

            System.out.printf("Nhap Email moi (Hien tai: %s - Nhan Enter de giu nguyen): ", current.getEmail());
            String email = scanner.nextLine().trim();
            if (!email.isEmpty()) current.setEmail(email);

            System.out.printf("Nhap Nganh hoc moi (Hien tai: %s - Nhan Enter de giu nguyen): ", current.getMajor());
            String major = scanner.nextLine().trim();
            if (!major.isEmpty()) current.setMajor(major);

            boolean success = studentDAO.updateStudentLocal(current);
            if (success) {
                System.out.println(">> Da cap nhat sinh vien thanh cong va tao su kien UPDATE vao danh sach cho dong bo!");
            }
        } catch (NumberFormatException e) {
            System.err.println("Ma SV phai la mot so nguyen hop le!");
        } catch (SQLException e) {
            System.err.println("Loi CSDL khi sua sinh vien: " + e.getMessage());
        }
    }

    private static void handleDeleteStudent() {
        System.out.println("\n--- XOA SINH VIEN (DELETE) ---");
        try {
            System.out.print("Nhap Ma SV can xoa: ");
            int id = Integer.parseInt(scanner.nextLine().trim());

            Student current = studentDAO.getStudentById(id);
            if (current == null) {
                System.out.println("Khong tim thay sinh vien voi Ma SV nay de xoa!");
                return;
            }

            System.out.printf("Ban co chac chan muon xoa sinh vien '%s' (Ma SV: %d)? (y/n): ", current.getName(), id);
            String confirm = scanner.nextLine().trim();
            if ("y".equalsIgnoreCase(confirm) || "yes".equalsIgnoreCase(confirm)) {
                boolean success = studentDAO.deleteStudentLocal(id);
                if (success) {
                    System.out.println(">> Da xoa sinh vien thanh cong va tao su kien DELETE vao danh sach cho dong bo!");
                }
            } else {
                System.out.println("Da huy thao tac xoa.");
            }
        } catch (NumberFormatException e) {
            System.err.println("Ma SV phai la mot so nguyen hop le!");
        } catch (SQLException e) {
            System.err.println("Loi CSDL khi xoa sinh vien: " + e.getMessage());
        }
    }

    private static void handleViewPendingEvents() {
        System.out.println("\n--- DANH SACH SU KIEN DANG CHO DONG BO (PENDING EVENTS) ---");
        try {
            List<SyncEvent> pending = studentDAO.getPendingOutgoingEvents();
            if (pending.isEmpty()) {
                System.out.println("(Khong co su kien nao dang cho gui. Du lieu da dong nhat hoan toan!)");
            } else {
                System.out.printf("%-38s | %-8s | %-6s | %-15s\n", "Event ID (UUID)", "Thao tac", "SVID", "Thoi gian tao");
                System.out.println("-----------------------------------------------------------------------------------");
                for (SyncEvent ev : pending) {
                    System.out.printf("%-38s | %-8s | %-6d | %-15d\n",
                            ev.getEventId(), ev.getOperation(), ev.getStudentId(), ev.getTimestamp());
                }
                System.out.printf(">> Tong cong co %d su kien chua duoc gui ACK. Chon muc 6 de 'Sync now'.\n", pending.size());
            }
        } catch (SQLException e) {
            System.err.println("Loi khi doc danh sach su kien cho dong bo: " + e.getMessage());
        }
    }

    private static void handleSyncNow() {
        syncManager.performSync();
    }

    private static void handleTestDbConnection() {
        System.out.print("\nKiem tra ket noi MySQL... ");
        if (DatabaseConnection.testConnection()) {
            System.out.println("THANH CONG! Ket noi database hoat dong binh thuong.");
        } else {
            System.out.println("THAT BAI! Khong the ket noi den co so du lieu.");
        }
    }

    private static void handleChangePeerConfig() {
        AppConfig config = AppConfig.getInstance();
        System.out.println("\n--- CAU HINH MAY DOI TAC (PEER CONFIG) ---");
        System.out.printf("Dia chi IP hien tai: %s\n", config.getPeerHost());
        System.out.print("Nhap IP moi cua may doi tac (Nhan Enter de giu nguyen): ");
        String newHost = scanner.nextLine().trim();
        if (!newHost.isEmpty()) {
            config.setPeerHost(newHost);
        }

        System.out.printf("Cong TCP hien tai: %d\n", config.getPeerPort());
        System.out.print("Nhap Cong TCP moi (Nhan Enter de giu nguyen): ");
        String newPortStr = scanner.nextLine().trim();
        if (!newPortStr.isEmpty()) {
            try {
                int newPort = Integer.parseInt(newPortStr);
                config.setPeerPort(newPort);
            } catch (NumberFormatException e) {
                System.err.println("Cong TCP phai la mot so nguyen!");
            }
        }

        System.out.printf(">> Da cap nhat thong tin may doi tac: %s:%d\n", config.getPeerHost(), config.getPeerPort());
    }
}
