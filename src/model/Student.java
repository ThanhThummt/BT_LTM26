package model;

/**
 * Lớp đại diện cho đối tượng Sinh viên.
 */
public class Student {
    private int id;
    private String name;
    private String email;
    private String major;
    private long updatedAt;

    public Student() {
    }

    public Student(int id, String name, String email, String major, long updatedAt) {
        this.id = id;
        this.name = name;
        this.email = email;
        this.major = major;
        this.updatedAt = updatedAt;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getMajor() {
        return major;
    }

    public void setMajor(String major) {
        this.major = major;
    }

    public long getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(long updatedAt) {
        this.updatedAt = updatedAt;
    }

    @Override
    public String toString() {
        return String.format("Student[ID=%d, Name='%s', Email='%s', Major='%s', UpdatedAt=%d]",
                id, name, email, major, updatedAt);
    }
}
