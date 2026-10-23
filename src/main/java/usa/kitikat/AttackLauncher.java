package usa.kitikat;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.scene.Group;
import javafx.scene.Scene;
import javafx.scene.transform.Scale;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import java.util.prefs.Preferences;

/**
 * Точка входа лаунчера.
 *
 * <p>Масштаб интерфейса (50% / 75% / 100%) реализован «оболочкой» из
 * {@link Group} + {@link Scale} на корневом {@link MainShell}:
 * окно меняет физический размер, а логический размер (layout) делится на scale.
 * Из-за этого <b>всё</b> масштабируется единообразно — шрифты бокового меню,
 * заголовки, карточки, панели, радиусы, отступы — ничего не «вылезает».</p>
 */
public final class AttackLauncher extends Application {

    /** Логический (базовый) размер окна при 100%. */
    private static final double BASE_WIDTH = 1060;
    private static final double BASE_HEIGHT = 700;

    /** Минимальный логический размер (свободное уменьшение окна). */
    private static final double MIN_WIDTH = 900;
    private static final double MIN_HEIGHT = 620;

    private final Preferences preferences = Preferences.userNodeForPackage(MainShell.class);

    private Stage stage;
    private Scene scene;
    private MainShell shell;
    private double scale = 1.0;

    private DiscordRPC rpc;

    @Override
    public void start(Stage stage) {
        this.stage = stage;

        scale = clampScale(preferences.getDouble("uiScale", 1.0));

        shell = new MainShell(
                stage,
                this::minimizeWindow,
                this::updateDiscord,
                this::setUiScale
        );
        // первый presence создаётся ещё внутри конструктора — докидываем настройки
        if (rpc != null) {
            shell.applyDiscordConfig(rpc);
        }

        Group root = new Group(shell);
        /*
         * ВАЖНО: иначе Group.autosizeChildren на каждом layout вызывает
         * child.autosize() и перетирает наш resize() pref-размером оболочки.
         */
        root.setAutoSizeChildren(false);

        scene = new Scene(root, BASE_WIDTH * scale, BASE_HEIGHT * scale);
        scene.setFill(javafx.scene.paint.Color.TRANSPARENT);

        var css = getClass().getResource("/styles.css");
        if (css != null) {
            scene.getStylesheets().add(css.toExternalForm());
        }

        // Любой ресайз окна → пересчитываем логический размер интерфейса.
        ChangeListener<Number> relayout = (obs, o, n) -> applyLayout();
        scene.widthProperty().addListener(relayout);
        scene.heightProperty().addListener(relayout);

        stage.initStyle(StageStyle.TRANSPARENT);
        stage.setTitle("Attack Launcher");
        applyWindowIcon();

        stage.setMinWidth(MIN_WIDTH * scale);
        stage.setMinHeight(MIN_HEIGHT * scale);
        stage.setScene(scene);

        applyLayout();
        stage.show();
    }

    /**
     * Применить текущий scale: transform на оболочке + логический resize.
     * Физические размеры берём из окна, поэтому пережимаем только layout.
     */
    private void applyLayout() {
        if (shell == null || scene == null) {
            return;
        }
        double logicalW = scene.getWidth() / scale;
        double logicalH = scene.getHeight() / scale;

        shell.getTransforms().setAll(new Scale(scale, scale, 0, 0));
        shell.resize(logicalW, logicalH);
    }

    /**
     * Смена масштаба из настроек: окно пересчитывается так, чтобы
     * логический размер интерфейса остался прежним.
     */
    private void setUiScale(double requested) {
        double logicalW = scene.getWidth() / scale;
        double logicalH = scene.getHeight() / scale;

        scale = clampScale(requested);
        preferences.putDouble("uiScale", scale);

        stage.setMinWidth(MIN_WIDTH * scale);
        stage.setMinHeight(MIN_HEIGHT * scale);
        stage.setWidth(logicalW * scale);
        stage.setHeight(logicalH * scale);

        applyLayout();
    }

    private static double clampScale(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return 1.0;
        }
        return Math.min(1.0, Math.max(0.5, value));
    }

    /**
     * Свернуть в панель задач.
     *
     * <p>Окно не «теряется»: кнопка с иконкой остаётся в панели задач,
     * по ней лаунчер всегда можно вернуть. Раньше здесь был {@code stage.hide()},
     * из-за которого окно исчезало, а приложение завершалось
     * (implicit exit — окон больше нет).</p>
     */
    private void minimizeWindow() {
        Platform.runLater(() -> {
            if (stage != null) {
                stage.setIconified(true);
            }
        });
    }

    /**
     * Иконка окна: панель задач, Alt+Tab, диспетчер задач.
     *
     * <p>Без неё Windows показывала стандартную иконку javaw.exe.</p>
     */
    private void applyWindowIcon() {
        String[] candidates = {
                "/assets/nemesis-icon-bright.png",
                "/assets/nemesis.png"
        };
        for (String candidate : candidates) {
            try {
                var url = getClass().getResource(candidate);
                if (url == null) {
                    continue;
                }
                /*
                 * ВАЖНО: загрузка синхронная. При backgroundLoading картинка
                 * ещё не декодирована в момент установки — Windows тогда
                 * остаётся без иконки (показывает иконку javaw.exe).
                 */
                stage.getIcons().add(
                        new javafx.scene.image.Image(url.toExternalForm(), false)
                );
                return;
            } catch (Throwable ignored) {
            }
        }
    }

    /**
     * Rich Presence. details == null && state == null → выключить.
     *
     * <p>Строки «Играет: …» — это новая активность, для неё Discord
     * начинает новый таймер; всё остальное (просмотр страниц,
     * настройки) таймер не сбрасывает.</p>
     */
    private void updateDiscord(String details, String state) {
        try {
            if (details == null && state == null) {
                if (rpc != null) {
                    rpc.close();
                }
                return;
            }
            if (rpc == null) {
                rpc = new DiscordRPC();
                if (shell != null) {
                    shell.applyDiscordConfig(rpc);
                }
            }
            if (details != null && details.startsWith("Играет:")) {
                rpc.startActivity(details, state);
            } else {
                rpc.start(details, state);
            }
        } catch (Throwable ignored) {
        }
    }

    @Override
    public void stop() {
        try {
            if (shell != null) {
                shell.shutdown();
            }
        } catch (Throwable ignored) {
        }
        try {
            if (rpc != null) {
                rpc.close();
            }
        } catch (Throwable ignored) {
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
