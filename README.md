# DATABASE SYNC — ĐỒNG BỘ HAI CƠ SỞ DỮ LIỆU QUA TCP/IP

Dự án bài tập môn **Mạng máy tính**: Đồng bộ dữ liệu hai chiều (Bidirectional Synchronization) giữa 2 cơ sở dữ liệu MySQL trên 2 máy tính độc lập thông qua giao thức **TCP/IP Socket** bằng ngôn ngữ **Java thuần (Standard Java)**.

---

## 1. Kiến trúc và Nguyên lý hoạt động

- **Mô hình ngang hàng (Peer-to-Peer)**: Cả 2 máy (Máy A và Máy B) đều chạy chung một chương trình Java duy nhất.
- Mỗi máy vừa là **TCP Server** (chạy nền lắng nghe kết nối đến qua cổng chỉ định), vừa là **TCP Client** (kết nối sang máy đối phương khi người dùng kích hoạt lệnh **"Sync now"**).
- **Giao thức phân định gói tin**: Dùng **Newline-delimited JSON** (mỗi thông điệp là 1 dòng JSON kết thúc bằng `\n`), đọc bằng `BufferedReader.readLine()` và ghi bằng `BufferedWriter` + `flush()`.
- **Cơ chế chống lặp vô hạn (Loop Prevention)**: Khi nhận sự kiện từ máy đối tác, chương trình chỉ ghi vào bảng `students` và `received_events`, **tuyệt đối không** thêm sự kiện đó vào bảng `outgoing_events`.
- **Cơ chế chống trùng lặp (Idempotent Delivery)**: Lưu lại các `event_id` (UUID) đã nhận trong bảng `received_events`. Nếu nhận lại một sự kiện đã có, chương trình bỏ qua thao tác và gửi ngay phản hồi `ACK` thành công.
- **Xử lý xung đột (Conflict Resolution)**: So sánh trường mốc thời gian `updated_at`. Bản ghi mới hơn sẽ được ưu tiên giữ lại.

---

## 2. Cấu trúc thư mục dự án

```text
DatabaseSync/
├── src/
│   ├── Main.java                          # Menu Console chính của ứng dụng
│   ├── module-info.java                   # Khai báo module Java (requires java.sql)
│   ├── config/
│   │   └── AppConfig.java                 # Đọc cấu hình từ config.properties
│   ├── database/
│   │   ├── DatabaseConnection.java        # Quản lý kết nối JDBC MySQL
│   │   └── StudentDAO.java                # Xử lý CRUD, Transaction và Outgoing/Received Log
│   ├── model/
│   │   ├── Student.java                   # Đối tượng sinh viên
│   │   ├── SyncEvent.java                 # Thông điệp sự kiện đồng bộ (CHANGE)
│   │   └── AckMessage.java                # Thông điệp phản hồi (ACK)
│   ├── network/
│   │   ├── TcpServer.java                 # Lắng nghe kết nối TCP chạy nền
│   │   └── TcpClient.java                 # Kết nối sang máy đối tác gửi sự kiện
│   ├── sync/
│   │   ├── JsonUtils.java                 # Tuần tự hóa JSON thuần, không phụ thuộc thư viện ngoài
│   │   └── SyncManager.java               # Điều phối luồng đồng bộ Sync now
│   └── test/
│       └── SyncProtocolTest.java          # Kiểm thử tự động JSON và kết nối TCP Socket
├── sql/
│   └── student_db.sql                     # Script khởi tạo cơ sở dữ liệu MySQL
├── lib/
│   └── mysql-connector-j-8.3.0.jar        # Thư viện JDBC Driver của MySQL
├── config.example.properties              # File mẫu thiết lập IP/Port và DB
├── .gitignore                             # Tránh commit file cấu hình và binary
└── README.md                              # Hướng dẫn chi tiết
```

---

## 3. Chuẩn bị môi trường trên 2 máy tính (Windows)

### Bước 1: Khởi động MySQL (XAMPP)
1. Mở **XAMPP Control Panel** trên cả 2 máy.
2. Nhấn nút **Start** tại mục **MySQL** (mặc định chạy tại port 3306).

### Bước 2: Tạo Cơ sở dữ liệu `student_db`
1. Mở trình duyệt, truy cập `http://localhost/phpmyadmin/`.
2. Chọn tab **Import** (hoặc tab **SQL**).
3. Mở file [sql/student_db.sql](sql/student_db.sql), copy toàn bộ nội dung và bấm **Go** (Thực hiện).
4. Kiểm tra thấy database `student_db` xuất hiện gồm 3 bảng:
   - `students`: Bảng dữ liệu sinh viên.
   - `outgoing_events`: Bảng lưu sự kiện phát sinh tại máy này cần gửi đi.
   - `received_events`: Bảng lưu `event_id` đã nhận từ máy khác để chống trùng lặp.

### Bước 3: Xác định địa chỉ IP mạng LAN (`ipconfig`)
Trên mỗi máy, mở **Command Prompt (cmd)** hoặc **PowerShell** và gõ:
```cmd
ipconfig
```
Tìm dòng `IPv4 Address` thuộc card mạng đang kết nối chung (Wi-Fi hoặc cáp LAN).
- Ví dụ:
  - Máy A có IP LAN: `192.168.1.15`
  - Máy B có IP LAN: `192.168.1.20`

> **LƯU Ý QUAN TRỌNG:** Tuyệt đối không dùng `localhost` hoặc `127.0.0.1` làm địa chỉ của máy đối tác. `localhost` chỉ dùng cho kết nối MySQL nội bộ của chính máy đó.

### Bước 4: Mở cổng tường lửa Windows Firewall
Để 2 máy có thể kết nối TCP đến nhau qua cổng (ví dụ: `8888`), cần cho phép ứng dụng hoặc mở cổng trên Windows Firewall:
- Cách nhanh nhất: Mở PowerShell với quyền Administrator trên cả 2 máy và chạy lệnh sau để mở port 8888:
```powershell
New-NetFirewallRule -DisplayName "DatabaseSync_TCP_8888" -Direction Inbound -LocalPort 8888 -Protocol TCP -Action Allow
```

---

## 4. Cấu hình file `config.properties`

Tại thư mục gốc `DatabaseSync/`, copy file `config.example.properties` thành file `config.properties` trên từng máy.

### Trên Máy A:
```properties
# Cấu hình MySQL cục bộ của Máy A
db.host=localhost
db.port=3306
db.name=student_db
db.user=root
db.password=

# Cổng lắng nghe của Máy A
server.port=8888

# Địa chỉ của Máy B (máy đối tác)
peer.host=192.168.1.20
peer.port=8888
network.timeout=5000
```

### Trên Máy B:
```properties
# Cấu hình MySQL cục bộ của Máy B
db.host=localhost
db.port=3306
db.name=student_db
db.user=root
db.password=

# Cổng lắng nghe của Máy B
server.port=8888

# Địa chỉ của Máy A (máy đối tác)
peer.host=192.168.1.15
peer.port=8888
network.timeout=5000
```

---

## 5. Hướng dẫn Biên dịch và Chạy ứng dụng

### Cách 1: Chạy bằng Eclipse IDE
1. Mở Eclipse, chọn **File** -> **Open Projects from File System...** -> Trỏ tới thư mục `d:\JAVA\DatabaseSync`.
2. Chuột phải vào project -> **Refresh (F5)**.
3. Chuột phải vào file `src/Main.java` (hoặc `src/ui/MainFrame.java`) -> Chọn **Run As** -> **Java Application**.

### Cách 2: Chạy bằng Dòng lệnh (Terminal / PowerShell / CMD)
Tại thư mục `d:\JAVA\DatabaseSync`:

1. **Biên dịch toàn bộ:**
```cmd
javac -cp "lib\mysql-connector-j-8.3.0.jar;lib\flatlaf-3.5.4.jar" -d bin src\model\*.java src\config\*.java src\sync\*.java src\database\*.java src\network\*.java src\ui\*.java src\Main.java src\test\*.java
```

2. **Khởi chạy Giao diện đồ họa Java Swing (Mặc định - FlatLaf Dark Mode):**
```cmd
java -cp "bin;lib\mysql-connector-j-8.3.0.jar;lib\flatlaf-3.5.4.jar" Main
```

3. **Khởi chạy chế độ dòng lệnh Console (nếu muốn):**
```cmd
java -cp "bin;lib\mysql-connector-j-8.3.0.jar;lib\flatlaf-3.5.4.jar" Main --console
```

4. **Chạy kiểm thử tự động giao thức Socket & JSON:**
```cmd
java -cp "bin;lib\mysql-connector-j-8.3.0.jar;lib\flatlaf-3.5.4.jar" test.SyncProtocolTest
```

---

## 6. Kịch bản Kiểm thử & Demo 2 máy (Demo Checklist)

Khi khởi động, chương trình sẽ hiển thị Menu Console:
```text
=================================================================
     DATABASE SYNC - DONG BO DU LIEU HAI CHIEU QUA TCP/IP        
=================================================================
[Khoi dong] Kiem tra ket noi MySQL cuc bo... THANH CONG!
[TCP Server] Dang lang nghe ket noi dong bo tai cong 8888...
[Thong tin] Cong lang nghe cuc bo (Server Port): 8888
[Thong tin] May doi tac cau hinh (Peer Host:Port): 192.168.1.20:8888

-------------------------- MENU CHINH --------------------------
1. Xem danh sach sinh vien hien co (Local DB)
2. Them sinh vien moi (INSERT)
3. Sua thong tin sinh vien (UPDATE)
4. Xoa sinh vien (DELETE)
5. Xem danh sach su kien cho dong bo (Pending Events)
6. Dong bo ngay sang may doi tac (SYNC NOW)
7. Kiem tra ket noi MySQL cuc bo
8. Cau hinh IP/Port may doi tac (Peer Settings)
0. Thoat
----------------------------------------------------------------
```

Thực hiện lần lượt các bước demo sau:

### Kịch bản 1: Đồng bộ INSERT từ Máy A sang Máy B
1. **Trên Máy A**:
   - Chọn `2` (Thêm sinh viên mới).
   - Nhập: ID = `101`, Tên = `Nguyen Van An`, Email = `an@gmail.com`, Ngành = `CNTT`.
   - Chọn `5` (Xem sự kiện chờ): Thấy 1 sự kiện `INSERT` trạng thái Pending.
   - Chọn `6` (SYNC NOW).
   - Màn hình Máy A báo: `Da gui su kien [...]`, `Nhan ACK OK`.
2. **Trên Máy B**:
   - Màn hình nền của Máy B tự động in log: `[TCP Server] Nhan su kien: ... SVID=101`, gửi trả `ACK`.
   - Chọn `1` (Xem danh sách sinh viên): Thấy sinh viên ID `101` đã xuất hiện trong database của Máy B!
   - Chọn `5` (Xem sự kiện chờ trên Máy B): **Không có sự kiện nào** (chứng minh đã chống vòng lặp đồng bộ thành công).

### Kịch bản 2: Đồng bộ UPDATE từ Máy B sang Máy A (Chiều ngược lại)
1. **Trên Máy B**:
   - Chọn `3` (Sửa thông tin sinh viên).
   - Nhập ID = `101`.
   - Đổi Tên thành: `Nguyen Van An (Updated)`, giữ nguyên email, đổi Ngành = `Khoa hoc May tinh`.
   - Chọn `6` (SYNC NOW).
2. **Trên Máy A**:
   - Chọn `1` (Xem danh sách): Thông tin sinh viên ID `101` đã được cập nhật chính xác theo Máy B.

### Kịch bản 3: Đồng bộ DELETE từ Máy A sang Máy B
1. **Trên Máy A**:
   - Chọn `4` (Xóa sinh viên), nhập ID = `101`, xác nhận `y`.
   - Chọn `6` (SYNC NOW).
2. **Trên Máy B**:
   - Chọn `1`: Sinh viên `101` đã bị xóa khỏi cơ sở dữ liệu Máy B.

### Kịch bản 4: Xử lý khi Máy đối tác ngắt kết nối (Offline & Reconnect)
1. Tắt chương trình trên Máy B (hoặc tạm thời rút dây mạng Máy B).
2. **Trên Máy A**:
   - Chọn `2` (Thêm sinh viên mới): ID = `102`, Tên = `Tran Thi Binh`.
   - Chọn `6` (SYNC NOW):
     - Màn hình Máy A báo: `Khong the ket noi toi may doi tac (192.168.1.20:8888): Connection refused`.
     - Thông báo lưu ý: `Cac su kien van duoc luu o trang thai cho (Pending) de dong bo lai sau`.
   - Chọn `5`: Thấy sự kiện `102` vẫn được lưu an toàn trong bảng `outgoing_events` (`is_sent = 0`).
3. Khởi động lại chương trình trên Máy B.
4. **Trên Máy A**:
   - Chọn lại `6` (SYNC NOW).
   - Kết nối thành công, sự kiện được gửi bù và Máy B nhận được bản ghi sinh viên ID `102` đầy đủ.

### Kịch bản 5: Chống sự kiện trùng lặp (Duplicate Message Prevention)
1. Bảng `received_events` trên Máy B đã ghi nhận `event_id` của sự kiện thêm sinh viên `102`.
2. Nếu Máy A vì lý do mạng chập chờn mà gửi lại sự kiện này:
   - `StudentDAO.applyRemoteEvent()` kiểm tra thấy `event_id` đã có trong `received_events`.
   - Trả về `ACK OK` ngay lập tức mà không thực hiện câu lệnh chèn thêm một lần nữa. Không xảy ra lỗi trùng khóa chính (Duplicate Key Error).
