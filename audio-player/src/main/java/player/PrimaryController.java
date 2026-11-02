package player;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javafx.animation.AnimationTimer;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.Slider;
import javafx.scene.layout.HBox;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.FileChooser;
import javafx.stage.Window;
import javafx.util.Duration;

/**
 * Main controller of the Audio Player.
 *
 * ============================================================
 *  COMPUTER GRAPHICS PARTS IN THIS CLASS
 * ============================================================
 *  1) Equalizer Bars  → visualizerBars + setScaleY()
 *     - Scaling transform on UI rectangles
 *
 *  2) Wave Effect     → waveLabel + setScaleX/Y()
 *     - Scaling based on average audio level
 *
 *  3) Circular Visualizer → Canvas + GraphicsContext
 *     - 2D coordinate system (centerX, centerY)
 *     - Circles (strokeOval / fillOval)
 *     - Polar → Cartesian: x = cx + r*cos(θ), y = cy + r*sin(θ)
 *     - Radial lines (strokeLine)
 *     - Color + opacity
 *     - Radius scaling by audio level
 *     - Real-time animation with AnimationTimer
 * ============================================================
 */
public class PrimaryController {

    // =========================================================
    // FXML — UI CONTROLS (not graphics drawing)
    // =========================================================

    @FXML private ListView<String> playlistView;
    @FXML private Label songNameLabel;
    @FXML private Label currentTimeLabel;
    @FXML private Label totalTimeLabel;
    @FXML private Slider progressSlider;
    @FXML private Slider volumeSlider;

    // ---------- GRAPHICS-RELATED FXML NODES ----------
    /** Equalizer: HBox of bar nodes; we scale their Y. */
    @FXML private HBox visualizerBars;

    /** Wave: Label scaled by audio level. */
    @FXML private Label waveLabel;

    /** Circular visualizer: JavaFX Canvas (2D drawing surface). */
    @FXML private Canvas circularVisualizer;

    // =========================================================
    // MUSIC DATA (media player logic — not graphics)
    // =========================================================

    private final List<File> songs = new ArrayList<>();
    private MediaPlayer mediaPlayer;
    private int currentSongIndex = -1;
    private boolean isSeeking = false;

    // =========================================================
    // ★ GRAPHICS STATE — Circular visualizer
    // =========================================================

    /** GraphicsContext = the "pen" used to draw on the Canvas. */
    private GraphicsContext circularGraphics;

    /**
     * Current audio energy level in range roughly 0..1.
     * Used to scale circle radius and radial bar lengths.
     */
    private double currentAudioLevel = 0.15;

    /**
     * AnimationTimer = frame-based animation loop.
     * handle() is called every frame (~60 FPS) to redraw the canvas.
     */
    private AnimationTimer circularAnimation;

    // =========================================================
    // INITIALIZE
    // =========================================================

    @FXML
    private void initialize() {

        // ★ GRAPHICS: set up Canvas drawing + start animation loop
        if (circularVisualizer != null) {
            circularGraphics = circularVisualizer.getGraphicsContext2D();
            drawCircularVisualizer(0.15);   // initial static frame
            startCircularAnimation();       // start real-time redraw
        }

        // Volume (media logic)
        if (volumeSlider != null) {
            volumeSlider.setValue(70);
            volumeSlider.valueProperty().addListener(
                    (obs, oldValue, newValue) -> {
                        if (mediaPlayer != null) {
                            mediaPlayer.setVolume(newValue.doubleValue() / 100.0);
                        }
                    }
            );
        }

        // Progress / seek (media logic)
        if (progressSlider != null) {
            progressSlider.setValue(0);
            progressSlider.valueChangingProperty().addListener(
                    (obs, wasChanging, isChanging) -> isSeeking = isChanging
            );
            progressSlider.setOnMousePressed(event -> isSeeking = true);
            progressSlider.setOnMouseReleased(event -> {
                if (mediaPlayer != null) {
                    Duration total = mediaPlayer.getTotalDuration();
                    if (total != null && !total.isUnknown() && total.toMillis() > 0) {
                        mediaPlayer.seek(total.multiply(progressSlider.getValue() / 100.0));
                    }
                }
                isSeeking = false;
            });
        }

        // Playlist selection (media logic)
        if (playlistView != null) {
            playlistView.getSelectionModel().selectedIndexProperty().addListener(
                    (obs, oldIndex, newIndex) -> {
                        int index = newIndex.intValue();
                        if (index >= 0 && index < songs.size()) {
                            loadSong(index);
                        }
                    }
            );
        }

        if (songNameLabel != null) songNameLabel.setText("No song selected");
        if (currentTimeLabel != null) currentTimeLabel.setText("00:00");
        if (totalTimeLabel != null) totalTimeLabel.setText("00:00");
    }

    // =========================================================
    // ADD / LOAD SONG (media logic)
    // =========================================================

    @FXML
    private void addSong(ActionEvent event) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Select Audio File");
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter(
                        "Audio Files", "*.mp3", "*.wav", "*.m4a", "*.aac"
                )
        );
        Window window = ((Node) event.getSource()).getScene().getWindow();
        List<File> selectedFiles = fileChooser.showOpenMultipleDialog(window);
        if (selectedFiles == null || selectedFiles.isEmpty()) return;

        for (File file : selectedFiles) {
            if (file == null) continue;
            songs.add(file);
            playlistView.getItems().add(file.getName());
        }
        if (currentSongIndex == -1 && !songs.isEmpty()) {
            playlistView.getSelectionModel().select(0);
        }
    }

    private void loadSong(int index) {
        if (index < 0 || index >= songs.size()) return;

        if (mediaPlayer != null) {
            try {
                mediaPlayer.stop();
                mediaPlayer.dispose();
            } catch (Exception ignored) {
            }
            mediaPlayer = null;
        }

        currentSongIndex = index;
        File file = songs.get(index);

        if (songNameLabel != null) songNameLabel.setText(file.getName());
        if (currentTimeLabel != null) currentTimeLabel.setText("00:00");
        if (totalTimeLabel != null) totalTimeLabel.setText("00:00");
        if (progressSlider != null) progressSlider.setValue(0);

        Media media;
        try {
            media = new Media(file.toURI().toString());
        } catch (Exception e) {
            System.err.println("Could not load media: " + e.getMessage());
            return;
        }

        mediaPlayer = new MediaPlayer(media);
        if (volumeSlider != null) {
            mediaPlayer.setVolume(volumeSlider.getValue() / 100.0);
        }

        // Audio spectrum → feeds GRAPHICS with real frequency magnitudes
        try {
            mediaPlayer.setAudioSpectrumNumBands(12);
            mediaPlayer.setAudioSpectrumInterval(0.05);
            mediaPlayer.setAudioSpectrumThreshold(-60);
        } catch (Exception e) {
            System.err.println("Audio spectrum setup failed: " + e.getMessage());
        }

        // =====================================================
        // ★ GRAPHICS: AudioSpectrumListener
        // Each callback gives frequency band magnitudes (dB).
        // We convert them to 0..1 levels and drive:
        //   - Equalizer bar scaling
        //   - Wave label scaling
        //   - Circular visualizer radius / bar length
        // =====================================================
        mediaPlayer.setAudioSpectrumListener(
                (timestamp, duration, magnitudes, phases) -> {
                    if (magnitudes == null || magnitudes.length == 0) return;

                    double totalLevel = 0;
                    int count = Math.min(
                            magnitudes.length,
                            visualizerBars.getChildren().size()
                    );

                    // ★ GRAPHICS 1 — Equalizer bars (Scaling transform)
                    // Each bar's height is scaled by its frequency band level.
                    for (int i = 0; i < count; i++) {
                        double db = magnitudes[i];
                        double level = (db + 60.0) / 60.0; // normalize dB → 0..1
                        level = Math.max(0.0, Math.min(1.0, level));

                        Node bar = visualizerBars.getChildren().get(i);
                        bar.setScaleY(0.25 + level * 1.8); // ★ Scaling
                        totalLevel += level;
                    }

                    double averageLevel = totalLevel / count;
                    currentAudioLevel = averageLevel;

                    // ★ GRAPHICS 2 — Wave effect (Scaling on X and Y)
                    if (waveLabel != null) {
                        waveLabel.setScaleY(0.70 + averageLevel * 1.5);
                        waveLabel.setScaleX(0.90 + averageLevel * 0.20);
                    }

                    // ★ GRAPHICS 3 — Circular visualizer driven by average level
                    drawCircularVisualizer(averageLevel);
                }
        );

        mediaPlayer.setOnReady(() -> {
            Duration totalDuration = mediaPlayer.getTotalDuration();
            if (totalDuration != null && !totalDuration.isUnknown()) {
                totalTimeLabel.setText(formatTime(totalDuration));
            }
            currentTimeLabel.setText("00:00");
            progressSlider.setValue(0);
            resetVisualizer();
        });

        mediaPlayer.currentTimeProperty().addListener(
                (obs, oldTime, newTime) -> {
                    if (newTime == null) return;
                    if (currentTimeLabel != null) {
                        currentTimeLabel.setText(formatTime(newTime));
                    }
                    if (!isSeeking && mediaPlayer != null) {
                        Duration total = mediaPlayer.getTotalDuration();
                        if (total != null && !total.isUnknown() && total.toMillis() > 0) {
                            progressSlider.setValue(
                                    newTime.toMillis() / total.toMillis() * 100.0
                            );
                        }
                    }
                }
        );

        mediaPlayer.setOnError(() -> {
            if (mediaPlayer != null && mediaPlayer.getError() != null) {
                System.err.println("Media error: " + mediaPlayer.getError().getMessage());
            }
        });

        mediaPlayer.setOnEndOfMedia(() -> {
            if (currentSongIndex < songs.size() - 1) {
                nextSong(null);
            } else {
                mediaPlayer.stop();
                progressSlider.setValue(100);
                resetVisualizer();
            }
        });
    }

    // =========================================================
    // PLAY / PAUSE / STOP / PREV / NEXT (media logic)
    // =========================================================

    @FXML
    private void playSong(ActionEvent event) {
        playCurrentSong();
    }

    private void playCurrentSong() {
        if (songs.isEmpty()) return;
        if (mediaPlayer == null) {
            if (currentSongIndex < 0) {
                currentSongIndex = 0;
                playlistView.getSelectionModel().select(0);
            }
            loadSong(currentSongIndex);
        }
        if (mediaPlayer != null) {
            mediaPlayer.play();
        }
    }

    @FXML
    private void pauseSong(ActionEvent event) {
        if (mediaPlayer != null) mediaPlayer.pause();
    }

    @FXML
    private void stopSong(ActionEvent event) {
        if (mediaPlayer != null) mediaPlayer.stop();
        if (progressSlider != null) progressSlider.setValue(0);
        if (currentTimeLabel != null) currentTimeLabel.setText("00:00");
        resetVisualizer();
    }

    @FXML
    private void previousSong(ActionEvent event) {
        if (songs.isEmpty()) return;
        if (currentSongIndex > 0) {
            currentSongIndex--;
            playlistView.getSelectionModel().select(currentSongIndex);
            loadSong(currentSongIndex);
            playCurrentSong();
        }
    }

    @FXML
    private void nextSong(ActionEvent event) {
        if (songs.isEmpty()) return;
        if (currentSongIndex < songs.size() - 1) {
            currentSongIndex++;
            playlistView.getSelectionModel().select(currentSongIndex);
            loadSong(currentSongIndex);
            playCurrentSong();
        }
    }

    // =========================================================
    // ★★★ MAIN COMPUTER GRAPHICS METHOD ★★★
    // Circular visualizer drawn on Canvas with GraphicsContext
    //
    // Concepts demonstrated:
    //  - 2D coordinate system (origin top-left of canvas)
    //  - Center point (centerX, centerY)
    //  - Circles: strokeOval / fillOval
    //  - Polar → Cartesian conversion:
    //        x = centerX + radius * cos(angle)
    //        y = centerY + radius * sin(angle)
    //  - Radial lines (strokeLine)
    //  - Scaling (radius grows with audio level)
    //  - Color and opacity (Color.rgb with alpha)
    // =========================================================

    private void drawCircularVisualizer(double level) {
        if (circularGraphics == null || circularVisualizer == null) return;

        double width = circularVisualizer.getWidth();
        double height = circularVisualizer.getHeight();
        if (width <= 0 || height <= 0) return;

        // 2D coordinate system — center of canvas
        double centerX = width / 2.0;
        double centerY = height / 2.0;

        // Clear previous frame
        circularGraphics.clearRect(0, 0, width, height);

        // Background fill
        circularGraphics.setFill(Color.rgb(11, 18, 32));
        circularGraphics.fillRect(0, 0, width, height);

        // Base radius + pulse scaled by audio level (Scaling)
        double baseRadius = 72;
        double pulse = level * 35;
        double radius = baseRadius + pulse;

        // Outer circle
        circularGraphics.setStroke(Color.rgb(37, 99, 235, 0.40)); // color + opacity
        circularGraphics.setLineWidth(3);
        circularGraphics.strokeOval(
                centerX - radius, centerY - radius, radius * 2, radius * 2
        );

        // Inner glow circle
        double innerRadius = radius - 10;
        circularGraphics.setStroke(Color.rgb(124, 255, 178, 0.80));
        circularGraphics.setLineWidth(2);
        circularGraphics.strokeOval(
                centerX - innerRadius, centerY - innerRadius,
                innerRadius * 2, innerRadius * 2
        );

        // Center filled circle (size also reacts to audio)
        double centerRadius = 34 + level * 14;
        circularGraphics.setFill(Color.rgb(37, 99, 235));
        circularGraphics.fillOval(
                centerX - centerRadius, centerY - centerRadius,
                centerRadius * 2, centerRadius * 2
        );
        circularGraphics.setStroke(Color.rgb(124, 255, 178));
        circularGraphics.setLineWidth(2);
        circularGraphics.strokeOval(
                centerX - centerRadius, centerY - centerRadius,
                centerRadius * 2, centerRadius * 2
        );

        // Center text
        circularGraphics.setFill(Color.WHITE);
        circularGraphics.setFont(Font.font("Arial", FontWeight.BOLD, 13));
        String text = "MUSIC";
        circularGraphics.fillText(text, centerX - (text.length() * 7) / 2.0, centerY + 5);

        // ---------- Radial bars (Polar → Cartesian) ----------
        int numberOfLines = 48;
        for (int i = 0; i < numberOfLines; i++) {
            // Angle around the circle
            double angle = (2 * Math.PI * i) / numberOfLines - Math.PI / 2;

            // Variation so bars are not all equal length
            double variation = 0.65 + 0.35 * Math.abs(Math.sin(i * 0.8));

            double innerLineRadius = radius + 7;
            double outerLineRadius = innerLineRadius + (level * 65 * variation);

            // Polar → Cartesian conversion
            double x1 = centerX + Math.cos(angle) * innerLineRadius;
            double y1 = centerY + Math.sin(angle) * innerLineRadius;
            double x2 = centerX + Math.cos(angle) * outerLineRadius;
            double y2 = centerY + Math.sin(angle) * outerLineRadius;

            if (i % 2 == 0) {
                circularGraphics.setStroke(Color.rgb(33, 150, 243));
            } else {
                circularGraphics.setStroke(Color.rgb(124, 255, 178));
            }
            circularGraphics.setLineWidth(2.5);
            circularGraphics.strokeLine(x1, y1, x2, y2);
        }
    }

    // =========================================================
    // ★ GRAPHICS — Frame-based animation loop
    // AnimationTimer calls handle() every display frame.
    // We redraw the circular visualizer using currentAudioLevel.
    // =========================================================

    private void startCircularAnimation() {
        if (circularAnimation != null) {
            circularAnimation.stop();
        }
        circularAnimation = new AnimationTimer() {
            @Override
            public void handle(long now) {
                if (circularVisualizer == null) return;
                drawCircularVisualizer(currentAudioLevel);
            }
        };
        circularAnimation.start();
    }

    // =========================================================
    // ★ GRAPHICS — Reset all visualizers to idle state
    // =========================================================

    private void resetVisualizer() {
        // Equalizer bars back to normal scale
        if (visualizerBars != null) {
            for (Node bar : visualizerBars.getChildren()) {
                bar.setScaleY(1.0);
            }
        }
        // Wave back to normal scale
        if (waveLabel != null) {
            waveLabel.setScaleX(1.0);
            waveLabel.setScaleY(1.0);
        }
        // Circular visualizer idle
        currentAudioLevel = 0.15;
        if (circularVisualizer != null) {
            drawCircularVisualizer(0.15);
        }
    }

    // =========================================================
    // UTIL / NAVIGATION (not graphics)
    // =========================================================

    private String formatTime(Duration duration) {
        if (duration == null || duration.isUnknown()) return "00:00";
        int totalSeconds = (int) Math.floor(duration.toSeconds());
        return String.format("%02d:%02d", totalSeconds / 60, totalSeconds % 60);
    }

    @FXML
    private void switchToSecondary(ActionEvent event) throws IOException {
        if (mediaPlayer != null) mediaPlayer.stop();
        if (circularAnimation != null) circularAnimation.stop();
        App.setRoot("secondary");
    }

    public void cleanup() {
        if (circularAnimation != null) circularAnimation.stop();
        if (mediaPlayer != null) {
            mediaPlayer.stop();
            mediaPlayer.dispose();
            mediaPlayer = null;
        }
    }
}