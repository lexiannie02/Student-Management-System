package com.example.studentmanagementsystem;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.ListChangeListener;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.animation.PauseTransition;
import javafx.animation.Timeline;
import javafx.animation.KeyFrame;
import javafx.util.Duration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Predicate;

public class StudentManagementController {
    @FXML private TableView<Student> table;
    @FXML private TableColumn<Student, Number> idCol;
    @FXML private TableColumn<Student, String> fullNameCol;
    @FXML private TableColumn<Student, Number> ageCol;
    @FXML private TableColumn<Student, String> addressCol;
    @FXML private TableColumn<Student, String> courseCol;
    @FXML private TableColumn<Student, String> birthdayCol;
    @FXML private TableColumn<Student, String> emailCol;

    @FXML private TextField searchField;

    @FXML private Label errorLabel;
    @FXML private Label statusLabel;

    private final DateTimeFormatter DATE_FMT = DateTimeFormatter.ISO_DATE;

    private StudentRepository repository;
    private FilteredList<Student> filtered;
    private SortedList<Student> sorted;

    private PauseTransition statusClear;
    private PauseTransition errorClear;

    // Auto-save and auto-reload
    private PauseTransition autoSaveDebounce;
    private Timeline autoReloadTimer;

    private long lastKnownFileModifiedMillis = 0L;
    private long lastSelfSaveMillis = 0L;

    public void setRepository(StudentRepository repository) {
        this.repository = repository;
        setupTable();
        setupSearch();
        status("Loaded " + repository.getStudents().size() + " students from " + repository.getDataFile());

        // Init auto-save debounce (1s after last change)
        autoSaveDebounce = new PauseTransition(Duration.seconds(1));
        autoSaveDebounce.setOnFinished(e -> doAutoSave());
        repository.getStudents().addListener((ListChangeListener<Student>) c -> {
            // Any mutation triggers a debounced save
            autoSaveDebounce.stop();
            autoSaveDebounce.playFromStart();
        });

        // Initialize lastKnownFileModifiedMillis
        lastKnownFileModifiedMillis = currentFileModified();

        // Start auto-reload poll (every 3s)
        autoReloadTimer = new Timeline(new KeyFrame(Duration.seconds(1.5), e -> maybeAutoReload()));
        autoReloadTimer.setCycleCount(Timeline.INDEFINITE);
        autoReloadTimer.play();
    }

    @FXML
    public void initialize() {
        // repository is injected from LoginController
        statusClear = new PauseTransition(Duration.seconds(1.5));
        statusClear.setOnFinished(e -> statusLabel.setText(""));
        errorClear = new PauseTransition(Duration.seconds(1.5));
        errorClear.setOnFinished(e -> errorLabel.setText(""));
    }

    private void setupTable() {
        filtered = new FilteredList<>(repository.getStudents(), s -> true);
        sorted = new SortedList<>(filtered);
        sorted.comparatorProperty().bind(table.comparatorProperty());
        table.setItems(sorted);

        idCol.setCellValueFactory(data -> data.getValue().idNumberProperty());
        fullNameCol.setCellValueFactory(data -> data.getValue().fullNameProperty());
        ageCol.setCellValueFactory(data -> data.getValue().ageProperty());
        addressCol.setCellValueFactory(data -> data.getValue().addressProperty());
        courseCol.setCellValueFactory(data -> data.getValue().courseYearProperty());
        birthdayCol.setCellValueFactory(data -> new SimpleStringProperty(
                data.getValue().getBirthday() == null ? "" : DATE_FMT.format(data.getValue().getBirthday())));
        emailCol.setCellValueFactory(data -> data.getValue().emailProperty());

        // Double-click to edit
        table.setRowFactory(tv -> {
            TableRow<Student> row = new TableRow<>();
            row.setOnMouseClicked(evt -> {
                if (evt.getClickCount() == 2 && !row.isEmpty()) {
                    onUpdate();
                }
            });
            return row;
        });
    }

    private void setupSearch() {
        searchField.textProperty().addListener((obs, o, n) -> filtered.setPredicate(buildPredicate(n)));
    }

    private Predicate<Student> buildPredicate(String query) {
        if (query == null || query.isBlank()) return s -> true;
        String q = query.toLowerCase(Locale.ROOT).trim();
        return s -> {
            if (s == null) return false;
            return contains(Integer.toString(s.getIdNumber()), q)
                    || contains(s.getFullName(), q)
                    || contains(Integer.toString(s.getAge()), q)
                    || contains(s.getAddress(), q)
                    || contains(s.getCourseYear(), q)
                    || contains(s.getEmail(), q)
                    || (s.getBirthday() != null && contains(DATE_FMT.format(s.getBirthday()), q));
        };
    }

    private boolean contains(String value, String q) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(q);
    }

    @FXML
    public void onAdd() {
        clearError();
        Optional<Student> res = openStudentDialog(null, false);
        if (res.isEmpty()) return; // cancelled
        Student s = res.get();
        List<String> errors = StudentRepository.validate(s, true, repository, null);
        if (!errors.isEmpty()) {
            showError(String.join("; ", errors));
            return;
        }
        if (!repository.add(s)) {
            showError("Student with same ID already exists");
            return;
        }
        status("Added student " + s.getIdNumber());
        table.getSelectionModel().select(s);
    }

    @FXML
    public void onUpdate() {
        clearError();
        Student selected = table.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showError("Select a student to edit");
            return;
        }
        int originalId = selected.getIdNumber();
        Optional<Student> res = openStudentDialog(selected, true);
        if (res.isEmpty()) return; // cancelled
        Student updated = res.get();
        List<String> errors = StudentRepository.validate(updated, false, repository, originalId);
        if (!errors.isEmpty()) {
            showError(String.join("; ", errors));
            return;
        }
        if (repository.findById(originalId).isEmpty()) {
            showError("Student not found: " + originalId);
            return;
        }
        if (!repository.update(originalId, updated)) {
            showError("Failed to update student");
            return;
        }
        status("Updated student " + originalId + " -> " + updated.getIdNumber());
        table.refresh();
        table.getSelectionModel().select(updated);
    }

    @FXML
    public void onDelete() {
        clearError();
        Student selected = table.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showError("Select a student to delete");
            return;
        }
        int id = selected.getIdNumber();
        boolean ok = repository.delete(id);
        if (ok) {
            status("Deleted student " + id);
        } else {
            showError("Student not found: " + id);
        }
    }

    @FXML
    public void onReload() {
        try {
            repository.load();
            lastKnownFileModifiedMillis = currentFileModified();
            status("Reloaded from file");
        } catch (IOException e) {
            showError("Reload failed: " + e.getMessage());
        }
    }

    @FXML
    public void onSave() {
        try {
            repository.save();
            lastSelfSaveMillis = System.currentTimeMillis();
            lastKnownFileModifiedMillis = currentFileModified();
            status("Saved to file: " + repository.getDataFile());
        } catch (IOException e) {
            showError("Save failed: " + e.getMessage());
        }
    }

    private Optional<Student> openStudentDialog(Student initial, boolean isEdit) {
        try {
            FXMLLoader loader = new FXMLLoader(SMSApplication.class.getResource("student-form.fxml"));
            Scene scene = new Scene(loader.load(), 450, 400);
            StudentFormController ctrl = loader.getController();
            ctrl.setData(repository, initial, isEdit);
            Stage dialog = new Stage();
            dialog.initModality(Modality.WINDOW_MODAL);
            dialog.initOwner(table.getScene().getWindow());
            dialog.setTitle(isEdit ? "Edit Student" : "Add Student");
            dialog.setScene(scene);
            dialog.setMaximized(false);
            dialog.setResizable(false);
            dialog.showAndWait();
            return Optional.ofNullable(ctrl.getResult());
        } catch (IOException e) {
            showError("Failed to open form: " + e.getMessage());
            return Optional.empty();
        }
    }

    private void status(String msg) {
        statusLabel.setText(msg);
        statusClear.stop();
        statusClear.playFromStart();
    }

    private void showError(String msg) {
        errorLabel.setText(msg);
        errorClear.stop();
        errorClear.playFromStart();
    }

    private void clearError() {
        errorClear.stop();
        errorLabel.setText("");
    }

    private void doAutoSave() {
        try {
            repository.save();
            lastSelfSaveMillis = System.currentTimeMillis();
            lastKnownFileModifiedMillis = currentFileModified();
            // Quiet status to avoid spam; uncomment to show
            // status("Auto-saved");
        } catch (IOException ex) {
            showError("Auto-save failed: " + ex.getMessage());
        }
    }

    private void maybeAutoReload() {
        long mod = currentFileModified();
        if (mod <= 0) return; // file may not exist yet
        // If file changed after our last known and not just from our own save, reload
        if (mod > lastKnownFileModifiedMillis && mod > lastSelfSaveMillis + 400) { // 400ms guard
            try {
                repository.load();
                lastKnownFileModifiedMillis = mod;
                status("Auto-reloaded from file");
            } catch (IOException ex) {
                showError("Auto-reload failed: " + ex.getMessage());
            }
        }
    }

    private long currentFileModified() {
        try {
            Path p = repository.getDataFile();
            if (p == null || !Files.exists(p)) return 0L;
            return Files.getLastModifiedTime(p).toMillis();
        } catch (IOException e) {
            return 0L;
        }
    }

    @FXML
    public void onLogout() {
        // Attempt to save before leaving
        try {
            repository.save();
        } catch (IOException e) {
            // best-effort; staying silent here or could show a brief message
        }
        // Stop timers
        if (autoSaveDebounce != null) autoSaveDebounce.stop();
        if (autoReloadTimer != null) autoReloadTimer.stop();
        if (statusClear != null) statusClear.stop();
        if (errorClear != null) errorClear.stop();

        // Switch back to login view on same stage
        try {
            FXMLLoader loader = new FXMLLoader(SMSApplication.class.getResource("login-view.fxml"));
            Scene scene = new Scene(loader.load(), 420, 260);
            Stage stage = (Stage) table.getScene().getWindow();
            stage.setTitle("Login - Student Management");
            stage.setScene(scene);
            stage.setMaximized(false);
            stage.setResizable(false);
        } catch (IOException e) {
            // If this fails, we cannot navigate; show inline error
            showError("Logout failed: " + e.getMessage());
        }
    }
}
