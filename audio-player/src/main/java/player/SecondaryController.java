package player;

import java.io.IOException;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;

public class SecondaryController {

    @FXML
    private void switchToPrimary() throws IOException {
        App.setRoot("primary");
    }

    @FXML
    private void setDarkTheme(ActionEvent event) {
        App.setTheme("style.css");
    }

    @FXML
    private void setLightTheme(ActionEvent event) {
        App.setTheme("style-light.css");
    }
}