package com.example.studentmanagementsystem;

import javafx.fxml.FXML;
import javafx.scene.control.Label;

public class SMSController {
    @FXML
    private Label welcomeText;

    @FXML
    protected void onHelloButtonClick() {
        welcomeText.setText("Welcome to JavaFX Application!");
    }
}