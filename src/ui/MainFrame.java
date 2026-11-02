package ui;

import config.AppConfig;
import database.DatabaseConnection;
import database.StudentDAO;
import model.Student;
import model.SyncEvent;
import network.SyncListener;
import network.TcpClient;
import network.TcpServer;
import sync.SyncManager;

import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.JTableHeader;
import java.awt.*;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Enumeration;
import java.util.List;

/**
 * Giao diện đồ họa Java Swing phong cách LIGHT MODE hiện đại.
 * - Nền trắng #FFFFFF
 * - Khu vực nội dung xám nhạt #F8FAFC
 * - Màu chữ #1E293B
 * - Màu nhấn xanh dương #2563EB
 * - Đường viền mảnh #E2E8F0, bo góc nhẹ, font Segoe UI.
 */
public class MainFrame extends JFrame implements SyncListener {

    // Bảng màu chuẩn thiết kế Light Mode
    public static final Color COLOR_BG_WHITE = new Color(0xFF, 0xFF, 0xFF);          // #FFFFFF
    public static final Color COLOR_BG_SLATE_50 = new Color(0xF8, 0xFA, 0xFC);       // #F8FAFC
    public static final Color COLOR_TEXT_MAIN = new Color(0x1E, 0x29, 0x3B);         // #1E293B
    public static final Color COLOR_TEXT_MUTED = new Color(0x64, 0x74, 0x8B);        // #64748B
    public static final Color COLOR_PRIMARY_BLUE = new Color(0x25, 0x63, 0xEB);      // #2563EB
    public static final Color COLOR_BORDER = new Color(0xE2, 0xE8, 0xF0);            // #E2E8F0
    public static final Color COLOR_SUCCESS_GREEN = new Color(0x16, 0xA3, 0x4A);     // #16A34A
    public static final Color COLOR_WARNING_AMBER = new Color(0xD9, 0x77, 0x06);     // #D97706
    public static final Color COLOR_DANGER_RED = new Color(0xDC, 0x26, 0x26);        // #DC2626

    // Fonts chuẩn Segoe UI
    public static final Font FONT_TITLE = new Font("Segoe UI", Font.BOLD, 18);
    public static final Font FONT_SUBTITLE = new Font("Segoe UI", Font.PLAIN, 12);
    public static final Font FONT_SECTION = new Font("Segoe UI", Font.BOLD, 14);
    public static final Font FONT_BOLD = new Font("Segoe UI", Font.BOLD, 13);
    public static final Font FONT_REGULAR = new Font("Segoe UI", Font.PLAIN, 13);
    public static final Font FONT_STAT_NUM = new Font("Segoe UI", Font.BOLD, 22);

    private final StudentDAO studentDAO = new StudentDAO();
    private final SyncManager syncManager = new SyncManager(studentDAO);
    private final AppConfig config = AppConfig.getInstance();

    private TcpServer tcpServer;
    private Thread serverThread;

    // Header components
    private JLabel lblMachineName;
    private JLabel lblDbStatusBadge;
    private JLabel lblServerStatusBadge;

    // Khu vực 1: Kết nối mạng
    private JTextField txtLocalIp;
    private JTextField txtLocalPort;
    private JTextField txtPeerIp;
    private JTextField txtPeerPort;
    private JButton btnToggleServer;
    private JButton btnTestConnection;
    private JLabel lblPeerStatusText;

    // Khu vực 2: Quản lý sinh viên
    private JTable tblStudents;
    private DefaultTableModel modelStudents;
    private JButton btnAdd;
    private JButton btnEdit;
    private JButton btnDelete;
    private JButton btnRefresh;

    // Khu vực 3: Đồng bộ
    private JButton btnSyncNow;
    private JLabel lblPendingValue;
    private JLabel lblSyncedValue;
    private JLabel lblSyncStatusText;
    private JProgressBar progressBarSync;

    // Khu vực 4: Nhật ký
    private JTable tblLogs;
    private DefaultTableModel modelLogs;
    private JButton btnClearLogs;

    public MainFrame() {
        setTitle("DatabaseSync — Đồng bộ hai cơ sở dữ liệu qua TCP/IP (Light Mode)");
        setSize(1150, 780);
        setMinimumSize(new Dimension(1000, 680));
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        initUI();
        initData();
        startServerAutomatically();
    }

    private void initUI() {
        JPanel root = new JPanel(new BorderLayout(0, 10));
        root.setBackground(COLOR_BG_WHITE);
        setContentPane(root);

        // 1. Header (Tên ứng dụng, tên máy, trạng thái)
        root.add(createHeaderPanel(), BorderLayout.NORTH);

        // 2. Thân chính (Center): Gồm 3 phần chia theo chiều dọc
        JPanel centerContainer = new JPanel(new BorderLayout(0, 10));
        centerContainer.setBackground(COLOR_BG_WHITE);
        centerContainer.setBorder(new EmptyBorder(0, 14, 0, 14));

        // Phần trên: Kết nối (Trái) & Đồng bộ (Phải)
        JPanel topGrid = new JPanel(new GridLayout(1, 2, 12, 0));
        topGrid.setBackground(COLOR_BG_WHITE);
        topGrid.add(createNetworkPanel());
        topGrid.add(createSyncPanel());
        centerContainer.add(topGrid, BorderLayout.NORTH);

        // Phần giữa: Quản lý sinh viên (JTable)
        centerContainer.add(createStudentPanel(), BorderLayout.CENTER);

        root.add(centerContainer, BorderLayout.CENTER);

        // 3. Phía dưới: Nhật ký gửi/nhận
        JPanel bottomContainer = new JPanel(new BorderLayout());
        bottomContainer.setBackground(COLOR_BG_WHITE);
        bottomContainer.setBorder(new EmptyBorder(0, 14, 12, 14));
        bottomContainer.add(createLogPanel(), BorderLayout.CENTER);
        root.add(bottomContainer, BorderLayout.SOUTH);
    }

    // =========================================================================
    // HEADER: Tên ứng dụng, tên máy, trạng thái
    // =========================================================================
    private JPanel createHeaderPanel() {
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(COLOR_BG_WHITE);
        header.setBorder(new CompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, COLOR_BORDER),
                new EmptyBorder(12, 16, 12, 16)
        ));

        // Bên trái: Tiêu đề & phụ đề
        JPanel titleBox = new JPanel(new GridLayout(2, 1, 0, 2));
        titleBox.setOpaque(false);

        JLabel lblTitle = new JLabel("HỆ THỐNG ĐỒNG BỘ CƠ SỞ DỮ LIỆU HAI CHIỀU QUA TCP/IP");
        lblTitle.setFont(FONT_TITLE);
        lblTitle.setForeground(COLOR_TEXT_MAIN);
        titleBox.add(lblTitle);

        JLabel lblSubtitle = new JLabel("Bài tập lớn Mạng máy tính — Mô hình ngang hàng (A ↔ B) qua Java Socket & MySQL");
        lblSubtitle.setFont(FONT_SUBTITLE);
        lblSubtitle.setForeground(COLOR_TEXT_MUTED);
        titleBox.add(lblSubtitle);

        header.add(titleBox, BorderLayout.WEST);

        // Bên phải: Badge tên máy & trạng thái
        JPanel badgesBox = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 4));
        badgesBox.setOpaque(false);

        String hostName = "localhost";
        try {
            hostName = InetAddress.getLocalHost().getHostName();
        } catch (Exception ignored) {}

        lblMachineName = createBadge("Máy: " + hostName, new Color(0xF1, 0xF5, 0xF9), COLOR_TEXT_MAIN);
        lblDbStatusBadge = createBadge("MySQL: Đang kiểm tra...", new Color(0xFE, 0xF3, 0xC7), COLOR_WARNING_AMBER);
        lblServerStatusBadge = createBadge("Server: Khởi động...", new Color(0xDB, 0xEA, 0xFE), COLOR_PRIMARY_BLUE);

        badgesBox.add(lblMachineName);
        badgesBox.add(lblDbStatusBadge);
        badgesBox.add(lblServerStatusBadge);

        header.add(badgesBox, BorderLayout.EAST);
        return header;
    }

    // =========================================================================
    // KHU VỰC 1: KẾT NỐI MẠNG (IP, Port, Trạng thái TCP, Nút thao tác)
    // =========================================================================
    private JPanel createNetworkPanel() {
        JPanel card = createCardPanel("Kết nối mạng & TCP");
        card.setLayout(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(5, 6, 5, 6);
        c.fill = GridBagConstraints.HORIZONTAL;

        // Dòng 0: IP hiện tại & Port Server
        c.gridx = 0; c.gridy = 0; c.weightx = 0.0;
        card.add(createLabel("IP hiện tại:"), c);

        c.gridx = 1; c.gridy = 0; c.weightx = 0.55;
        txtLocalIp = createTextField(detectLocalLanIp(), false);
        txtLocalIp.setToolTipText("Địa chỉ IP LAN của máy này");
        card.add(txtLocalIp, c);

        c.gridx = 2; c.gridy = 0; c.weightx = 0.0;
        card.add(createLabel("Port Server:"), c);

        c.gridx = 3; c.gridy = 0; c.weightx = 0.35;
        txtLocalPort = createTextField(String.valueOf(config.getServerPort()), true);
        card.add(txtLocalPort, c);

        // Dòng 1: IP đối tác & Port đối tác
        c.gridx = 0; c.gridy = 1; c.weightx = 0.0;
        card.add(createLabel("IP đối tác:"), c);

        c.gridx = 1; c.gridy = 1; c.weightx = 0.55;
        txtPeerIp = createTextField(config.getPeerHost(), true);
        txtPeerIp.setToolTipText("Nhập IP LAN của máy đối tác (Ví dụ 192.168.1.20)");
        card.add(txtPeerIp, c);

        c.gridx = 2; c.gridy = 1; c.weightx = 0.0;
        card.add(createLabel("Port đối tác:"), c);

        c.gridx = 3; c.gridy = 1; c.weightx = 0.35;
        txtPeerPort = createTextField(String.valueOf(config.getPeerPort()), true);
        card.add(txtPeerPort, c);

        // Dòng 2: Nút thao tác
        JPanel btnBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 2));
        btnBar.setOpaque(false);

        btnToggleServer = createSecondaryButton("Dừng Server");
        btnToggleServer.addActionListener(e -> toggleServer());
        btnBar.add(btnToggleServer);

        btnTestConnection = createSecondaryButton("Kiểm tra kết nối");
        btnTestConnection.addActionListener(e -> testPeerConnection());
        btnBar.add(btnTestConnection);

        lblPeerStatusText = new JLabel("Đối tác: Chưa kiểm tra");
        lblPeerStatusText.setFont(FONT_SUBTITLE);
        lblPeerStatusText.setForeground(COLOR_TEXT_MUTED);
        btnBar.add(lblPeerStatusText);

        c.gridx = 0; c.gridy = 2; c.gridwidth = 4;
        card.add(btnBar, c);

        return card;
    }

    // =========================================================================
    // KHU VỰC 3: ĐỒNG BỘ DỮ LIỆU (Thống kê, Nút SYNC NOW, Tiến trình)
    // =========================================================================
    private JPanel createSyncPanel() {
        JPanel card = createCardPanel("Đồng bộ dữ liệu (Sync Manager)");
        card.setLayout(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(5, 8, 5, 8);
        c.fill = GridBagConstraints.HORIZONTAL;

        // Dòng 0: Thống kê số lượng (2 ô stat)
        JPanel statsBox = new JPanel(new GridLayout(1, 2, 10, 0));
        statsBox.setOpaque(false);

        // Box Pending
        JPanel pendingBox = createStatSubBox("Chờ gửi (Pending)", "0", COLOR_WARNING_AMBER);
        lblPendingValue = (JLabel) pendingBox.getClientProperty("valueLabel");
        statsBox.add(pendingBox);

        // Box Synced
        JPanel syncedBox = createStatSubBox("Đã đồng bộ (Synced)", "0", COLOR_SUCCESS_GREEN);
        lblSyncedValue = (JLabel) syncedBox.getClientProperty("valueLabel");
        statsBox.add(syncedBox);

        c.gridx = 0; c.gridy = 0; c.weightx = 1.0;
        card.add(statsBox, c);

        // Dòng 1: Nút ĐỒNG BỘ NGAY (Màu nhấn xanh dương #2563EB)
        btnSyncNow = new JButton("ĐỒNG BỘ NGAY (SYNC NOW)");
        btnSyncNow.setFont(new Font("Segoe UI", Font.BOLD, 14));
        btnSyncNow.setBackground(COLOR_PRIMARY_BLUE);
        btnSyncNow.setForeground(Color.WHITE);
        btnSyncNow.setFocusPainted(false);
        btnSyncNow.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btnSyncNow.setPreferredSize(new Dimension(240, 36));
        btnSyncNow.setBorder(new LineBorder(COLOR_PRIMARY_BLUE, 1, true));
        btnSyncNow.addActionListener(e -> performSyncInBackground());

        c.gridx = 0; c.gridy = 1;
        card.add(btnSyncNow, c);

        // Dòng 2: Progress bar & Label trạng thái
        JPanel progressBox = new JPanel(new BorderLayout(6, 2));
        progressBox.setOpaque(false);

        progressBarSync = new JProgressBar();
        progressBarSync.setPreferredSize(new Dimension(100, 6));
        progressBarSync.setForeground(COLOR_PRIMARY_BLUE);
        progressBarSync.setVisible(false);
        progressBox.add(progressBarSync, BorderLayout.NORTH);

        lblSyncStatusText = new JLabel("Trạng thái: Sẵn sàng", JLabel.CENTER);
        lblSyncStatusText.setFont(FONT_SUBTITLE);
        lblSyncStatusText.setForeground(COLOR_TEXT_MUTED);
        progressBox.add(lblSyncStatusText, BorderLayout.CENTER);

        c.gridx = 0; c.gridy = 2;
        card.add(progressBox, c);

        return card;
    }

    // =========================================================================
    // KHU VỰC 2: QUẢN LÝ SINH VIÊN (JTable & Thao tác)
    // =========================================================================
    private JPanel createStudentPanel() {
        JPanel card = createCardPanel("Quản lý sinh viên (Dữ liệu MySQL)");
        card.setLayout(new BorderLayout(0, 8));

        String[] cols = {"Mã SV", "Họ và tên", "Email", "Ngành học", "Cập nhật lần cuối"};
        modelStudents = new DefaultTableModel(cols, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        tblStudents = new JTable(modelStudents);
        tblStudents.setFont(FONT_REGULAR);
        tblStudents.setForeground(COLOR_TEXT_MAIN);
        tblStudents.setBackground(COLOR_BG_WHITE);
        tblStudents.setGridColor(COLOR_BORDER);
        tblStudents.setRowHeight(28);
        tblStudents.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        tblStudents.setSelectionBackground(new Color(0xDB, 0xEA, 0xFE)); // Blue tint 100
        tblStudents.setSelectionForeground(COLOR_TEXT_MAIN);

        // Header Table
        JTableHeader tableHeader = tblStudents.getTableHeader();
        tableHeader.setFont(FONT_BOLD);
        tableHeader.setBackground(COLOR_BG_SLATE_50);
        tableHeader.setForeground(COLOR_TEXT_MAIN);
        tableHeader.setReorderingAllowed(false);
        tableHeader.setPreferredSize(new Dimension(tableHeader.getWidth(), 30));

        // Căn giữa cột Mã SV
        DefaultTableCellRenderer centerRenderer = new DefaultTableCellRenderer();
        centerRenderer.setHorizontalAlignment(JLabel.CENTER);
        tblStudents.getColumnModel().getColumn(0).setCellRenderer(centerRenderer);
        tblStudents.getColumnModel().getColumn(0).setPreferredWidth(85);
        tblStudents.getColumnModel().getColumn(1).setPreferredWidth(220);
        tblStudents.getColumnModel().getColumn(2).setPreferredWidth(230);
        tblStudents.getColumnModel().getColumn(3).setPreferredWidth(200);
        tblStudents.getColumnModel().getColumn(4).setPreferredWidth(170);

        JScrollPane scrollPane = new JScrollPane(tblStudents);
        scrollPane.setBorder(new LineBorder(COLOR_BORDER, 1, true));
        scrollPane.getViewport().setBackground(COLOR_BG_WHITE);
        card.add(scrollPane, BorderLayout.CENTER);

        // Toolbar nút bấm
        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        toolbar.setOpaque(false);

        btnAdd = createPrimarySmallButton("Thêm sinh viên");
        btnAdd.addActionListener(e -> showAddStudentDialog());
        toolbar.add(btnAdd);

        btnEdit = createSecondaryButton("Sửa sinh viên");
        btnEdit.addActionListener(e -> showEditStudentDialog());
        toolbar.add(btnEdit);

        btnDelete = createSecondaryButton("Xóa sinh viên");
        btnDelete.addActionListener(e -> handleDeleteStudent());
        toolbar.add(btnDelete);

        btnRefresh = createSecondaryButton("Làm mới bảng");
        btnRefresh.addActionListener(e -> reloadStudentsInBackground());
        toolbar.add(btnRefresh);

        card.add(toolbar, BorderLayout.SOUTH);
        return card;
    }

    // =========================================================================
    // KHU VỰC 4: NHẬT KÝ GỬI/NHẬN & KẾT QUẢ (Real-time Audit Log)
    // =========================================================================
    private JPanel createLogPanel() {
        JPanel card = createCardPanel("Nhật ký gửi/nhận & Hoạt động mạng");
        card.setLayout(new BorderLayout(0, 6));
        card.setPreferredSize(new Dimension(getWidth(), 160));

        String[] cols = {"Thời gian", "Chiều", "Thao tác", "Nội dung chi tiết"};
        modelLogs = new DefaultTableModel(cols, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        tblLogs = new JTable(modelLogs);
        tblLogs.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        tblLogs.setForeground(COLOR_TEXT_MAIN);
        tblLogs.setBackground(COLOR_BG_WHITE);
        tblLogs.setGridColor(COLOR_BORDER);
        tblLogs.setRowHeight(22);

        JTableHeader th = tblLogs.getTableHeader();
        th.setFont(new Font("Segoe UI", Font.BOLD, 12));
        th.setBackground(COLOR_BG_SLATE_50);
        th.setForeground(COLOR_TEXT_MAIN);

        tblLogs.getColumnModel().getColumn(0).setPreferredWidth(90);
        tblLogs.getColumnModel().getColumn(1).setPreferredWidth(90);
        tblLogs.getColumnModel().getColumn(2).setPreferredWidth(100);
        tblLogs.getColumnModel().getColumn(3).setPreferredWidth(700);

        JScrollPane scrollPane = new JScrollPane(tblLogs);
        scrollPane.setBorder(new LineBorder(COLOR_BORDER, 1, true));
        scrollPane.getViewport().setBackground(COLOR_BG_WHITE);
        card.add(scrollPane, BorderLayout.CENTER);

        JPanel logActions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        logActions.setOpaque(false);
        btnClearLogs = createSecondaryButton("Xóa nhật ký");
        btnClearLogs.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        btnClearLogs.addActionListener(e -> modelLogs.setRowCount(0));
        logActions.add(btnClearLogs);

        card.add(logActions, BorderLayout.SOUTH);
        return card;
    }

    // =========================================================================
    // HELPER UI FACTORY METHODS (GIAO DIỆN LIGHT MODE CHUẨN ĐỒNG NHẤT)
    // =========================================================================
    private JPanel createCardPanel(String sectionTitle) {
        JPanel panel = new JPanel();
        panel.setBackground(COLOR_BG_SLATE_50);
        panel.setBorder(new CompoundBorder(
                new LineBorder(COLOR_BORDER, 1, true),
                new EmptyBorder(8, 12, 10, 12)
        ));

        // Thêm tiêu đề Section nhỏ ở góc
        JLabel lblSec = new JLabel(sectionTitle);
        lblSec.setFont(FONT_SECTION);
        lblSec.setForeground(COLOR_TEXT_MAIN);
        panel.putClientProperty("titleLabel", lblSec);
        return panel;
    }

    private JPanel createStatSubBox(String title, String defaultValue, Color valueColor) {
        JPanel box = new JPanel(new BorderLayout(0, 2));
        box.setBackground(COLOR_BG_WHITE);
        box.setBorder(new CompoundBorder(
                new LineBorder(COLOR_BORDER, 1, true),
                new EmptyBorder(6, 10, 6, 10)
        ));

        JLabel lblTitle = new JLabel(title);
        lblTitle.setFont(FONT_SUBTITLE);
        lblTitle.setForeground(COLOR_TEXT_MUTED);
        box.add(lblTitle, BorderLayout.NORTH);

        JLabel lblVal = new JLabel(defaultValue);
        lblVal.setFont(FONT_STAT_NUM);
        lblVal.setForeground(valueColor);
        box.add(lblVal, BorderLayout.CENTER);

        box.putClientProperty("valueLabel", lblVal);
        return box;
    }

    private JLabel createBadge(String text, Color bg, Color fg) {
        JLabel badge = new JLabel(" " + text + " ");
        badge.setFont(new Font("Segoe UI", Font.BOLD, 12));
        badge.setOpaque(true);
        badge.setBackground(bg);
        badge.setForeground(fg);
        badge.setBorder(new LineBorder(bg, 4, true));
        return badge;
    }

    private JLabel createLabel(String text) {
        JLabel label = new JLabel(text);
        label.setFont(FONT_REGULAR);
        label.setForeground(COLOR_TEXT_MAIN);
        return label;
    }

    private JTextField createTextField(String text, boolean editable) {
        JTextField tf = new JTextField(text);
        tf.setEditable(editable);
        tf.setFont(FONT_REGULAR);
        tf.setForeground(COLOR_TEXT_MAIN);
        tf.setBackground(editable ? COLOR_BG_WHITE : COLOR_BG_SLATE_50);
        tf.setBorder(new CompoundBorder(
                new LineBorder(COLOR_BORDER, 1, true),
                new EmptyBorder(3, 6, 3, 6)
        ));
        return tf;
    }

    private JButton createSecondaryButton(String text) {
        JButton btn = new JButton(text);
        btn.setFont(FONT_BOLD);
        btn.setBackground(COLOR_BG_WHITE);
        btn.setForeground(COLOR_TEXT_MAIN);
        btn.setFocusPainted(false);
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btn.setBorder(new CompoundBorder(
                new LineBorder(COLOR_BORDER, 1, true),
                new EmptyBorder(5, 12, 5, 12)
        ));
        return btn;
    }

    private JButton createPrimarySmallButton(String text) {
        JButton btn = new JButton(text);
        btn.setFont(FONT_BOLD);
        btn.setBackground(COLOR_PRIMARY_BLUE);
        btn.setForeground(Color.WHITE);
        btn.setFocusPainted(false);
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btn.setBorder(new CompoundBorder(
                new LineBorder(COLOR_PRIMARY_BLUE, 1, true),
                new EmptyBorder(5, 14, 5, 14)
        ));
        return btn;
    }

    // =========================================================================
    // DỮ LIỆU & NỀN TẢNG (SWING WORKER TRÁNH TREO EDT)
    // =========================================================================
    private void initData() {
        SwingWorker<Boolean, Void> dbCheckWorker = new SwingWorker<>() {
            @Override
            protected Boolean doInBackground() {
                return DatabaseConnection.testConnection();
            }

            @Override
            protected void done() {
                try {
                    if (get()) {
                        lblDbStatusBadge.setText("MySQL: Đã kết nối");
                        lblDbStatusBadge.setBackground(new Color(0xDC, 0xFC, 0xE7)); // Green 100
                        lblDbStatusBadge.setForeground(COLOR_SUCCESS_GREEN);
                        addLogEntry("HỆ THỐNG", "DB", "Kết nối MySQL cục bộ (" + config.getDbName() + ") thành công.");
                        reloadStudentsInBackground();
                        updateStats();
                    } else {
                        lblDbStatusBadge.setText("MySQL: Lỗi kết nối");
                        lblDbStatusBadge.setBackground(new Color(0xFE, 0xE2, 0xE2)); // Red 100
                        lblDbStatusBadge.setForeground(COLOR_DANGER_RED);
                        addLogEntry("LỖI", "DB", "Không thể kết nối MySQL! Kiểm tra XAMPP đã Start MySQL chưa.");
                        JOptionPane.showMessageDialog(MainFrame.this,
                                "Chưa kết nối được MySQL cục bộ!\n\nVui lòng kiểm tra:\n1. XAMPP Control Panel đã Start MySQL chưa.\n2. Đã tạo database qua file sql/student_db.sql chưa.\n3. Kiểm tra config.properties.",
                                "Cảnh báo Cơ sở dữ liệu", JOptionPane.WARNING_MESSAGE);
                    }
                } catch (Exception ignored) {}
            }
        };
        dbCheckWorker.execute();
    }

    private void reloadStudentsInBackground() {
        SwingWorker<List<Student>, Void> worker = new SwingWorker<>() {
            @Override
            protected List<Student> doInBackground() throws Exception {
                return studentDAO.getAllStudents();
            }

            @Override
            protected void done() {
                try {
                    List<Student> list = get();
                    modelStudents.setRowCount(0);
                    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                    for (Student s : list) {
                        modelStudents.addRow(new Object[]{
                                s.getId(),
                                s.getName(),
                                s.getEmail(),
                                s.getMajor(),
                                sdf.format(new Date(s.getUpdatedAt()))
                        });
                    }
                    updateStats();
                } catch (Exception e) {
                    addLogEntry("LỖI", "DB", "Lỗi tải danh sách sinh viên: " + e.getMessage());
                }
            }
        };
        worker.execute();
    }

    private void updateStats() {
        SwingWorker<int[], Void> worker = new SwingWorker<>() {
            @Override
            protected int[] doInBackground() {
                int pending = studentDAO.getPendingEventCount();
                int synced = studentDAO.getSyncedEventCount();
                return new int[]{pending, synced};
            }

            @Override
            protected void done() {
                try {
                    int[] counts = get();
                    lblPendingValue.setText(String.valueOf(counts[0]));
                    lblSyncedValue.setText(String.valueOf(counts[1]));
                } catch (Exception ignored) {}
            }
        };
        worker.execute();
    }

    // =========================================================================
    // QUẢN LÝ TCP SERVER
    // =========================================================================
    private void startServerAutomatically() {
        int port;
        try {
            port = Integer.parseInt(txtLocalPort.getText().trim());
        } catch (NumberFormatException e) {
            port = config.getServerPort();
        }

        tcpServer = new TcpServer(port, studentDAO);
        tcpServer.setListener(this);
        serverThread = new Thread(tcpServer, "Light-TcpServer-Thread");
        serverThread.setDaemon(true);
        serverThread.start();
        btnToggleServer.setText("Dừng Server");
    }

    private void toggleServer() {
        if (tcpServer != null) {
            tcpServer.stop();
            tcpServer = null;
            serverThread = null;
            btnToggleServer.setText("Khởi động Server");
            lblServerStatusBadge.setText("Server: Đã dừng");
            lblServerStatusBadge.setBackground(new Color(0xFE, 0xE2, 0xE2));
            lblServerStatusBadge.setForeground(COLOR_DANGER_RED);
        } else {
            startServerAutomatically();
        }
    }

    // =========================================================================
    // ĐỒNG BỘ (SYNC NOW) & KIỂM TRA KẾT NỐI
    // =========================================================================
    private void performSyncInBackground() {
        applyPeerSettingsFromUI();

        btnSyncNow.setEnabled(false);
        progressBarSync.setVisible(true);
        progressBarSync.setIndeterminate(true);
        lblSyncStatusText.setText("Trạng thái: Đang kết nối và gửi sự kiện...");
        lblSyncStatusText.setForeground(COLOR_WARNING_AMBER);

        SwingWorker<Integer, Void> worker = new SwingWorker<>() {
            @Override
            protected Integer doInBackground() {
                return syncManager.performSync(MainFrame.this);
            }

            @Override
            protected void done() {
                btnSyncNow.setEnabled(true);
                progressBarSync.setVisible(false);
                progressBarSync.setIndeterminate(false);

                try {
                    int synced = get();
                    lblSyncStatusText.setText("Trạng thái: Đồng bộ hoàn tất (" + synced + " sự kiện thành công)");
                    lblSyncStatusText.setForeground(COLOR_SUCCESS_GREEN);
                } catch (Exception e) {
                    lblSyncStatusText.setText("Trạng thái: Đồng bộ thất bại");
                    lblSyncStatusText.setForeground(COLOR_DANGER_RED);
                }
                updateStats();
                reloadStudentsInBackground();
            }
        };
        worker.execute();
    }

    private void testPeerConnection() {
        applyPeerSettingsFromUI();
        lblPeerStatusText.setText("Đối tác: Đang kiểm tra...");
        lblPeerStatusText.setForeground(COLOR_WARNING_AMBER);

        SwingWorker<Boolean, Void> worker = new SwingWorker<>() {
            @Override
            protected Boolean doInBackground() {
                TcpClient client = new TcpClient(config.getPeerHost(), config.getPeerPort(), 3000);
                return client.testConnection();
            }

            @Override
            protected void done() {
                try {
                    boolean ok = get();
                    if (ok) {
                        lblPeerStatusText.setText("Đối tác: [●] Đã kết nối TCP");
                        lblPeerStatusText.setForeground(COLOR_SUCCESS_GREEN);
                        addLogEntry("KẾT NỐI", "TCP", "Kiểm tra kết nối tới " + config.getPeerHost() + ":" + config.getPeerPort() + " -> THÀNH CÔNG");
                    } else {
                        lblPeerStatusText.setText("Đối tác: [○] Không phản hồi");
                        lblPeerStatusText.setForeground(COLOR_DANGER_RED);
                        addLogEntry("LỖI", "TCP", "Không thể kết nối tới " + config.getPeerHost() + ":" + config.getPeerPort() + " (Peer tắt hoặc chặn Firewall)");
                    }
                } catch (Exception ignored) {}
            }
        };
        worker.execute();
    }

    private void applyPeerSettingsFromUI() {
        String peerHost = txtPeerIp.getText().trim();
        if (!peerHost.isEmpty()) {
            config.setPeerHost(peerHost);
        }
        try {
            int peerPort = Integer.parseInt(txtPeerPort.getText().trim());
            config.setPeerPort(peerPort);
        } catch (NumberFormatException ignored) {}
    }

    // =========================================================================
    // DIALOG THÊM / SỬA / XÓA SINH VIÊN
    // =========================================================================
    private void showAddStudentDialog() {
        JTextField txtId = createTextField("", true);
        JTextField txtName = createTextField("", true);
        JTextField txtEmail = createTextField("", true);
        JTextField txtMajor = createTextField("", true);

        JPanel panel = new JPanel(new GridLayout(4, 2, 8, 8));
        panel.setBackground(COLOR_BG_WHITE);
        panel.add(createLabel("Mã SV (số nguyên):"));
        panel.add(txtId);
        panel.add(createLabel("Họ và tên:"));
        panel.add(txtName);
        panel.add(createLabel("Email:"));
        panel.add(txtEmail);
        panel.add(createLabel("Ngành học:"));
        panel.add(txtMajor);

        int result = JOptionPane.showConfirmDialog(this, panel, "Thêm sinh viên mới (INSERT)", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (result == JOptionPane.OK_OPTION) {
            try {
                int id = Integer.parseInt(txtId.getText().trim());
                String name = txtName.getText().trim();
                String email = txtEmail.getText().trim();
                String major = txtMajor.getText().trim();

                if (name.isEmpty()) {
                    JOptionPane.showMessageDialog(this, "Họ và tên không được để trống!", "Lỗi nhập liệu", JOptionPane.ERROR_MESSAGE);
                    return;
                }

                SwingWorker<Boolean, Void> worker = new SwingWorker<>() {
                    @Override
                    protected Boolean doInBackground() throws Exception {
                        Student existing = studentDAO.getStudentById(id);
                        if (existing != null) return null;
                        Student s = new Student(id, name, email, major, System.currentTimeMillis());
                        return studentDAO.addStudentLocal(s);
                    }

                    @Override
                    protected void done() {
                        try {
                            Boolean ok = get();
                            if (ok == null) {
                                JOptionPane.showMessageDialog(MainFrame.this, "Mã SV này đã tồn tại trong CSDL!", "Trùng khóa", JOptionPane.WARNING_MESSAGE);
                            } else if (ok) {
                                addLogEntry("CỤC BỘ", "INSERT", "Đã thêm SV ID " + id + " ('" + name + "') vào CSDL & tạo sự kiện Pending.");
                                reloadStudentsInBackground();
                            }
                        } catch (Exception ex) {
                            JOptionPane.showMessageDialog(MainFrame.this, "Lỗi thêm SV: " + ex.getMessage(), "Lỗi SQL", JOptionPane.ERROR_MESSAGE);
                        }
                    }
                };
                worker.execute();

            } catch (NumberFormatException e) {
                JOptionPane.showMessageDialog(this, "Mã SV phải là một số nguyên hợp lệ!", "Lỗi nhập liệu", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void showEditStudentDialog() {
        int row = tblStudents.getSelectedRow();
        if (row == -1) {
            JOptionPane.showMessageDialog(this, "Vui lòng chọn một sinh viên trong bảng để sửa!", "Thông báo", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        int id = (int) modelStudents.getValueAt(row, 0);
        String currentName = (String) modelStudents.getValueAt(row, 1);
        String currentEmail = (String) modelStudents.getValueAt(row, 2);
        String currentMajor = (String) modelStudents.getValueAt(row, 3);

        JTextField txtName = createTextField(currentName, true);
        JTextField txtEmail = createTextField(currentEmail, true);
        JTextField txtMajor = createTextField(currentMajor, true);

        JPanel panel = new JPanel(new GridLayout(4, 2, 8, 8));
        panel.setBackground(COLOR_BG_WHITE);
        panel.add(createLabel("Mã SV:"));
        panel.add(createLabel(String.valueOf(id)));
        panel.add(createLabel("Họ và tên:"));
        panel.add(txtName);
        panel.add(createLabel("Email:"));
        panel.add(txtEmail);
        panel.add(createLabel("Ngành học:"));
        panel.add(txtMajor);

        int result = JOptionPane.showConfirmDialog(this, panel, "Sửa thông tin SV ID " + id + " (UPDATE)", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (result == JOptionPane.OK_OPTION) {
            String name = txtName.getText().trim();
            String email = txtEmail.getText().trim();
            String major = txtMajor.getText().trim();

            SwingWorker<Boolean, Void> worker = new SwingWorker<>() {
                @Override
                protected Boolean doInBackground() throws Exception {
                    Student s = new Student(id, name, email, major, System.currentTimeMillis());
                    return studentDAO.updateStudentLocal(s);
                }

                @Override
                protected void done() {
                    try {
                        if (get()) {
                            addLogEntry("CỤC BỘ", "UPDATE", "Đã cập nhật SV ID " + id + " vào CSDL & tạo sự kiện Pending.");
                            reloadStudentsInBackground();
                        }
                    } catch (Exception ex) {
                        JOptionPane.showMessageDialog(MainFrame.this, "Lỗi cập nhật SV: " + ex.getMessage(), "Lỗi SQL", JOptionPane.ERROR_MESSAGE);
                    }
                }
            };
            worker.execute();
        }
    }

    private void handleDeleteStudent() {
        int row = tblStudents.getSelectedRow();
        if (row == -1) {
            JOptionPane.showMessageDialog(this, "Vui lòng chọn một sinh viên trong bảng để xóa!", "Thông báo", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        int id = (int) modelStudents.getValueAt(row, 0);
        String name = (String) modelStudents.getValueAt(row, 1);

        int confirm = JOptionPane.showConfirmDialog(this,
                "Bạn có chắc chắn muốn xóa sinh viên: " + name + " (ID: " + id + ")?\nThao tác này sẽ tạo sự kiện DELETE gửi sang đối tác khi đồng bộ.",
                "Xác nhận xóa (DELETE)", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);

        if (confirm == JOptionPane.YES_OPTION) {
            SwingWorker<Boolean, Void> worker = new SwingWorker<>() {
                @Override
                protected Boolean doInBackground() throws Exception {
                    return studentDAO.deleteStudentLocal(id);
                }

                @Override
                protected void done() {
                    try {
                        if (get()) {
                            addLogEntry("CỤC BỘ", "DELETE", "Đã xóa SV ID " + id + " khỏi CSDL & tạo Tombstone sự kiện Pending.");
                            reloadStudentsInBackground();
                        }
                    } catch (Exception ex) {
                        JOptionPane.showMessageDialog(MainFrame.this, "Lỗi xóa SV: " + ex.getMessage(), "Lỗi SQL", JOptionPane.ERROR_MESSAGE);
                    }
                }
            };
            worker.execute();
        }
    }

    // =========================================================================
    // CÁC HÀM LẮNG NGHE SYNC LISTENER (CẬP NHẬT TỰ ĐỘNG KHI ĐỐI TÁC GỬI DỮ LIỆU)
    // =========================================================================
    @Override
    public void onLog(String time, String direction, String operation, String message) {
        SwingUtilities.invokeLater(() -> {
            modelLogs.insertRow(0, new Object[]{time, direction, operation, message});
            if (modelLogs.getRowCount() > 200) {
                modelLogs.setRowCount(200);
            }
        });
    }

    @Override
    public void onRemoteDataApplied(SyncEvent event) {
        SwingUtilities.invokeLater(() -> {
            reloadStudentsInBackground();
            updateStats();
        });
    }

    @Override
    public void onServerStatus(boolean isRunning, String message) {
        SwingUtilities.invokeLater(() -> {
            if (isRunning) {
                lblServerStatusBadge.setText("Server: Đang lắng nghe");
                lblServerStatusBadge.setBackground(new Color(0xDC, 0xFC, 0xE7));
                lblServerStatusBadge.setForeground(COLOR_SUCCESS_GREEN);
                btnToggleServer.setText("Dừng Server");
            } else {
                lblServerStatusBadge.setText("Server: Đã dừng");
                lblServerStatusBadge.setBackground(new Color(0xFE, 0xE2, 0xE2));
                lblServerStatusBadge.setForeground(COLOR_DANGER_RED);
                btnToggleServer.setText("Khởi động Server");
            }
        });
    }

    private void addLogEntry(String direction, String operation, String message) {
        String now = new SimpleDateFormat("HH:mm:ss").format(new Date());
        onLog(now, direction, operation, message);
    }

    private static String detectLocalLanIp() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface iface = interfaces.nextElement();
                if (iface.isLoopback() || !iface.isUp() || iface.isVirtual()) continue;
                Enumeration<InetAddress> addresses = iface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress addr = addresses.nextElement();
                    if (addr instanceof Inet4Address && !addr.isLoopbackAddress()) {
                        return addr.getHostAddress();
                    }
                }
            }
            return InetAddress.getLocalHost().getHostAddress();
        } catch (Exception e) {
            return "127.0.0.1";
        }
    }

    // =========================================================================
    // ĐIỂM KHỞI CHẠY GIAO DIỆN
    // =========================================================================
    public static void main(String[] args) {
        // Áp dụng phong cách FlatLaf Light Mode với bo góc nhẹ
        try {
            com.formdev.flatlaf.FlatLightLaf.setup();
            UIManager.put("Button.arc", 6);
            UIManager.put("Component.arc", 6);
            UIManager.put("ProgressBar.arc", 6);
            UIManager.put("TextComponent.arc", 6);
        } catch (Throwable t) {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {}
        }

        SwingUtilities.invokeLater(() -> {
            MainFrame frame = new MainFrame();
            frame.setVisible(true);
        });
    }
}
