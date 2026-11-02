package sync;

import model.AckMessage;
import model.Student;
import model.SyncEvent;

/**
 * Tiện ích chuyển đổi JSON thủ công (không phụ thuộc thư viện ngoài).
 * Đảm bảo định dạng mỗi thông điệp là 1 dòng (newline-delimited JSON) theo đúng quy định Rule.
 */
public class JsonUtils {

    // Helper escape ký tự đặc biệt trong chuỗi JSON
    public static String escapeJson(String text) {
        if (text == null) return "";
        StringBuilder sb = new StringBuilder();
        for (char c : text.toCharArray()) {
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\b': sb.append("\\b"); break;
                case '\f': sb.append("\\f"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < ' ') {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        return sb.toString();
    }

    // Trích xuất giá trị String của một key ở cấp độ hiện tại
    public static String extractString(String json, String key) {
        String pattern = "\"" + key + "\":\"";
        int start = json.indexOf(pattern);
        if (start == -1) return null;
        start += pattern.length();
        StringBuilder val = new StringBuilder();
        boolean escape = false;
        for (int i = start; i < json.length(); i++) {
            char c = json.charAt(i);
            if (escape) {
                if (c == '"') val.append('"');
                else if (c == '\\') val.append('\\');
                else if (c == 'n') val.append('\n');
                else if (c == 'r') val.append('\r');
                else if (c == 't') val.append('\t');
                else val.append(c);
                escape = false;
            } else {
                if (c == '\\') {
                    escape = true;
                } else if (c == '"') {
                    return val.toString();
                } else {
                    val.append(c);
                }
            }
        }
        return val.toString();
    }

    // Trích xuất giá trị số (int/long) của một key
    public static Long extractLong(String json, String key) {
        String pattern = "\"" + key + "\":";
        int start = json.indexOf(pattern);
        if (start == -1) return null;
        start += pattern.length();
        while (start < json.length() && Character.isWhitespace(json.charAt(start))) {
            start++;
        }
        int end = start;
        while (end < json.length() && (Character.isDigit(json.charAt(end)) || json.charAt(end) == '-')) {
            end++;
        }
        if (start == end) return null;
        try {
            return Long.parseLong(json.substring(start, end));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static Integer extractInt(String json, String key) {
        Long val = extractLong(json, key);
        return val != null ? val.intValue() : null;
    }

    // Chuyển đổi đối tượng Student sang JSON
    public static String studentToJson(Student s) {
        if (s == null) return "null";
        return String.format("{\"id\":%d,\"name\":\"%s\",\"email\":\"%s\",\"major\":\"%s\",\"updatedAt\":%d}",
                s.getId(),
                escapeJson(s.getName()),
                escapeJson(s.getEmail()),
                escapeJson(s.getMajor()),
                s.getUpdatedAt());
    }

    // Parse JSON sang Student
    public static Student parseStudent(String json) {
        if (json == null || json.trim().isEmpty() || json.trim().equals("null")) return null;
        Integer id = extractInt(json, "id");
        String name = extractString(json, "name");
        String email = extractString(json, "email");
        String major = extractString(json, "major");
        Long updatedAt = extractLong(json, "updatedAt");

        Student s = new Student();
        if (id != null) s.setId(id);
        s.setName(name != null ? name : "");
        s.setEmail(email != null ? email : "");
        s.setMajor(major != null ? major : "");
        s.setUpdatedAt(updatedAt != null ? updatedAt : System.currentTimeMillis());
        return s;
    }

    // Chuyển đổi SyncEvent sang JSON 1 dòng (cho socket truyền)
    public static String eventToJson(SyncEvent event) {
        if (event == null) return "";
        String studentJson = (event.getStudent() != null) ? studentToJson(event.getStudent()) : "null";
        return String.format("{\"type\":\"%s\",\"eventId\":\"%s\",\"operation\":\"%s\",\"studentId\":%d,\"student\":%s,\"timestamp\":%d}",
                escapeJson(event.getType()),
                escapeJson(event.getEventId()),
                escapeJson(event.getOperation()),
                event.getStudentId(),
                studentJson,
                event.getTimestamp());
    }

    // Parse dòng JSON sang SyncEvent
    public static SyncEvent parseEvent(String json) {
        if (json == null || json.trim().isEmpty()) return null;
        SyncEvent event = new SyncEvent();
        event.setType(extractString(json, "type"));
        event.setEventId(extractString(json, "eventId"));
        event.setOperation(extractString(json, "operation"));
        Integer studentId = extractInt(json, "studentId");
        if (studentId != null) event.setStudentId(studentId);
        Long ts = extractLong(json, "timestamp");
        if (ts != null) event.setTimestamp(ts);

        // Trích xuất khối "student": { ... } nếu có
        int studentIdx = json.indexOf("\"student\":");
        if (studentIdx != -1) {
            int braceStart = json.indexOf('{', studentIdx);
            if (braceStart != -1) {
                int braceCount = 0;
                int braceEnd = -1;
                for (int i = braceStart; i < json.length(); i++) {
                    if (json.charAt(i) == '{') braceCount++;
                    else if (json.charAt(i) == '}') {
                        braceCount--;
                        if (braceCount == 0) {
                            braceEnd = i;
                            break;
                        }
                    }
                }
                if (braceEnd != -1) {
                    String studentSubJson = json.substring(braceStart, braceEnd + 1);
                    event.setStudent(parseStudent(studentSubJson));
                }
            }
        }
        return event;
    }

    // Chuyển đổi AckMessage sang JSON 1 dòng
    public static String ackToJson(AckMessage ack) {
        if (ack == null) return "";
        return String.format("{\"type\":\"%s\",\"eventId\":\"%s\",\"status\":\"%s\",\"message\":\"%s\"}",
                escapeJson(ack.getType()),
                escapeJson(ack.getEventId()),
                escapeJson(ack.getStatus()),
                escapeJson(ack.getMessage()));
    }

    // Parse dòng JSON sang AckMessage
    public static AckMessage parseAck(String json) {
        if (json == null || json.trim().isEmpty()) return null;
        AckMessage ack = new AckMessage();
        ack.setType(extractString(json, "type"));
        ack.setEventId(extractString(json, "eventId"));
        ack.setStatus(extractString(json, "status"));
        ack.setMessage(extractString(json, "message"));
        return ack;
    }
}
