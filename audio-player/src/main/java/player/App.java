package player;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;

public class App extends Application {

    private static Scene scene;
    private static String currentTheme = "style.css"; // ডিফল্ট Dark Theme

    @Override
    public void start(Stage stage) throws IOException {
        scene = new Scene(loadFXML("primary"), 1100, 750);
        applyTheme();
        stage.setTitle("Interactive 2D Audio Visualizer");
        stage.setScene(scene);
        stage.show();
    }

    static void setRoot(String fxml) throws IOException {
        scene.setRoot(loadFXML(fxml));
        applyTheme(); // রুট চেঞ্জ হলে থিম আবার অ্যাপ্লাই হবে
    }

    public static void setTheme(String themeFile) {
        currentTheme = themeFile;
        applyTheme();
    }

    private static void applyTheme() {
        scene.getStylesheets().clear();
        scene.getStylesheets().add(App.class.getResource(currentTheme).toExternalForm());
    }

    private static Parent loadFXML(String fxml) throws IOException {
        FXMLLoader fxmlLoader = new FXMLLoader(App.class.getResource(fxml + ".fxml"));
        return fxmlLoader.load();
    }

    public static void main(String[] args) {
        launch();
    }
}