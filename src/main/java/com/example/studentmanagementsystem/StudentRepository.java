package com.example.studentmanagementsystem;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public class StudentRepository {
    private final ObservableList<Student> students = FXCollections.observableArrayList();
    private final Path dataFile;

    public StudentRepository() {
        this(defaultDataFile());
    }

    private static Path defaultDataFile() {
        Path projectDir = Paths.get(System.getProperty("user.dir"));
        Path target = projectDir.resolve("students.txt");
        Path old = Paths.get(System.getProperty("user.home"), "students.txt");
        if (!Files.exists(target) && Files.exists(old)) {
            try { Files.copy(old, target); } catch (IOException ignored) {}
        }
        return target;
    }

    public StudentRepository(Path file) {
        this.dataFile = file;
    }

    public ObservableList<Student> getStudents() {
        return students;
    }

    public Path getDataFile() { return dataFile; }

    public void load() throws IOException {
        students.clear();
        if (!Files.exists(dataFile)) {
            // create file and return empty list
            try { Files.createFile(dataFile); } catch (IOException ignored) {}
            return;
        }
        try (BufferedReader br = Files.newBufferedReader(dataFile, StandardCharsets.UTF_8)) {
            String line;
            List<String> legacyBuffer = new ArrayList<>(7);
            Set<Integer> seenIds = new HashSet<>(); // track duplicates
            Set<String> seenNames = new HashSet<>(); // track duplicate full names (case-insensitive, trimmed)
            Set<String> seenEmails = new HashSet<>(); // new: track emails
            while ((line = br.readLine()) != null) {
                if (line.isBlank()) continue;
                if (line.contains("|")) {
                    // Flush any incomplete legacy buffer before proceeding
                    if (!legacyBuffer.isEmpty()) {
                        // If we somehow have exactly 7 lines, attempt parse
                        if (legacyBuffer.size() == 7) {
                            Student legacy = Student.fromCsv(String.join("\n", legacyBuffer));
                            if (legacy != null && legacy.getIdNumber() > 0) {
                                String normName = normalizeName(legacy.getFullName());
                                String normEmail = normalizeEmail(legacy.getEmail());
                                if (seenIds.add(legacy.getIdNumber()) &&
                                        (normName.isEmpty() || seenNames.add(normName)) &&
                                        (normEmail.isEmpty() || seenEmails.add(normEmail))) {
                                    students.add(legacy);
                                }
                            }
                        }
                        legacyBuffer.clear();
                    }
                    Student s = Student.fromCsv(line);
                    if (s != null && s.getIdNumber() > 0) {
                        String normName = normalizeName(s.getFullName());
                        String normEmail = normalizeEmail(s.getEmail());
                        if (seenIds.add(s.getIdNumber()) &&
                                (normName.isEmpty() || seenNames.add(normName)) &&
                                (normEmail.isEmpty() || seenEmails.add(normEmail))) {
                            students.add(s);
                        }
                    }
                } else {
                    // Legacy format line (no pipe). Collect until 7 lines.
                    legacyBuffer.add(line);
                    if (legacyBuffer.size() == 7) {
                        Student legacy = Student.fromCsv(String.join("\n", legacyBuffer));
                        if (legacy != null && legacy.getIdNumber() > 0) {
                            String normName = normalizeName(legacy.getFullName());
                            String normEmail = normalizeEmail(legacy.getEmail());
                            if (seenIds.add(legacy.getIdNumber()) &&
                                    (normName.isEmpty() || seenNames.add(normName)) &&
                                    (normEmail.isEmpty() || seenEmails.add(normEmail))) {
                                students.add(legacy);
                            }
                        }
                        legacyBuffer.clear();
                    }
                }
            }
            // In case file ended mid-legacy record (ignore if incomplete)
            if (legacyBuffer.size() == 7) {
                Student legacy = Student.fromCsv(String.join("\n", legacyBuffer));
                if (legacy != null && legacy.getIdNumber() > 0) {
                    String normName = normalizeName(legacy.getFullName());
                    String normEmail = normalizeEmail(legacy.getEmail());
                    if (seenIds.add(legacy.getIdNumber()) &&
                            (normName.isEmpty() || seenNames.add(normName)) &&
                            (normEmail.isEmpty() || seenEmails.add(normEmail))) {
                        students.add(legacy);
                    }
                }
            }
        }
    }

    private static String normalizeName(String name) {
        if (name == null) return "";
        String trimmed = name.trim();
        return trimmed.isEmpty() ? "" : trimmed.toLowerCase();
    }

    private static String normalizeEmail(String email) { // new helper
        if (email == null) return "";
        String trimmed = email.trim();
        return trimmed.isEmpty() ? "" : trimmed.toLowerCase();
    }

    public void save() throws IOException {
        List<String> lines = new ArrayList<>();
        for (Student s : students) {
            lines.add(s.toCsv());
        }
        try (BufferedWriter bw = Files.newBufferedWriter(dataFile, StandardCharsets.UTF_8)) {
            for (String l : lines) {
                bw.write(l);
                bw.newLine();
            }
        }
    }

    public Optional<Student> findById(int id) {
        if (id <= 0) return Optional.empty();
        return students.stream().filter(s -> id == s.getIdNumber()).findFirst();
    }

    public boolean add(Student s) {
        if (s == null) return false;
        if (findById(s.getIdNumber()).isPresent()) return false;
        String normName = normalizeName(s.getFullName());
        if (!normName.isEmpty() && students.stream().anyMatch(o -> normalizeName(o.getFullName()).equals(normName))) return false;
        String normEmail = normalizeEmail(s.getEmail());
        if (!normEmail.isEmpty() && students.stream().anyMatch(o -> normalizeEmail(o.getEmail()).equals(normEmail))) return false;
        return students.add(s);
    }

    public boolean update(int originalId, Student updated) {
        if (originalId <= 0 || updated == null) return false;
        if (originalId != updated.getIdNumber() && findById(updated.getIdNumber()).isPresent()) {
            return false;
        }
        String normName = normalizeName(updated.getFullName());
        if (!normName.isEmpty() && students.stream().anyMatch(o -> o.getIdNumber() != originalId && normalizeName(o.getFullName()).equals(normName))) return false;
        String normEmail = normalizeEmail(updated.getEmail());
        if (!normEmail.isEmpty() && students.stream().anyMatch(o -> o.getIdNumber() != originalId && normalizeEmail(o.getEmail()).equals(normEmail))) return false;
        for (int i = 0; i < students.size(); i++) {
            if (originalId == students.get(i).getIdNumber()) {
                students.set(i, updated);
                return true;
            }
        }
        return false;
    }

    public boolean delete(int id) {
        return students.removeIf(s -> s.getIdNumber() == id);
    }

    // Basic validators
    public static List<String> validate(Student s, boolean checkDuplicateId, StudentRepository repo, Integer originalId) {
        List<String> errors = new ArrayList<>();
        if (s.getIdNumber() <= 0) errors.add("ID number must be a positive integer");
        if (s.getAge() < 0 || s.getAge() > 150) errors.add("Age must be between 0 and 150");
        if (s.getEmail() != null && !s.getEmail().isBlank() && !s.getEmail().matches("^[^@\n\r]+@[^@\n\r]+\\.[^@\n\r]+$")) errors.add("Invalid email format");
        LocalDate dob = s.getBirthday();
        if (dob != null && dob.isAfter(LocalDate.now())) errors.add("Birthday cannot be in the future");
        String fullName = s.getFullName() == null ? "" : s.getFullName().trim();
        if (!fullName.isEmpty() && fullName.matches(".*\\d.*")) errors.add("Full name cannot contain numbers");
        if (checkDuplicateId && repo != null) {
            Optional<Student> existing = repo.findById(s.getIdNumber());
            if (existing.isPresent() && (originalId == null || existing.get().getIdNumber() != originalId)) {
                errors.add("ID already exists");
            }
        }
        // Duplicate full name check (case-insensitive) ignoring the original record if updating
        if (repo != null && !fullName.isEmpty()) {
            String norm = fullName.toLowerCase();
            boolean duplicateName = repo.students.stream()
                    .anyMatch(other -> other.getIdNumber() != s.getIdNumber() && normalizeName(other.getFullName()).equals(norm));
            if (duplicateName) errors.add("Full name already exists");
        }
        String email = s.getEmail() == null ? "" : s.getEmail().trim();
        if (repo != null && !email.isEmpty()) {
            String normE = email.toLowerCase();
            boolean duplicateEmail = repo.students.stream()
                    .anyMatch(other -> other.getIdNumber() != s.getIdNumber() && normalizeEmail(other.getEmail()).equals(normE));
            if (duplicateEmail) errors.add("Email already exists");
        }
        return errors;
    }
}
