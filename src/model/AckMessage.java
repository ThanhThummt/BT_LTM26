package model;

/**
 * Lớp đại diện cho thông điệp phản hồi (ACK) sau khi xử lý sự kiện đồng bộ qua TCP.
 */
public class AckMessage {
    private String type = "ACK";
    private String eventId;
    private String status; // "OK" hoặc "ERROR"
    private String message;

    public AckMessage() {
    }

    public AckMessage(String eventId, String status, String message) {
        this.type = "ACK";
        this.eventId = eventId;
        this.status = status;
        this.message = message;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getEventId() {
        return eventId;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public boolean isSuccess() {
        return "OK".equalsIgnoreCase(status);
    }

    @Override
    public String toString() {
        return String.format("AckMessage[EventId=%s, Status=%s, Message='%s']", eventId, status, message);
    }
}
