package com.example.studentmanagementsystem;

import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.stage.Stage;
import javafx.animation.PauseTransition;
import javafx.util.Duration;
import javafx.scene.control.TextFormatter;
import javafx.util.StringConverter;

import java.net.URL;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;

public class StudentFormController implements Initializable {
    @FXML private Label titleLabel;
    @FXML private TextField idField;
    @FXML private TextField fullNameField;
    @FXML private TextField ageField;
    @FXML private TextField addressField;
    @FXML private TextField courseField;
    @FXML private DatePicker birthdayPicker;
    @FXML private TextField emailField;
    @FXML private Label errorLabel;
    @FXML private Button okButton;
    @FXML private Button cancelButton;

    private StudentRepository repository;
    private boolean isEdit;
    private Integer originalId;
    private Student result;

    private PauseTransition errorClear;
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final DateTimeFormatter[] FLEX_DATE_FORMATS = new DateTimeFormatter[] {
            DateTimeFormatter.ISO_LOCAL_DATE,
            DateTimeFormatter.ofPattern("M/d/uuuu", Locale.ENGLISH).withResolverStyle(ResolverStyle.STRICT),
            DateTimeFormatter.ofPattern("MM/dd/uuuu", Locale.ENGLISH).withResolverStyle(ResolverStyle.STRICT),
            DateTimeFormatter.ofPattern("d/M/uuuu", Locale.ENGLISH).withResolverStyle(ResolverStyle.STRICT),
            DateTimeFormatter.ofPattern("dd/MM/uuuu", Locale.ENGLISH).withResolverStyle(ResolverStyle.STRICT),
            DateTimeFormatter.ofPattern("uuuu/M/d", Locale.ENGLISH).withResolverStyle(ResolverStyle.STRICT),
            DateTimeFormatter.ofPattern("uuuu/MM/dd", Locale.ENGLISH).withResolverStyle(ResolverStyle.STRICT),
            DateTimeFormatter.ofPattern("d-M-uuuu", Locale.ENGLISH).withResolverStyle(ResolverStyle.STRICT),
            DateTimeFormatter.ofPattern("dd-MM-uuuu", Locale.ENGLISH).withResolverStyle(ResolverStyle.STRICT),
            DateTimeFormatter.ofPattern("M-d-uuuu", Locale.ENGLISH).withResolverStyle(ResolverStyle.STRICT),
            DateTimeFormatter.ofPattern("MM-dd-uuuu", Locale.ENGLISH).withResolverStyle(ResolverStyle.STRICT),
            DateTimeFormatter.ofPattern("uuuu.MM.dd", Locale.ENGLISH).withResolverStyle(ResolverStyle.STRICT),
            DateTimeFormatter.ofPattern("MMM d, uuuu", Locale.ENGLISH).withResolverStyle(ResolverStyle.STRICT),
            DateTimeFormatter.ofPattern("MMM d uuuu", Locale.ENGLISH).withResolverStyle(ResolverStyle.STRICT),
            DateTimeFormatter.ofPattern("d MMM uuuu", Locale.ENGLISH).withResolverStyle(ResolverStyle.STRICT)
    };

    private static LocalDate parseFlexibleDate(String s) {
        if (s == null) return null;
        String t = s.trim();
        if (t.isEmpty()) return null;
        for (DateTimeFormatter fmt : FLEX_DATE_FORMATS) {
            try { return LocalDate.parse(t, fmt); } catch (Exception ignored) {}
        }
        return null;
    }

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        errorClear = new PauseTransition(Duration.seconds(3));
        errorClear.setOnFinished(e -> errorLabel.setText(""));

        // ID: allow digits only, up to 10 characters
        idField.setTextFormatter(new TextFormatter<>(change -> {
            String next = change.getControlNewText();
            if (next.matches("\\d{0,10}")) return change; // allow
            return null; // reject non-digit edits
        }));

        // Age: allow digits only, up to 3 characters (0-999)
        ageField.setTextFormatter(new TextFormatter<>(change -> {
            String next = change.getControlNewText();
            if (next.matches("\\d{0,3}")) return change; // allow
            return null; // reject non-digit edits
        }));

        // Birthday: flexible converter and prompt
        birthdayPicker.setPromptText("YYYY-MM-DD or MM/DD/YYYY");
        birthdayPicker.setConverter(new StringConverter<>() {
            @Override public String toString(LocalDate date) { return date == null ? "" : date.format(DATE_FMT); }
            @Override public LocalDate fromString(String s) { return parseFlexibleDate(s); }
        });
    }

    public void setData(StudentRepository repo, Student initial, boolean isEdit) {
        this.repository = repo;
        this.isEdit = isEdit;
        if (initial != null) {
            this.originalId = initial.getIdNumber();
            idField.setText(Integer.toString(initial.getIdNumber()));
            fullNameField.setText(initial.getFullName());
            ageField.setText(Integer.toString(initial.getAge()));
            addressField.setText(initial.getAddress());
            courseField.setText(initial.getCourseYear());
            birthdayPicker.setValue(initial.getBirthday());
            emailField.setText(initial.getEmail());
        }
        titleLabel.setText(isEdit ? "Edit Student" : "Add Student");
    }

    public Student getResult() { return result; }

    @FXML
    private void onOk() {
        errorLabel.setText("");
        List<String> localErrors = new ArrayList<>();

        // Form-level quick checks before building Student
        String ageText = ageField.getText() == null ? "" : ageField.getText().trim();
        if (ageText.isEmpty()) {
            localErrors.add("Age is required");
        }
        String email = emailField.getText() == null ? "" : emailField.getText().trim();
        if (!email.isEmpty() && !(email.contains("@") && email.contains("."))) {
            localErrors.add("Email must contain '@' and '.'");
        }
        // Birthday flexible format check
        String dobText = birthdayPicker.getEditor().getText() == null ? "" : birthdayPicker.getEditor().getText().trim();
        LocalDate parsedDob = null;
        if (!dobText.isEmpty()) {
            parsedDob = parseFlexibleDate(dobText);
            if (parsedDob == null) {
                localErrors.add("Birthday must be a valid date (e.g., YYYY-MM-DD or MM/DD/YYYY)");
            }
        }

        Student s = readForm();
        if (parsedDob != null) {
            s.setBirthday(parsedDob);
        }

        // Repository-level validations (ID integer, duplicates, email regex, age bounds, etc.)
        localErrors.addAll(StudentRepository.validate(s, !isEdit, repository, originalId));

        if (!localErrors.isEmpty()) {
            errorLabel.setText(String.join("; ", localErrors));
            errorClear.stop();
            errorClear.playFromStart();
            return;
        }
        // All good: set result and close
        this.result = s;
        close();
    }

    @FXML
    private void onCancel() { this.result = null; close(); }

    private void close() {
        Stage stage = (Stage) okButton.getScene().getWindow();
        stage.close();
    }

    private Student readForm() {
        Student s = new Student();
        // Parse ID as integer with validation
        int id = 0;
        try {
            String raw = idField.getText() == null ? "" : idField.getText().trim();
            id = Integer.parseInt(raw);
        } catch (Exception ex) {
            // leave id as 0; validator will show friendly message
        }
        s.setIdNumber(id);

        String fullName = trim(fullNameField.getText());
        s.setFullName(fullName);
        s.setAddress(trim(addressField.getText()));
        s.setCourseYear(trim(courseField.getText()));
        s.setEmail(trim(emailField.getText()));
        LocalDate dob = birthdayPicker.getValue();
        s.setBirthday(dob);
        int age = 0;
        try { age = Integer.parseInt(ageField.getText().trim()); } catch (Exception ignored) {}
        s.setAge(age);
        return s;
    }

    private static String trim(String v) { return v == null ? "" : v.trim(); }
}
