package com.example.studentmanagementsystem;

import javafx.beans.property.*;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

public class Student {
    private final IntegerProperty idNumber = new SimpleIntegerProperty(this, "idNumber", 0);
    private final StringProperty fullName = new SimpleStringProperty(this, "fullName", "");
    private final IntegerProperty age = new SimpleIntegerProperty(this, "age", 0);
    private final StringProperty address = new SimpleStringProperty(this, "address", "");
    private final StringProperty courseYear = new SimpleStringProperty(this, "courseYear", "");
    private final ObjectProperty<LocalDate> birthday = new SimpleObjectProperty<>(this, "birthday", null);
    private final StringProperty email = new SimpleStringProperty(this, "email", "");

    public Student() {}

    public Student(int idNumber, String fullName, int age, String address, String courseYear, LocalDate birthday, String email) {
        setIdNumber(idNumber);
        setFullName(fullName);
        setAge(age);
        setAddress(address);
        setCourseYear(courseYear);
        setBirthday(birthday);
        setEmail(email);
    }

    // Backward-compatible constructor (kept signature but converts id string to int best-effort)
    public Student(String idNumber, String fullName, int age, String address, String courseYear, LocalDate birthday, String email) {
        this(parseIntSafe(idNumber), fullName, age, address, courseYear, birthday, email);
    }

    // Legacy constructor without full name
    public Student(String idNumber, int age, String address, String courseYear, LocalDate birthday, String email) {
        this(parseIntSafe(idNumber), "", age, address, courseYear, birthday, email);
    }

    public int getIdNumber() { return idNumber.get(); }
    public void setIdNumber(int value) { idNumber.set(value); }
    public IntegerProperty idNumberProperty() { return idNumber; }

    public String getFullName() { return fullName.get(); }
    public void setFullName(String value) { fullName.set(value); }
    public StringProperty fullNameProperty() { return fullName; }

    public int getAge() { return age.get(); }
    public void setAge(int value) { age.set(value); }
    public IntegerProperty ageProperty() { return age; }

    public String getAddress() { return address.get(); }
    public void setAddress(String value) { address.set(value); }
    public StringProperty addressProperty() { return address; }

    public String getCourseYear() { return courseYear.get(); }
    public void setCourseYear(String value) { courseYear.set(value); }
    public StringProperty courseYearProperty() { return courseYear; }

    public LocalDate getBirthday() { return birthday.get(); }
    public void setBirthday(LocalDate value) { birthday.set(value); }
    public ObjectProperty<LocalDate> birthdayProperty() { return birthday; }

    public String getEmail() { return email.get(); }
    public void setEmail(String value) { email.set(value); }
    public StringProperty emailProperty() { return email; }

    public String toCsv() {
        // New format: ID(int), FullName, Age, Address, CourseYear, Birthday(ISO), Email
        return getIdNumber() + "," + escape(getFullName()) + "," + getAge() + "," + escape(getAddress()) + "," +
                escape(getCourseYear()) + "," + (getBirthday() == null ? "" : getBirthday().format(DateTimeFormatter.ISO_DATE)) + "," +
                escape(getEmail());
    }

    public static Student fromCsv(String line) {
        // Try new format (7 fields). If only 6 fields, treat as legacy without fullName.
        String[] parts = CsvUtil.splitCsv(line, 7);
        if (parts == null) return null;
        boolean legacy = countCommas(line) < 6;
        try {
            if (legacy) {
                // Legacy: ID, Age, Address, CourseYear, Birthday, Email
                int id = parseIntSafe(parts[0]);
                int age = parseIntSafe(parts[1]);
                String address = CsvUtil.unescape(parts[2]);
                String courseYear = CsvUtil.unescape(parts[3]);
                LocalDate dob = null;
                try { dob = parts[4].isBlank() ? null : LocalDate.parse(parts[4]); } catch (Exception ignored) {}
                String email = CsvUtil.unescape(parts[5]);
                if (id <= 0) return null; // skip invalid legacy rows
                return new Student(id, "", age, address, courseYear, dob, email);
            } else {
                // New: ID, FullName, Age, Address, CourseYear, Birthday, Email
                int id = parseIntSafe(parts[0]);
                String fullName = CsvUtil.unescape(parts[1]);
                int age = parseIntSafe(parts[2]);
                String address = CsvUtil.unescape(parts[3]);
                String courseYear = CsvUtil.unescape(parts[4]);
                LocalDate dob = null;
                try { dob = parts[5].isBlank() ? null : LocalDate.parse(parts[5]); } catch (Exception ignored) {}
                String email = CsvUtil.unescape(parts[6]);
                if (id <= 0) return null;
                return new Student(id, fullName, age, address, courseYear, dob, email);
            }
        } catch (Exception ex) {
            return null;
        }
    }

    private static int countCommas(String s) {
        if (s == null) return 0;
        int c = 0;
        boolean inQuotes = false;
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch == '"') {
                inQuotes = !inQuotes;
            } else if (ch == ',' && !inQuotes) {
                c++;
            }
        }
        return c;
    }

    private static String escape(String s) {
        if (s == null) return "";
        if (s.contains(",") || s.contains("\"") || s.contains("\n") || s.contains("\r")) {
            return '"' + s.replace("\"", "\"\"") + '"';
        }
        return s;
    }

    private static int parseIntSafe(String s) {
        if (s == null) return 0;
        try {
            return Integer.parseInt(s.trim());
        } catch (Exception e) {
            return 0;
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Student)) return false;
        Student student = (Student) o;
        return getIdNumber() == student.getIdNumber();
    }

    @Override
    public int hashCode() {
        return Objects.hash(getIdNumber());
    }
}

class CsvUtil {
    // Simple CSV splitter supporting quotes
    public static String[] splitCsv(String line, int expected) {
        if (line == null) return null;
        String[] out = new String[expected];
        StringBuilder sb = new StringBuilder();
        int idx = 0;
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                        sb.append('"');
                        i++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    sb.append(c);
                }
            } else {
                if (c == '"') {
                    inQuotes = true;
                } else if (c == ',') {
                    if (idx < expected) out[idx++] = sb.toString();
                    sb.setLength(0);
                } else {
                    sb.append(c);
                }
            }
        }
        if (idx < expected) out[idx++] = sb.toString();
        while (idx < expected) out[idx++] = "";
        return out;
    }

    public static String unescape(String s) { return s == null ? "" : s; }
}
