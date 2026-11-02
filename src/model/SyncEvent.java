package model;

import java.util.UUID;

/**
 * Lớp đại diện cho một thông điệp sự kiện thay đổi dữ liệu (CHANGE) gửi qua TCP.
 */
public class SyncEvent {
    private String type = "CHANGE";
    private String eventId;
    private String operation; // "INSERT", "UPDATE", "DELETE"
    private int studentId;
    private Student student;
    private long timestamp;

    public SyncEvent() {
    }

    public SyncEvent(String operation, int studentId, Student student, long timestamp) {
        this.eventId = UUID.randomUUID().toString();
        this.operation = operation;
        this.studentId = studentId;
        this.student = student;
        this.timestamp = timestamp;
    }

    public SyncEvent(String eventId, String operation, int studentId, Student student, long timestamp) {
        this.eventId = eventId;
        this.operation = operation;
        this.studentId = studentId;
        this.student = student;
        this.timestamp = timestamp;
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

    public String getOperation() {
        return operation;
    }

    public void setOperation(String operation) {
        this.operation = operation;
    }

    public int getStudentId() {
        return studentId;
    }

    public void setStudentId(int studentId) {
        this.studentId = studentId;
    }

    public Student getStudent() {
        return student;
    }

    public void setStudent(Student student) {
        this.student = student;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    @Override
    public String toString() {
        return String.format("SyncEvent[ID=%s, Op=%s, StudentId=%d, Time=%d]",
                eventId, operation, studentId, timestamp);
    }
}
