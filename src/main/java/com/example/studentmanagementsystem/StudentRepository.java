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
import java.util.List;
import java.util.Optional;

public class StudentRepository {
    private final ObservableList<Student> students = FXCollections.observableArrayList();
    private final Path dataFile;

    public StudentRepository() {
        this(Paths.get(System.getProperty("user.home"), "students.csv"));
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
            while ((line = br.readLine()) != null) {
                if (line.isBlank()) continue;
                Student s = Student.fromCsv(line);
                if (s != null && s.getIdNumber() > 0) {
                    students.add(s);
                }
            }
        }
    }

    public void save() throws IOException {
        List<String> lines = new ArrayList<>();
        for (Student s : students) {
            lines.add(s.toCsv());
        }
        try (BufferedWriter bw = Files.newBufferedWriter(dataFile, StandardCharsets.UTF_8)) {
            for (String line : lines) {
                bw.write(line);
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
        return students.add(s);
    }

    public boolean update(int originalId, Student updated) {
        if (originalId <= 0 || updated == null) return false;
        // Prevent ID collisions
        if (originalId != updated.getIdNumber() && findById(updated.getIdNumber()).isPresent()) {
            return false;
        }
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
        return errors;
    }
}
