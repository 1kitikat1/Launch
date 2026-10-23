package usa.kitikat;

import javafx.animation.AnimationTimer;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.ScrollPane;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/** Временный отладочный запуск: метрики раскладки + скриншоты. */
public final class DebugDump extends Application {

    private static final Path OUT = Path.of(System.getProperty("user.dir"), "debug-out");
    private static final long T0 = System.currentTimeMillis();
    private static final AtomicLong FRAMES = new AtomicLong();

    static {
        try {
            Files.createDirectories(OUT);
            Files.deleteIfExists(OUT.resolve("log.txt"));
        } catch (Exception ignored) {
        }
    }

    private static synchronized void log(String line) {
        String text = String.format("[%6d ms] %s", System.currentTimeMillis() - T0, line);
        try {
            Files.writeString(OUT.resolve("log.txt"), text + System.lineSeparator(),
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (Exception ignored) {
        }
        System.out.println(text);
    }

    private Stage stage;
    private MainShell shell;
    private Scene scene;
    private double scale = 1.0;
    private final java.util.prefs.Preferences preferences =
            java.util.prefs.Preferences.userNodeForPackage(MainShell.class);
    private double savedUiScale = 1.0;
    private String savedAccent = "blue";

    /** Зеркалит NemesisLauncher: Group + Scale + логический resize. */
    private void applyLayout() {
        double logicalW = scene.getWidth() / scale;
        double logicalH = scene.getHeight() / scale;
        shell.getTransforms().setAll(new javafx.scene.transform.Scale(scale, scale, 0, 0));
        shell.resize(logicalW, logicalH);
        log(String.format("applyLayout scale=%.2f logical=%.1fx%.1f", scale, logicalW, logicalH));
    }

    private void setScale(double newScale) {
        double logicalW = scene.getWidth() / scale;
        double logicalH = scene.getHeight() / scale;
        scale = newScale;
        stage.setMinWidth(900 * scale);
        stage.setMinHeight(620 * scale);
        stage.setWidth(logicalW * scale);
        stage.setHeight(logicalH * scale);
        applyLayout();
        log("setScale done -> stage=" + stage.getWidth() + "x" + stage.getHeight()
                + " scene=" + scene.getWidth() + "x" + scene.getHeight());
    }

    @Override
    public void start(Stage stage) {
        log("** DebugDump started, user.dir=" + System.getProperty("user.dir"));
        this.stage = stage;
        savedUiScale = preferences.getDouble("uiScale", 1.0);
        savedAccent = preferences.get("accent", "blue");
        log("saved uiScale = " + savedUiScale + ", accent = " + savedAccent);

        log("building MainShell...");
        shell = new MainShell(stage, () -> {
        }, (a, b) -> {
        }, this::applyScaleSafely);
        log("MainShell built");

        javafx.scene.Group root = new javafx.scene.Group(shell);
        root.setAutoSizeChildren(false);
        scene = new Scene(root, 1060, 700);
        scene.setFill(javafx.scene.paint.Color.TRANSPARENT);
        var css = getClass().getResource("/styles.css");
        if (css != null) scene.getStylesheets().add(css.toExternalForm());
        scene.widthProperty().addListener((o, a, b) -> applyLayout());
        scene.heightProperty().addListener((o, a, b) -> applyLayout());
        log("scene built");

        stage.initStyle(StageStyle.TRANSPARENT);
        stage.setMinWidth(900);
        stage.setMinHeight(620);
        stage.setScene(scene);
        log("showing stage...");
        applyLayout();
        stage.show();
        log("stage shown, size = " + stage.getWidth() + "x" + stage.getHeight());

        new AnimationTimer() {
            @Override public void handle(long now) {
                FRAMES.incrementAndGet();
            }
        }.start();
        log("animation timer started");

        // heartbeat отдельным потоком — видно, живёт ли процесс
        Thread heartbeat = new Thread(() -> {
            while (true) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    return;
                }
                log("tick frames=" + FRAMES.get()
                        + " stage=" + stage.getWidth() + "x" + stage.getHeight()
                        + " scene=" + scene.getWidth() + "x" + scene.getHeight());
            }
        }, "heartbeat");
        heartbeat.setDaemon(true);
        heartbeat.start();

        // цепочка шагов через обычный поток + Platform.runLater (без анимационного таймера)
        Thread driver = new Thread(() -> {
            sleep(2500);
            later("dump onboarding", () -> {
                dump("onboarding");
                shot("onboarding");
            });
            sleep(500);
            later("select onboarding option", () -> clickChoiceOption(
                    onboardingOption()));
            sleep(800);
            later("dump onboarding selected", () -> {
                dump("onboarding-selected");
                shot("onboarding-selected");
            });
            sleep(400);
            later("finish onboarding",
                    () -> clickByText(".primary-button", "Продолжить"));
            sleep(1000);
            later("dump after onboarding", () -> {
                dump("after-onboarding");
                shot("after-onboarding");
            });
            sleep(600);
            later("dump games 100%", () -> {
                dump("games-100");
                shot("games-100");
            });
            sleep(600);
            // ---- обновление: свой сервер, fetch, сравнение, скачивание ----
            log("update self-test starting");
            UpdateChecker.Info loaded;
            try {
                loaded = updateSelfTest();
            } catch (Throwable t) {
                log("update self-test FAILED: " + t);
                t.printStackTrace();
                loaded = new UpdateChecker.Info("9.9.9",
                        "http://127.0.0.1:1/none.zip",
                        "Тестовая версия 9.9.9 — проверка обновления.");
            }
            final UpdateChecker.Info testInfo = loaded;
            sleep(400);
            later("show update dialog", () ->
                    invoke1("showUpdateDialog", UpdateChecker.Info.class, testInfo));
            sleep(1500);
            later("shot update dialog", () -> {
                dump("update-dialog");
                shot("update-dialog");
            });
            sleep(500);
            later("close update dialog",
                    () -> clickByText(".secondary-button", "Позже"));
            sleep(700);
            later("expand sidebar", () -> invoke1("setSidebarExpanded", boolean.class, Boolean.TRUE));
            sleep(800);
            later("dump sidebar expanded", () -> {
                dump("sidebar-expanded");
                shot("sidebar-expanded");
            });
            sleep(600);
            later("collapse sidebar", () -> invoke1("setSidebarExpanded", boolean.class, Boolean.FALSE));
            sleep(1000);
            later("dump sidebar collapsed", () -> {
                dump("sidebar-collapsed");
                shot("sidebar-collapsed");
                invoke("showSettings");
            });
            sleep(1500);
            later("dump settings 100%", () -> {
                dump("settings-100");
                shot("settings-100");
                scrollTest();
            });
            sleep(700);
            later("shot window controls section", () -> {
                scrollToSelector(".choice-option");
                shot("settings-window-controls");
            });
            sleep(900);
            later("switch window controls to dots in settings", () ->
                    clickChoiceOption(0));
            sleep(900);
            later("shot window controls after switch", () -> {
                scrollToSelector(".choice-option");
                shot("settings-window-controls-dots");
                log("windowControls = "
                        + preferences.get("windowControls", "?"));
                javafx.scene.Node sc = shell.lookup(".page-scroll");
                if (sc instanceof javafx.scene.control.ScrollPane sp) {
                    sp.setVvalue(0);
                }
            });
            sleep(700);
            later("shot updates section", () -> {
                scrollToSelector(".updates-row");
                shot("settings-updates");
                log("current version = " + UpdateChecker.currentVersion());
            });
            sleep(700);
            later("personalization check", () -> checkPersonalization());
            sleep(4500);
            later("dump settings after accent switch", () -> {
                dump("settings-accent");
                shot("settings-accent");
            });
            sleep(800);
            later("resize to 900x620 (100%)", () -> {
                stage.setWidth(900);
                stage.setHeight(620);
            });
            sleep(200);
            later("screen immediately after resize", () -> screenShot("after-resize-quick"));
            sleep(1300);
            later("dump settings 900x620", () -> {
                dump("settings-900x620");
                shot("settings-900x620");
            });
            sleep(800);
            later("click 75%", () -> clickScaleButton("75%"));
            sleep(1600);
            later("dump settings 75%", () -> {
                dump("settings-75");
                shot("settings-75");
            });
            sleep(800);
            later("click 50%", () -> clickScaleButton("50%"));
            sleep(1600);
            later("dump settings 50%", () -> {
                dump("settings-50");
                shot("settings-50");
            });
            sleep(800);
            later("click 100% back", () -> clickScaleButton("100%"));
            sleep(1600);
            later("dump settings 100% back", () -> {
                dump("settings-100-back");
                shot("settings-100-back");
                invoke("showGames");
            });
            sleep(1400);
            later("open minecraft clients", () -> invoke("showMinecraftClients"));
            sleep(1200);
            later("dump minecraft clients", () -> {
                dump("minecraft-clients");
                shot("minecraft-clients");
            });
            sleep(400);
            later("open delta details", () -> openSecondClientCard());
            sleep(1200);
            later("dump delta details", () -> {
                dump("delta-details");
                shot("delta-details");
            });
            sleep(600);
            later("dump games + tall page", () -> {
                dump("games-final");
                shot("games-final");
                forceTallPage();
            });
            sleep(1500);
            later("dump tall + scroll test", () -> {
                dump("tall");
                shot("tall");
                scrollTest();
                preferences.putDouble("uiScale", savedUiScale);
                preferences.put("accent", savedAccent);
                invoke("applyAccentTheme");
                log("restored uiScale = " + savedUiScale + ", accent = " + savedAccent);
                log("done — exiting");
                Platform.exit();
            });
        }, "driver");
        driver.setDaemon(true);
        driver.start();
        log("driver started");
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ignored) {
        }
    }

    /**
     * Самодиагностика обновления: поднимаем локальный HTTP-сервер с
     * version.json и архивом, проверяем fetch, сравнение версий,
     * скачивание с прогрессом и генерацию скрипта подмены файлов.
     */
    private static UpdateChecker.Info updateSelfTest() throws Exception {
        com.sun.net.httpserver.HttpServer server =
                com.sun.net.httpserver.HttpServer.create(
                        new java.net.InetSocketAddress("127.0.0.1", 0), 0);
        int port = server.getAddress().getPort();
        byte[] archive = new byte[2 * 1024 * 1024];
        new java.util.Random(7).nextBytes(archive);
        String json = "{\"version\":\"9.9.9\","
                + "\"url\":\"http://127.0.0.1:" + port
                + "/NemesisLauncher-9.9.9.zip\","
                + "\"notes\":\"Тестовая версия 9.9.9 — проверка обновления.\"}";

        server.createContext("/version.json", exchange -> {
            byte[] body = json.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            try (var out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        server.createContext("/NemesisLauncher-9.9.9.zip", exchange -> {
            exchange.sendResponseHeaders(200, archive.length);
            try (var out = exchange.getResponseBody()) {
                out.write(archive);
            }
        });
        server.start();
        log("update test server on 127.0.0.1:" + port);

        try {
            UpdateChecker.Info info = UpdateChecker.fetch(
                    "http://127.0.0.1:" + port + "/version.json");
            log("update fetch -> version=" + info.version()
                    + " url=" + info.url()
                    + " notes=" + info.notes());

            String current = UpdateChecker.currentVersion();
            log("update current=" + current
                    + " isNewer=" + UpdateChecker.isNewer(current, info.version())
                    + " | 1.0.0<1.1.0=" + UpdateChecker.isNewer("1.0.0", "1.1.0")
                    + " 1.1.0<1.1.0=" + UpdateChecker.isNewer("1.1.0", "1.1.0")
                    + " 2.0.0<1.9.9=" + UpdateChecker.isNewer("2.0.0", "1.9.9")
                    + " dev<1.1.0=" + UpdateChecker.isNewer("dev", "1.1.0"));

            Path zip = Files.createTempFile("nemesis-update-test-", ".zip");
            AtomicLong maxProgress = new AtomicLong(-1);
            UpdateChecker.download(info.url(), zip,
                    value -> {
                        if (value >= 0) {
                            maxProgress.accumulateAndGet(
                                    (long) (value * 100), Math::max);
                        }
                    },
                    text -> log("update status: " + text));
            log("update download -> " + Files.size(zip)
                    + " байт, прогресс дорос до " + maxProgress.get() + "%");

            Path appDir = Path.of(System.getProperty("user.dir"),
                    "build", "jpackage", "NemesisLauncher");
            String script = UpdateChecker.scriptBody(appDir, zip,
                    Path.of(System.getProperty("user.dir"),
                            "build", "jpackage", "restart.flag"));
            log("---- update script (" + script.split("\n").length + " строк) ----");
            for (String line : script.split("\n")) {
                log("  " + line);
            }
            Files.writeString(OUT.resolve("update-script.ps1"), script,
                    java.nio.charset.StandardCharsets.UTF_8);
            Files.deleteIfExists(zip);
            return info;
        } finally {
            server.stop(0);
        }
    }

    /** Callback, который MainShell зовёт из кнопок настроек. */
    private void applyScaleSafely(double requested) {
        setScale(requested);
    }

    private interface Action {
        void run() throws Exception;
    }

    private void later(String what, Action action) {
        log("queue: " + what);
        Platform.runLater(() -> {
            try {
                action.run();
            } catch (Throwable t) {
                log("ERROR in " + what + ": " + t);
                t.printStackTrace();
            }
        });
    }

    private void invoke(String methodName) {
        try {
            Method m = MainShell.class.getDeclaredMethod(methodName);
            m.setAccessible(true);
            m.invoke(shell);
        } catch (Exception ex) {
            log("invoke failed: " + ex);
        }
    }

    private void invoke1(String methodName, Class<?> type, Object arg) {
        try {
            Method m = MainShell.class.getDeclaredMethod(methodName, type);
            m.setAccessible(true);
            m.invoke(shell, arg);
        } catch (Exception ex) {
            log("invoke failed: " + ex);
        }
    }

    /** Создаёт искусственно высокую страницу, чтобы проверить скролл. */
    private void forceTallPage() {
        javafx.scene.control.Label filler = new javafx.scene.control.Label();
        filler.setPrefHeight(2400);
        filler.getStyleClass().add("muted");
        filler.setText("TALL PAGE FOR SCROLL TEST (2400px)");
        StackPane pageLayer = (StackPane) findScroll().getContent();
        javafx.scene.layout.VBox page = new javafx.scene.layout.VBox(12);
        page.setPadding(new javafx.geometry.Insets(24, 28, 28, 28));
        page.setMaxWidth(Double.MAX_VALUE);
        page.getChildren().addAll(
                new javafx.scene.control.Label("SCROLL TEST"),
                filler,
                new javafx.scene.control.Label("BOTTOM")
        );
        pageLayer.getChildren().setAll(page);
        pageLayer.requestLayout();
    }

    private void scrollTest() {
        ScrollPane sp = findScroll();
        log("---- SCROLL TEST ----");
        log("  vvalue before = " + sp.getVvalue());
        Node vp = sp.lookup(".viewport");
        logViewportChildren("  before", vp);
        sp.setVvalue(1.0);
        log("  vvalue after = " + sp.getVvalue());
        logViewportChildren("  after ", vp);
        sp.setVvalue(0);
    }

    private void logViewportChildren(String tag, Node vp) {
        if (!(vp instanceof javafx.scene.Parent p)) {
            log(tag + " viewport not a Parent");
            return;
        }
        for (Node c : p.getChildrenUnmodifiable()) {
            log(tag + " child " + c.getClass().getSimpleName()
                    + " layoutY=" + c.getLayoutY()
                    + " bounds=" + fmt(c.getBoundsInLocal())
                    + " scene=" + fmt(c.localToScene(c.getBoundsInLocal())));
        }
    }

    /** Прокручивает страницу так, чтобы элемент селектора был виден сверху. */
    private void scrollToSelector(String selector) {
        Node target = shell.lookup(selector);
        ScrollPane sp = findScroll();
        if (target == null) {
            log("scroll: target not found " + selector);
            return;
        }
        double viewport = sp.getHeight();
        double content = sp.getContent() == null
                ? 0 : sp.getContent().getBoundsInLocal().getHeight();
        double max = Math.max(1, content - viewport);
        double already = sp.getVvalue() * max;
        double targetY = target.localToScene(target.getBoundsInLocal()).getMinY();
        double scrollY = sp.localToScene(sp.getBoundsInLocal()).getMinY();
        double desired = already + (targetY - scrollY) - 24;
        double v = Math.max(0, Math.min(1, desired / max));
        sp.setVvalue(v);
        log("scroll " + selector + ": v="
                + String.format(java.util.Locale.ROOT, "%.3f", v)
                + " content=" + Math.round(content)
                + " viewport=" + Math.round(viewport));
    }

    /** Жмём кнопку масштаба в настройках (как пользователь). */
    private void clickScaleButton(String label) {
        for (javafx.scene.Node n : shell.lookupAll(".button")) {
            if (n instanceof javafx.scene.control.Button b && label.equals(b.getText())) {
                log("clicking scale button: " + label);
                b.fire();
                return;
            }
        }
        log("scale button not found: " + label);
    }

    /**
     * Какой вариант первоначальной настройки выбрать в прогоне:
     * 0 — точки, 1 — обычные кнопки (по умолчанию 1; поменять можно
     * файлом debug-out/onboarding-option.txt).
     */
    private int onboardingOption() {
        try {
            return Integer.parseInt(java.nio.file.Files
                    .readString(java.nio.file.Path.of(
                            "debug-out", "onboarding-option.txt"))
                    .trim());
        } catch (Exception ignored) {
            return 1;
        }
    }

    private void clickByText(String selector, String text) {
        for (javafx.scene.Node n : shell.lookupAll(selector)) {
            if (n instanceof javafx.scene.control.Button b && text.equals(b.getText())) {
                log("clicking " + selector + ": " + text);
                b.fire();
                return;
            }
        }
        log("not found " + selector + ": " + text);
    }

    /** Клик по варианту стиля кнопок окна: 0 — точки, 1 — обычные. */
    private void clickChoiceOption(int index) {
        List<javafx.scene.Node> options =
                new ArrayList<>(shell.lookupAll(".choice-option"));
        options.sort(java.util.Comparator.comparingDouble(
                n -> n.localToScene(n.getBoundsInLocal()).getMinX()));
        log("onboarding options found: " + options.size());
        if (index < 0 || index >= options.size()) {
            log("option not found: " + index);
            return;
        }
        javafx.scene.Node n = options.get(index);
        n.fireEvent(new javafx.scene.input.MouseEvent(
                javafx.scene.input.MouseEvent.MOUSE_CLICKED,
                0, 0, 0, 0,
                javafx.scene.input.MouseButton.PRIMARY, 1,
                false, false, false, false,           // shift, ctrl, alt, meta
                true, false, false, false, false, false,   // кнопки мыши, stillSincePress
                new javafx.scene.input.PickResult(n, 0, 0)));
        log("clicked onboarding option " + index);
    }

    /** Открывает страницу установки второй карточки — это Delta. */
    private void openSecondClientCard() {
        List<javafx.scene.Node> cards = new ArrayList<>(shell.lookupAll(".client-card"));
        cards.sort(java.util.Comparator.comparingDouble(
                n -> n.localToScene(n.getBoundsInLocal()).getMinX()));
        log("client cards found: " + cards.size());
        for (javafx.scene.Node c : cards) {
            log("  card scene=" + fmt(c.localToScene(c.getBoundsInLocal()))
                    + " pref=" + ((javafx.scene.layout.Region) c).getPrefWidth()
                    + " actual=" + ((javafx.scene.layout.Region) c).getWidth());
        }
        if (cards.size() < 2) {
            log("second card not found");
            return;
        }
        for (javafx.scene.Node b : cards.get(1).lookupAll(".card-button")) {
            if (b instanceof javafx.scene.control.Button btn) {
                log("firing card button: " + btn.getText());
                btn.fire();
                return;
            }
        }
        log("card button not found");
    }

    private ScrollPane findScroll() {
        Node n = shell.lookup(".page-scroll");
        if (n instanceof ScrollPane sp) return sp;
        throw new IllegalStateException("page-scroll not found");
    }

    private String boundsOf(Node n) {
        if (n == null) return "null";
        Bounds b = n.getBoundsInLocal();
        Bounds s = n.localToScene(b);
        return String.format("local=%s scene=%s", fmt(b), fmt(s));
    }

    private String fmt(Bounds b) {
        if (b == null) return "null";
        return String.format("[%.1f,%.1f %.1fx%.1f]", b.getMinX(), b.getMinY(), b.getWidth(), b.getHeight());
    }

    private void dump(String tag) {
        log("================ " + tag + " ================");
        log("scene = " + scene.getWidth() + " x " + scene.getHeight()
                + "   stage = " + stage.getWidth() + " x " + stage.getHeight());
        log("shell = " + fmt(shell.getBoundsInLocal())
                + "  shellScene = " + fmt(shell.localToScene(shell.getBoundsInLocal()))
                + "  scale=" + scale + "  frames=" + FRAMES.get());

        Node contentNode = shell.getCenter();
        log("center(content) = " + boundsOf(contentNode));
        log("SIZES: shell min=" + shell.getMinWidth() + "x" + shell.getMinHeight()
                + " pref=" + shell.prefWidth(-1) + "x" + shell.prefHeight(-1));
        if (contentNode instanceof Region cr) {
            log("       content min=" + cr.getMinWidth() + "x" + cr.getMinHeight()
                    + " pref=" + cr.prefWidth(-1) + "x" + cr.prefHeight(-1)
                    + " childrenManaged=" + ((StackPane) cr).getChildren().size());
            for (Node c : ((StackPane) cr).getChildren()) {
                String extra = "";
                if (c instanceof Region r2) {
                    extra = " min=" + r2.getMinWidth() + "x" + r2.getMinHeight()
                            + " pref=" + r2.prefWidth(-1) + "x" + r2.prefHeight(-1);
                }
                log("          child " + c.getClass().getSimpleName() + extra
                        + " managed=" + c.isManaged() + " local=" + fmt(c.getBoundsInLocal()));
            }
        }
        Node left = shell.getLeft();
        if (left instanceof Region lr) {
            log("       sidebar min=" + lr.getMinWidth() + "x" + lr.getMinHeight()
                    + " pref=" + lr.prefWidth(-1) + "x" + lr.prefHeight(-1));
        }
        logSidebar();
        Node top = shell.getTop();
        if (top instanceof Region tr) {
            log("       titlebar min=" + tr.getMinWidth() + "x" + tr.getMinHeight()
                    + " pref=" + tr.prefWidth(-1) + "x" + tr.prefHeight(-1));
        }

        ScrollPane sp = findScroll();
        log("pageScroll = " + boundsOf(sp));
        log("viewport   = " + boundsOf(sp.lookup(".viewport")));
        Node vbarNode = sp.lookup(".scroll-bar:vertical");
        boolean vbarVisible = vbarNode instanceof javafx.scene.control.ScrollBar sb2 && sb2.isVisible();
        log("vbar       = " + boundsOf(vbarNode) + " visible=" + vbarVisible);
        log("hbar       = " + boundsOf(sp.lookup(".scroll-bar:horizontal")));

        Node content = sp.getContent();
        log("content(pageLayer) = " + boundsOf(content));
        if (content instanceof Region r) {
            log("  pref   = " + r.prefWidth(-1) + " x " + r.prefHeight(-1));
            log("  pref@viewportW = " + r.prefHeight(sp.getWidth()));
            log("  min    = " + r.minWidth(-1) + " x " + r.minHeight(-1));
            log("  actual = " + r.getWidth() + " x " + r.getHeight());
            log("  contentBias = " + r.getContentBias());
        }
        if (content instanceof StackPane pane && !pane.getChildren().isEmpty()) {
            Node page = pane.getChildren().get(0);
            log("  child(page) = " + boundsOf(page));
            if (page instanceof Region r) {
                log("    pref    = " + r.prefWidth(-1) + " x " + r.prefHeight(-1));
                log("    pref@w  = " + r.prefHeight(r.getWidth()));
                log("    actual  = " + r.getWidth() + " x " + r.getHeight());
                log("    bias    = " + r.getContentBias());
                if (page instanceof javafx.scene.layout.VBox vb) {
                    double sum = 0;
                    for (Node c : vb.getChildren()) {
                        if (c instanceof Region cr) {
                            sum += cr.prefHeight(cr.getWidth() > 0 ? cr.getWidth() : -1);
                            log("      child " + c.getClass().getSimpleName()
                                    + (c.getStyleClass().isEmpty() ? "" : "." + c.getStyleClass())
                                    + " local=" + fmt(c.getBoundsInLocal())
                                    + " prefH=" + cr.prefHeight(-1));
                        }
                    }
                    log("    sum(children prefHeight) = " + sum);
                }
            }
        }
        log("vvalue = " + sp.getVvalue() + "  hvalue = " + sp.getHvalue());

        List<String> outside = new ArrayList<>();
        collectOutside(shell, new javafx.geometry.BoundingBox(0, 0, scene.getWidth(), scene.getHeight()),
                outside, 0);
        log("OUTSIDE WINDOW (" + outside.size() + "):");
        outside.forEach(s -> log("   " + s));

        List<String> outsideContent = new ArrayList<>();
        Bounds cb = contentNode.localToScene(contentNode.getBoundsInLocal());
        collectOutside(contentNode, cb, outsideContent, 0);
        log("OUTSIDE CONTENT AREA (" + outsideContent.size() + "):");
        outsideContent.forEach(s -> log("   " + s));
        log("");
    }

    private void collectOutside(Node node, Bounds box, List<String> out, int depth) {
        if (depth > 40) return;
        if (node instanceof Region r && !r.isVisible()) return;
        Bounds s;
        try {
            s = node.localToScene(node.getBoundsInLocal());
        } catch (Exception ex) {
            return;
        }
        if (s != null && !s.isEmpty()) {
            boolean bad = s.getMinX() < box.getMinX() - 0.5 || s.getMaxX() > box.getMaxX() + 0.5
                    || s.getMinY() < box.getMinY() - 0.5 || s.getMaxY() > box.getMaxY() + 0.5;
            if (bad) {
                out.add(" ".repeat(Math.min(depth, 20)) + node.getClass().getSimpleName()
                        + (node.getStyleClass().isEmpty() ? "" : "." + node.getStyleClass())
                        + " scene=" + fmt(s) + (node instanceof javafx.scene.control.Label l
                        && l.getText() != null ? " text=\"" + trim(l.getText()) + "\"" : ""));
            }
        }
        if (node instanceof javafx.scene.Parent p) {
            for (Node c : p.getChildrenUnmodifiable()) collectOutside(c, box, out, depth + 1);
        }
    }

    private String trim(String t) {
        t = t.replace('\n', ' ');
        return t.length() > 40 ? t.substring(0, 40) + "…" : t;
    }

    private void logSidebar() {
        Node sb = shell.lookup(".sidebar");
        if (!(sb instanceof Region r)) {
            log("sidebar: not found");
            return;
        }
        StringBuilder labels = new StringBuilder();
        for (javafx.scene.Node n : shell.lookupAll(".nav-label")) {
            if (n instanceof javafx.scene.control.Label l) {
                labels.append('"').append(l.getText()).append("\" vis=").append(l.isVisible())
                        .append(" man=").append(l.isManaged())
                        .append(" op=").append(String.format("%.2f", l.getOpacity())).append("; ");
            }
        }
        log("       sidebar local=" + fmt(r.getBoundsInLocal())
                + " scene=" + fmt(r.localToScene(r.getBoundsInLocal()))
                + " | labels: " + labels);
    }

    /** Проверка персонализации: прозрачность окна, акцент, живой фон. */
    private void checkPersonalization() {
        log("---- PERSONALIZATION ----");
        log("  stage opacity = " + stage.getOpacity()
                + " (pref " + preferences.getDouble("windowOpacity", 0.90) + ")");
        log("  accent pref   = " + preferences.get("accent", "?"));
        try {
            Path file = AccentTheme.cssFile();
            log("  accent css    = " + file + " exists=" + Files.exists(file)
                    + " size=" + (Files.exists(file) ? Files.size(file) : -1));
        } catch (Exception ex) {
            log("  accent css error: " + ex);
        }
        log("  stylesheets   = " + scene.getStylesheets());
        log("  swatches      = " + shell.lookupAll(".accent-swatch").size());
        log("  running game  = " + reflectionField("runningGame"));
        for (javafx.scene.Node n : shell.lookupAll(".slider")) {
            if (n instanceof javafx.scene.control.Slider s) {
                log("  slider min=" + s.getMin() + " max=" + s.getMax()
                        + " value=" + s.getValue());
            }
        }
        for (javafx.scene.Node n : shell.lookupAll(".settings-section")) {
            if (n instanceof javafx.scene.layout.Region r) {
                r.applyCss();
                var fills = r.getBackground().getFills();
                log("  section bg = " + (fills.isEmpty() ? "none" : String.valueOf(fills.get(0).getFill())));
                break;
            }
        }
        log("  titlebar style= \"" + (shell.getTop() != null ? shell.getTop().getStyle() : "n/a") + "\"");

        double first = wallpaperScale();
        javafx.animation.PauseTransition wait =
                new javafx.animation.PauseTransition(javafx.util.Duration.millis(3000));
        wait.setOnFinished(e -> {
            double second = wallpaperScale();
            log("  wallpaper scale " + String.format("%.4f", first)
                    + " -> " + String.format("%.4f", second)
                    + "  live=" + (Math.abs(second - first) > 0.0005));
            clickSwatch(2);
        });
        wait.play();
    }

    private double wallpaperScale() {
        try {
            java.lang.reflect.Field f = MainShell.class.getDeclaredField("wallpaperView");
            f.setAccessible(true);
            return ((javafx.scene.image.ImageView) f.get(shell)).getScaleX();
        } catch (Exception ex) {
            log("  wallpaperScale failed: " + ex);
            return Double.NaN;
        }
    }

    private Object reflectionField(String name) {
        try {
            java.lang.reflect.Field f = MainShell.class.getDeclaredField(name);
            f.setAccessible(true);
            return f.get(shell);
        } catch (Exception ex) {
            return "error: " + ex;
        }
    }

    /** Кликаем кружок акцентного цвета (как пользователь). */
    private void clickSwatch(int index) {
        List<javafx.scene.Node> swatches = new ArrayList<>(shell.lookupAll(".accent-swatch"));
        swatches.sort(java.util.Comparator.comparingDouble(
                n -> n.localToScene(n.getBoundsInLocal()).getMinX()));
        if (index >= swatches.size()) {
            log("  swatch #" + index + " not found (" + swatches.size() + ")");
            return;
        }
        javafx.scene.Node swatch = swatches.get(index);
        log("  clicking swatch #" + index + " local=" + fmt(swatch.getBoundsInLocal()));
        javafx.event.Event.fireEvent(swatch, new javafx.scene.input.MouseEvent(
                javafx.scene.input.MouseEvent.MOUSE_CLICKED,
                0, 0, 0, 0,
                javafx.scene.input.MouseButton.PRIMARY, 1,
                false, false, false, false,
                true, false, false, false, false,
                false,
                new javafx.scene.input.PickResult(swatch, 0, 0)
        ));
        log("  accent pref after click = " + preferences.get("accent", "?"));
        // что реально перерисовалось в сцене (снапшот сцены может отставать)
        for (javafx.scene.Node n : shell.lookupAll(".setting-label")) {
            if (n instanceof javafx.scene.control.Label l) {
                log("  setting-label: " + l.getText());
            }
        }
        for (javafx.scene.Node n : shell.lookupAll(".primary-button")) {
            if (n instanceof javafx.scene.control.Button b) {
                b.applyCss();
                var fills = b.getBackground().getFills();
                String fill = fills.isEmpty() ? "none" : String.valueOf(fills.get(0).getFill());
                log("  primary-button \"" + b.getText() + "\" bg=" + fill);
            }
        }
        try {
            List<String> lines = Files.readAllLines(AccentTheme.cssFile());
            log("  accent.css first line: " + (lines.isEmpty() ? "-" : lines.get(0)));
        } catch (Exception ex) {
            log("  accent.css read failed: " + ex);
        }

        // CSS подключается следующим кадром — проверяем цвет кнопки позже
        javafx.animation.PauseTransition recheck =
                new javafx.animation.PauseTransition(javafx.util.Duration.millis(800));
        recheck.setOnFinished(e -> {
            for (javafx.scene.Node n : shell.lookupAll(".primary-button")) {
                if (n instanceof javafx.scene.control.Button b) {
                    b.applyCss();
                    var fills = b.getBackground().getFills();
                    log("  (через 800мс) primary-button bg="
                            + (fills.isEmpty() ? "none" : String.valueOf(fills.get(0).getFill())));
                }
            }
            log("  stylesheets now = " + scene.getStylesheets());
        });
        recheck.play();
    }

    private void shot(String tag) {
        try {
            long f = FRAMES.get();
            scene.snapshot(null);          // 1-й снапшот может отставать на кадр
            WritableImage img = scene.snapshot(null);
            int w = (int) img.getWidth(), h = (int) img.getHeight();
            BufferedImage bi = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    bi.setRGB(x, y, img.getPixelReader().getArgb(x, y));
                }
            }
            File fl = OUT.resolve(tag + ".png").toFile();
            ImageIO.write(bi, "png", fl);
            log("[shot] " + fl.getName() + " " + w + "x" + h + " framesBefore=" + f);
        } catch (Exception ex) {
            log("[shot] FAILED " + ex);
        }
        screenShot(tag + "-screen");
    }

    /** Скриншот реального экрана вокруг окна — видно белые артефакты. */
    private void screenShot(String tag) {
        try {
            int pad = 300;
            int x = (int) stage.getX() - pad;
            int y = (int) stage.getY() - pad;
            int w = (int) stage.getWidth() + pad * 2;
            int h = (int) stage.getHeight() + pad * 2;
            java.awt.Rectangle r = new java.awt.Rectangle(x, y, w, h);
            BufferedImage cap = new java.awt.Robot().createScreenCapture(r);
            File fl = OUT.resolve(tag + ".png").toFile();
            ImageIO.write(cap, "png", fl);
            log("[screen] " + fl.getName() + " " + w + "x" + h
                    + " stage=[" + (int) stage.getX() + "," + (int) stage.getY()
                    + " " + (int) stage.getWidth() + "x" + (int) stage.getHeight() + "]");
        } catch (Throwable ex) {
            log("[screen] FAILED " + ex);
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
