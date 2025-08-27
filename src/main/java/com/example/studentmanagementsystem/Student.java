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

    /**
     * Persist student to single line using '|' delimiter and custom escaping.
     * Escapes: backslash -> \\ , newline -> \n , carriage return -> \r , pipe -> \p
     */
    public String toCsv() {
        return getIdNumber() + "|" +
                encode(getFullName()) + "|" +
                getAge() + "|" +
                encode(getAddress()) + "|" +
                encode(getCourseYear()) + "|" +
                (getBirthday() == null ? "" : getBirthday().format(DateTimeFormatter.ISO_DATE)) + "|" +
                encode(getEmail());
    }

    /**
     * Flexible parser supporting both the new single-line pipe-delimited format and
     * the legacy 7-line block format separated by newlines.
     */
    public static Student fromCsv(String data) {
        if (data == null || data.isEmpty()) return null;
        try {
            String[] parts;
            boolean legacyBlock = !data.contains("|") && data.chars().filter(ch -> ch == '\n').count() >= 6;
            if (legacyBlock) {
                parts = data.split("\n", 7);
                if (parts.length < 7) return null;
                int id = parseIntSafe(parts[0]);
                String fullName = parts[1];
                int age = parseIntSafe(parts[2]);
                String address = parts[3];
                String courseYear = parts[4];
                LocalDate dob = null;
                try { dob = parts[5].isBlank() ? null : LocalDate.parse(parts[5]); } catch (Exception ignored) {}
                String email = parts[6];
                if (id <= 0) return null;
                return new Student(id, fullName, age, address, courseYear, dob, email);
            } else {
                parts = data.split("\\|", -1);
                if (parts.length < 7) return null;
                int id = parseIntSafe(parts[0]);
                String fullName = decode(parts[1]);
                int age = parseIntSafe(parts[2]);
                String address = decode(parts[3]);
                String courseYear = decode(parts[4]);
                LocalDate dob = null;
                if (!parts[5].isBlank()) {
                    try { dob = LocalDate.parse(parts[5]); } catch (Exception ignored) {}
                }
                String email = decode(parts[6]);
                if (id <= 0) return null;
                return new Student(id, fullName, age, address, courseYear, dob, email);
            }
        } catch (Exception ex) {
            return null;
        }
    }

    private static String encode(String s) {
        if (s == null) return "";
        return s
                .replace("\\", "\\\\")
                .replace("|", "\\p")
                .replace("\r", "\\r")
                .replace("\n", "\\n");
    }

    private static String decode(String s) {
        if (s == null) return "";
        StringBuilder out = new StringBuilder();
        boolean escape = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (!escape) {
                if (c == '\\') {
                    escape = true;
                } else {
                    out.append(c);
                }
            } else {
                switch (c) {
                    case 'n': out.append('\n'); break;
                    case 'r': out.append('\r'); break;
                    case 'p': out.append('|'); break;
                    case '\\': out.append('\\'); break;
                    default: out.append(c); break; // unknown sequence, keep literal
                }
                escape = false;
            }
        }
        if (escape) out.append('\\');
        return out.toString();
    }

    private static int parseIntSafe(String s) {
        if (s == null) return 0;
        try { return Integer.parseInt(s.trim()); } catch (Exception e) { return 0; }
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
    // Retained for backward compatibility (now trivial)
    public static String[] splitCsv(String line, int expected) { return new String[]{ line }; }
    public static String unescape(String s) { return s == null ? "" : s; }
}
