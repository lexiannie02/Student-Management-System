package com.example.studentmanagementsystem;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import javafx.animation.PauseTransition;
import javafx.util.Duration;

import java.io.IOException;

public class LoginController {
    @FXML private TextField usernameField;
    @FXML private PasswordField passwordField;
    @FXML private Label errorLabel;

    private final StudentRepository repository = new StudentRepository();
    private PauseTransition errorClear;

    @FXML
    public void initialize() {
        errorClear = new PauseTransition(Duration.seconds(3));
        errorClear.setOnFinished(e -> errorLabel.setText(""));
        try {
            repository.load();
        } catch (Exception e) {
            showError("Failed to load data: " + e.getMessage());
        }
    }

    @FXML
    public void onLoginClick(ActionEvent event) {
        String u = usernameField.getText() == null ? "" : usernameField.getText().trim();
        String p = passwordField.getText() == null ? "" : passwordField.getText().trim();
        if ("admin".equals(u) && "admin".equals(p)) {
            goToManagement();
        } else {
            showError("Invalid credentials. Use admin/admin");
        }
    }

    private void goToManagement() {
        Stage stage = (Stage) usernameField.getScene().getWindow();
        try {
            FXMLLoader loader = new FXMLLoader(SMSApplication.class.getResource("student-management-view.fxml"));
            Scene scene = new Scene(loader.load(), 900, 520);
            StudentManagementController controller = loader.getController();
            controller.setRepository(repository);
            stage.setTitle("Student Management");
            stage.setScene(scene);
            stage.setMaximized(false);
            stage.setResizable(false);
        } catch (IOException e) {
            showError("Failed to open management: " + e.getMessage());
        }
    }

    private void showError(String msg) {
        errorLabel.setText(msg);
        errorClear.stop();
        errorClear.playFromStart();
    }
}
