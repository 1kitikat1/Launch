package usa.kitikat;

import javafx.animation.Animation;
import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.ParallelTransition;
import javafx.animation.Timeline;
import javafx.animation.TranslateTransition;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.*;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.Duration;

import usa.kitikat.account.AuthService;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.prefs.Preferences;

public final class MainShell extends BorderPane {
    private static final String NEMA_REPOSITORY = "https://github.com/1kitikat1/Nema";
    private static final String DELTA_REPOSITORY = "https://github.com/1kitikat1/Delta";
    private static final String SYSTEMDLC_REPOSITORY = "https://github.com/1kitikat1/SystemDLC";
    private static final String ROCKSTAR_REPOSITORY = "https://github.com/1kitikat1/Rockstar";
    private static final String DIMASIK26_REPOSITORY = "https://github.com/1kitikat1/Dimasik26";
    /** Клиент Attack DLC: в репозитории только моды, инстанс общий с Dimasik26. */
    private static final String ATTACKDLC_REPOSITORY = "https://github.com/1kitikat1/AttackDLC";

    private final Stage stage;
    private final Runnable minimizeWindow;
    private final java.util.function.BiConsumer<String, String> discordUpdateCallback;
    private final java.util.function.DoubleConsumer scaleApplier;
    private final StackPane content = new StackPane();
    private final StackPane pageLayer = new StackPane();
    /** Открытый (если открыт) диалог обновления — чтобы не показать дважды. */
    private StackPane updateOverlay;

    private StackPane errorOverlay;
    /** Открытый диалог обновления клиентов. */
    private StackPane clientUpdateOverlay;
    /** Найденные сверкой mods/ обновления: имя клиента → что докачать/удалить. */
    private final java.util.Map<String, GitHubClient.ModsDiff> clientUpdates =
            new java.util.concurrent.ConcurrentHashMap<>();
    /**
     * Метки «Есть обновление» на карточках. Хранятся в настройках, чтобы
     * метка была видна сразу при запуске, пока идёт сверка с репозиторием.
     */
    private final java.util.Set<String> updateMarkers =
            java.util.concurrent.ConcurrentHashMap.newKeySet();
    /** Когда в последний раз проверяли клиенты (чтобы не долбить GitHub API). */
    private long lastClientCheck;
    /** Сетка карточек клиентов — перерисовывается, когда приходит результат сверки. */
    private FlowPane clientsGrid;
    private final ScrollPane pageScroll = new ScrollPane();
    private final ImageView wallpaperView = new ImageView();
    private final Rectangle wallpaperShade = new Rectangle();
    private final Preferences preferences = Preferences.userNodeForPackage(MainShell.class);
    private boolean discordRpcEnabled = true;
    private final MinecraftLauncher minecraftLauncher = new MinecraftLauncher();
    private final GitHubClient githubClient = new GitHubClient();

    private final String username = System.getProperty("user.name", "Player");
    private double dragX;
    private double dragY;
    /** Шапка окна — пересобирается при смене стиля кнопок окна. */
    private HBox titlebar;

    /* Сворачиваемая боковая панель: по умолчанию только иконки,
       при наведении — иконки + текст. */
    private static final double SIDEBAR_EXPANDED = 174;
    private static final double SIDEBAR_COLLAPSED = 62;
    private VBox sidebarNav;
    private final java.util.List<Animation> sidebarAnims = new java.util.ArrayList<>();
    private final java.util.List<Label> navLabels = new java.util.ArrayList<>();

    /* ================= иконки бокового меню ================= */
    private final java.util.List<NavIconSlot> navIconSlots =
            new java.util.ArrayList<>();

    private record NavIconSlot(String kind, StackPane slot,
                               java.util.List<Node> extras) {
    }

    /* ================= аккаунты ================= */
    private final usa.kitikat.account.AccountConfig accountConfig =
            usa.kitikat.account.AccountConfig.load();
    private final usa.kitikat.account.AccountStore accountStore =
            new usa.kitikat.account.AccountStore();
    private final usa.kitikat.account.AuthService authService =
            new usa.kitikat.account.AuthService(accountConfig);
    /** Идёт ли вход/регистрация прямо сейчас. */
    private boolean accountBusy;
    private String accountStatus;
    private String accountError;
    /** Идёт ли активация ключа прямо сейчас. */
    private boolean keyBusy;
    private String keyStatus;
    private String keyError;
    /** Идёт ли закрытие других входов (ротация сессии) прямо сейчас. */
    private boolean sessionBusy;
    private String sessionStatus;
    private String sessionError;
    /** Имя в верхней панели — обновляется после входа. */
    private Label barUserLabel;

    /* Персонализация: прозрачность, тема, живой фон, анимации. */
    private boolean animationsEnabled;
    /** Множитель длительности анимаций: 0.6 — быстро, 1.6 — медленно. */
    private double animSpeed = 1.0;
    private boolean liveWallpaper;
    private String accentId;
    private Timeline wallpaperPulse;
    private String accentCssUrl;
    /** Брать акцентный цвет из текущих обоев (галочка в настройках). */
    private boolean accentFromWallpaper;
    /** Цвет, вычисленный из обоев; null — пока не считали. */
    private String accentFromWallpaperHex;
    /** Аниме-оформление иконок меню (галочка в настройках). */
    private boolean animeIcons;

    /* Discord: что показывать сейчас и что показывать, когда игра закроется. */
    private String lastDetails = "Attack Launcher";
    private String lastState = "Смотрит каталог игр";
    /** Название модификации, с которой играют сейчас (для первой строки RPC). */
    private String lastModification = "";
    private volatile String runningGame;
    private GameActivityWatcher gameWatcher;
    /** Периодическая проверка: не вошли ли в аккаунт с другого ПК. */
    private Thread sessionWatcher;
    private Label rpcStatusLabel;

    public MainShell(Stage stage,
                     Runnable minimizeWindow,
                     java.util.function.BiConsumer<String, String> discordUpdateCallback,
                     java.util.function.DoubleConsumer scaleApplier) {
        this.stage = stage;
        this.minimizeWindow = minimizeWindow;
        this.discordUpdateCallback = discordUpdateCallback;
        this.scaleApplier = scaleApplier;

        getStyleClass().add("window-root");
        discordRpcEnabled = preferences.getBoolean("discordRpc", true);
        animationsEnabled = preferences.getBoolean("uiAnimations", true);
        animSpeed = preferences.getDouble("animSpeed", 1.0);
        liveWallpaper = preferences.getBoolean("liveWallpaper", true);
        accentId = preferences.get("accent", "blue");
        animeIcons = preferences.getBoolean("animeIcons", false);
        accentFromWallpaper = preferences.getBoolean("accentFromWallpaper", false);

        // метки обновлений клиентов из прошлого запуска — карточки сразу отличимы
        for (String name : preferences.get("clientUpdateMarkers", "").split(",")) {
            if (!name.isBlank()) updateMarkers.add(name.trim());
        }

        // последняя запущенная модификация — её пишем в первую строку Discord
        lastModification = preferences.get("lastModification", "").trim();

        // фоновые проверки не должны умирать молча — всё пишется в журнал
        Thread.setDefaultUncaughtExceptionHandler((thread, error) ->
                logUpdate("необработанная ошибка в потоке " + thread.getName(), error));

        // Миграция: переносим старые данные из NemesisLauncher → AttackDLC
        migrateFromOldLauncher();

        build();
        setupWallpaperLayer();

        // персонализация применяется до первого рендера страниц
        setWindowOpacity(preferences.getDouble("windowOpacity", 0.90));
        applyAccentTheme();
        startGameWatcher();

        showGames();
        refreshProfileAsync();
        startSessionWatcher();

        // при самом первом запуске — короткая настройка окна
        if (!preferences.getBoolean("onboardingDone", false)) {
            showOnboarding();
        } else {
            checkUpdatesOnStart();
            checkClientUpdatesOnStart();
        }
    }

    /* ================= диски и папки установки ================= */

    /** Выбранный диск установки, например «C:». */
    private String installDrive() {
        String value = preferences.get("installDrive", "C:");
        return value != null && value.length() >= 2 ? value.substring(0, 2) : "C:";
    }

    /** Папка Minecraft и клиентов: &lt;диск&gt;:\Nemesis */
    private Path gameDirectory() {
        return Path.of(installDrive(), "Nemesis");
    }

    /**
     * Папка клиента Delta: &lt;диск&gt;:\Delta.
     *
     * <p>Отдельно от {@link #gameDirectory()}: инстансы разные (свои mods,
     * versions, assets) — ставить Delta поверх Nemesis нельзя.</p>
     */
    private Path deltaDirectory() {
        return Path.of(installDrive(), "Delta");
    }

    /**
     * Папка сборки SystemDLC: &lt;диск&gt;:\SystemDLC.
     *
     * <p>Отдельный инстанс (1.21.4, Fabric): свои mods, versions, assets —
     * как и у Delta, ставить поверх Nemesis нельзя.</p>
     */
    private Path systemdlcDirectory() {
        return Path.of(installDrive(), "SystemDLC");
    }

    /**
     * Папка инстанса Rockstar: &lt;диск&gt;:\Rockstar.
     *
     * <p>Fabric 1.21.11 (кряк kitikat, порт) — скачивается zip-веткой
     * репозитория Rockstar, запускается как обычный инстанс.</p>
     */
    private Path rockstarDirectory() {
        return Path.of(installDrive(), "Rockstar");
    }

    /**
     * Папка инстанса Dimasik: &lt;диск&gt;:\Dimasik26.
     *
     * <p>Minecraft 1.21.11 (Fabric) — требует Java 21+, используется
     * портативный JRE из {@code runtime/} самого лаунчера.</p>
     */
    private Path dimasik26Directory() {
        return Path.of(installDrive(), "Dimasik26");
    }

    /**
     * Папка инстанса Attack DLC: &lt;диск&gt;:\minecraft.
     *
     * <p>Инстанс MC 26.2 ставится общий с Dimasik, а в хранилище клиентов
     * лежит только мод Attack DLC — обновляется он один, без тяжёлой сборки.</p>
     *
     * <p>Старые имена ({@code AttackDLC}, {@code Minecraft}) мигрируют в
     * {@code minecraft} один раз: содержимое переносится в новую папку,
     * старая удаляется.</p>
     */
    private Path attackdlcDirectory() {
        Path dir = Path.of(installDrive(), "minecraft");
        for (String legacy : new String[]{"AttackDLC", "Minecraft"}) {
            Path old = Path.of(installDrive(), legacy);
            if (old.toString().equals(dir.toString())) {
                continue;
            }
            if (old.toString().equalsIgnoreCase(dir.toString())) {
                // та же самая папка, только регистр другой — поправляем имя на месте
                try {
                    Files.move(old, dir);
                } catch (Throwable ignored) {
                }
                continue;
            }
            migrateLegacyGameDir(old, dir);
        }
        return dir;
    }

    /** Переносит содержимое {@code old} в {@code dir} и удаляет {@code old}. */
    private static void migrateLegacyGameDir(Path old, Path dir) {
        try {
            // страховка: не работаем, если пути совпадают без учёта регистра
            if (old.toString().equalsIgnoreCase(dir.toString())) {
                return;
            }
            if (!Files.exists(old)) {
                return;
            }
            if (!Files.exists(dir)) {
                Files.move(old, dir);   // просто переименовываем — старой папки не остаётся
                System.out.println("[AttackLauncher] game dir " + old.getFileName()
                        + " → " + dir.getFileName());
                return;
            }
            // новая папка уже есть — переносим файлы по одному, потом удаляем старую
            try (var children = Files.list(old)) {
                for (Path child : children.toList()) {
                    Path target = dir.resolve(child.getFileName().toString());
                    if (Files.isDirectory(child) && Files.isDirectory(target)) {
                        moveRecursive(child, target);
                        deleteRecursive(child);
                    } else {
                        Files.move(child, target,
                                java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    }
                }
            }
            deleteRecursive(old);
            System.out.println("[AttackLauncher] merged game dir " + old.getFileName()
                    + " → " + dir.getFileName() + " (old folder removed)");
        } catch (Throwable t) {
            System.err.println("[AttackLauncher] game dir migrate failed: " + t.getMessage());
        }
    }

    private static void moveRecursive(Path src, Path dst) throws java.io.IOException {
        try (var children = Files.list(src)) {
            for (Path child : children.toList()) {
                Path target = dst.resolve(child.getFileName().toString());
                if (Files.isDirectory(child)) {
                    Files.createDirectories(target);
                    moveRecursive(child, target);
                } else {
                    Files.move(child, target,
                            java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    private static void deleteRecursive(Path root) throws java.io.IOException {
        if (!Files.exists(root)) {
            return;
        }
        try (var walk = Files.walk(root)) {
            for (Path p : walk.sorted(java.util.Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(p);
            }
        }
    }

    /** Диски, на которые можно ставить игры (пустые приводы отсеяны). */
    private java.util.List<String> installDrives() {
        java.util.List<String> result = new java.util.ArrayList<>();
        for (java.io.File root : java.io.File.listRoots()) {
            try {
                if (root.getTotalSpace() > 0) {
                    result.add(root.getAbsolutePath().substring(0, 2));
                }
            } catch (Throwable ignored) {
            }
        }
        if (result.isEmpty()) {
            result.add("C:");
        }
        return result;
    }

    /** Место на диске: «186 ГБ свободно из 476 ГБ». */
    private String driveSpace(String drive) {
        try {
            java.io.File root = new java.io.File(drive + java.io.File.separator);
            long total = root.getTotalSpace();
            if (total <= 0) {
                return "диск недоступен";
            }
            return gb(root.getFreeSpace()) + " ГБ свободно из " + gb(total) + " ГБ";
        } catch (Throwable ignored) {
            return "место неизвестно";
        }
    }

    private static String gb(long bytes) {
        return Long.toString(Math.max(0, Math.round(bytes / 1073741824.0)));
    }

    private void build() {
        buildTitlebar();

        VBox nav = new VBox(6);
        nav.getStyleClass().add("sidebar");
        nav.setPadding(new Insets(18, 12, 16, 12));
        sidebarNav = nav;

        nav.getChildren().addAll(
                navButton("Игры", "games", this::showGames),
                navButton("Профиль", "user", this::showProfile),
                navButton("Настройки", "sliders", this::showSettings)
        );

        Region spacerBottom = new Region();
        VBox.setVgrow(spacerBottom, Priority.ALWAYS);

        nav.getChildren().addAll(spacerBottom, holdToCloseButton());

        /*
         * По умолчанию панель свёрнута до иконок.
         * Наведение мышью разворачивает её: иконки + текст.
         */
        setSidebarExpanded(false, false);
        nav.setOnMouseEntered(e -> setSidebarExpanded(true));
        nav.setOnMouseExited(e -> setSidebarExpanded(false));

        setLeft(nav);

        content.getStyleClass().add("content");
        setCenter(content);
        BorderPane.setMargin(content, new Insets(0, 14, 14, 0));
    }

    private void setupWallpaperLayer() {
        wallpaperView.getStyleClass().add("wallpaper-view");
        wallpaperView.setPreserveRatio(false);

        /*
         * ФОН НЕ УЧАСТВУЕТ В РАСЧЁТЕ РАЗМЕРОВ РОДИТЕЛЯ.
         *
         * ImageView нересайзабельный, поэтому его min/pref = текущим границам.
         * Если он остаётся managed, получается замкнутый круг:
         *   content.min = размер фона = размер content
         *   shell.min   = сайдбар + content.min + отступы
         * и окно перестаёт уменьшаться (контент «вылезает за окно»).
         *
         * Фон лежит на САМОМ ДНЕ оболочки и занимает всё окно —
         * поэтому он виден и под прозрачной боковой панелью,
         * и под тайтлбаром, и в полях вокруг контента.
         */
        wallpaperView.setManaged(false);
        wallpaperView.setLayoutX(0);
        wallpaperView.setLayoutY(0);
        wallpaperView.fitWidthProperty().bind(widthProperty());
        wallpaperView.fitHeightProperty().bind(heightProperty());
        wallpaperView.setMouseTransparent(true);

        wallpaperShade.getStyleClass().add("wallpaper-shade");
        wallpaperShade.setManaged(false);
        wallpaperShade.setMouseTransparent(true);
        wallpaperShade.setFill(Color.BLACK);
        wallpaperShade.widthProperty().bind(widthProperty());
        wallpaperShade.heightProperty().bind(heightProperty());

        /*
         * Скруглённая маска: без неё фон (неmanaged-узлы не режутся
         * фоном родителя) закрасил бы прозрачные углы окна.
         */
        Rectangle clip = new Rectangle();
        clip.widthProperty().bind(widthProperty());
        clip.heightProperty().bind(heightProperty());
        clip.setArcWidth(36);
        clip.setArcHeight(36);
        setClip(clip);

        // индексы 0 и 1 — рисуются раньше тайтлбара/панели/контента
        getChildren().addAll(0, java.util.List.<Node>of(wallpaperView, wallpaperShade));

        pageLayer.getStyleClass().add("page-layer");
        pageLayer.setMouseTransparent(false);
        pageLayer.setAlignment(Pos.TOP_LEFT);

        pageScroll.setContent(pageLayer);
        pageScroll.setFitToWidth(true);
        pageScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        pageScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        pageScroll.setPannable(false);
        pageScroll.getStyleClass().add("page-scroll");
        pageScroll.setMouseTransparent(false);
        pageScroll.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);

        content.getChildren().setAll(pageScroll);

        /* Чёрный минимализм: картинок-фонов нет — окно просто залито
           тёмным цветом от стиля, поэтому обои не загружаем, а сильнейшее
           затемнение (0) держим выключенным на случай старой настройки. */
        wallpaperView.setImage(null);
        setWallpaperDim(0.0);
    }

    private void loadWallpaper(String source) {
        try {
            Image image;
            if (source.startsWith("/")) {
                var stream = getClass().getResourceAsStream(source);
                if (stream == null) throw new IOException("Ресурс не найден: " + source);
                image = new Image(stream);
            } else {
                image = new Image(Path.of(source).toUri().toString());
            }
            wallpaperView.setImage(image);
            preferences.put("wallpaper", source);
        } catch (Exception ignored) {
            var stream = getClass().getResourceAsStream("/assets/wallpapers/wallpaper1.jpg");
            if (stream != null) wallpaperView.setImage(new Image(stream));
        }

        // «Цвет из фона»: акцент пересчитывается под новые обои
        if (accentFromWallpaper) {
            accentFromWallpaperHex = null;
            applyAccentTheme();
        }
    }

    /**
     * Главный цвет текущих обоев: берём усреднённый по яркости пиксель
     * из центральной зоны изображения. null — если картинка не читается.
     */
    private String pickWallpaperAccent() {
        Image image = wallpaperView.getImage();
        if (image == null || image.getWidth() < 1 || image.getHeight() < 1) {
            return null;
        }
        try {
            int w = (int) image.getWidth();
            int h = (int) image.getHeight();
            javafx.scene.image.PixelReader reader = image.getPixelReader();
            if (reader == null) {
                return null;
            }
            // средний цвет по сетке 16×16 из центральной половины
            long r = 0, g = 0, b = 0, n = 0;
            for (int y = h / 4; y < h * 3 / 4; y += Math.max(1, h / 16)) {
                for (int x = w / 4; x < w * 3 / 4; x += Math.max(1, w / 16)) {
                    int argb = reader.getArgb(x, y);
                    r += (argb >> 16) & 0xFF;
                    g += (argb >> 8) & 0xFF;
                    b += argb & 0xFF;
                    n++;
                }
            }
            if (n == 0) {
                return null;
            }
            // делаем цвет ярче, иначе акцент на панели почти не виден
            java.awt.Color color = new java.awt.Color(
                    (int) Math.min(255, (r / n) * 1.4),
                    (int) Math.min(255, (g / n) * 1.4),
                    (int) Math.min(255, (b / n) * 1.4));
            return String.format("#%02x%02x%02x",
                    color.getRed(), color.getGreen(), color.getBlue());
        } catch (Throwable ignored) {
            return null;
        }
    }

    private void setWallpaperDim(double value) {
        double clamped = Math.max(0.0, Math.min(0.75, value));
        wallpaperShade.setOpacity(clamped);
        preferences.putDouble("wallpaperDim", clamped);
    }

    private Button dot(String type) {
        Button button = new Button();
        button.getStyleClass().addAll("traffic-dot", type);

        switch (type) {
            case "close" -> button.setOnAction(e -> stage.close());
            case "min" -> button.setOnAction(e -> minimizeWindow.run());
            case "max" -> button.setOnAction(e ->
                    stage.setMaximized(!stage.isMaximized()));
            default -> button.setOnAction(e -> stage.setIconified(true));
        }

        return button;
    }

    /**
     * Шапка окна: логотип, имя пользователя и кнопки окна выбранного стиля.
     *
     * <p>Стиль берётся из настройки {@code windowControls}:
     * «dots» — три точки как в macOS слева, «classic» — обычные
     * кнопки справа, под дизайн лаунчера.</p>
     */
    private void buildTitlebar() {
        if (titlebar == null) {
            titlebar = new HBox(12);
            titlebar.getStyleClass().add("titlebar");
            titlebar.setAlignment(Pos.CENTER_LEFT);
            titlebar.setPadding(new Insets(13, 18, 11, 18));

            titlebar.setOnMousePressed(e -> {
                dragX = e.getSceneX();
                dragY = e.getSceneY();
            });
            titlebar.setOnMouseDragged(e -> {
                stage.setX(e.getScreenX() - dragX);
                stage.setY(e.getScreenY() - dragY);
            });
        }

        boolean classic = isClassicWindowControls();

        ImageView logo = image("/assets/nemesis.png", 26, 26);
        logo.getStyleClass().add("brand-logo");
        // логотип в заголовке — со скруглением
        logo.setClip(roundedClip(26, 26, 8));

        Label brand = new Label("ATTACK");
        brand.getStyleClass().add("bar-brand");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label user = new Label(activeUsername());
        user.getStyleClass().add("bar-user");
        barUserLabel = user;

        java.util.List<Node> parts = new java.util.ArrayList<>();

        if (!classic) {
            HBox traffic = new HBox(7, dot("close"), dot("min"), dot("max"));
            traffic.setAlignment(Pos.CENTER_LEFT);
            parts.add(traffic);
        }

        parts.add(logo);
        parts.add(brand);
        parts.add(spacer);
        parts.add(user);

        if (classic) {
            HBox buttons = new HBox(6,
                    winButton("min"), winButton("max"), winButton("close"));
            buttons.setAlignment(Pos.CENTER_LEFT);
            buttons.setPadding(new Insets(0, 0, 0, 6));
            parts.add(buttons);
        }

        titlebar.getChildren().setAll(parts);
        setTop(titlebar);
    }

    /** Обычные кнопки окна: свернуть, развернуть, закрыть. */
    private Button winButton(String kind) {
        Button button = new Button();
        button.setGraphic(winGlyph(kind));
        button.setFocusTraversable(false);
        button.getStyleClass().addAll("win-button", kind);

        switch (kind) {
            case "close" -> {
                button.setOnAction(e -> stage.close());
                button.setTooltip(new Tooltip("Закрыть"));
            }
            case "max" -> {
                button.setOnAction(e ->
                        stage.setMaximized(!stage.isMaximized()));
                button.setTooltip(new Tooltip("Развернуть"));
            }
            default -> {
                button.setOnAction(e -> minimizeWindow.run());
                button.setTooltip(new Tooltip("Свернуть"));
            }
        }

        return button;
    }

    /**
     * Векторные глифы для кнопок окна (вместо текстовых «— □ ×»):
     * тонкие штрихи 1.5px, скруглённые, под дизайн лаунчера.
     */
    private Group winGlyph(String kind) {
        javafx.scene.shape.Path path = new javafx.scene.shape.Path();
        path.getStyleClass().add("win-glyph");
        path.setFill(null);

        switch (kind) {
            case "close" -> path.getElements().addAll(
                    new MoveTo(3.5, 3.5), new LineTo(12.5, 12.5),
                    new MoveTo(12.5, 3.5), new LineTo(3.5, 12.5)
            );
            case "max" -> {
                Rectangle square = new Rectangle(3.5, 3.5, 9, 9);
                square.setArcWidth(2.5);
                square.setArcHeight(2.5);
                square.getStyleClass().add("win-glyph");
                square.setFill(null);
                return new Group(square);
            }
            default -> path.getElements().addAll(
                    new MoveTo(3, 9.5), new LineTo(13, 9.5)
            );
        }
        return new Group(path);
    }

    /** «Обычные кнопки» или «точки как в macOS» — выбирается при первом запуске. */
    private boolean isClassicWindowControls() {
        return "classic".equals(preferences.get("windowControls", "dots"));
    }

    private Button navButton(String text, String icon, Runnable action) {
        Button button = new Button();
        button.getStyleClass().add("nav-button");
        button.setOnAction(e -> action.run());
        button.setTooltip(new Tooltip(text));
        button.setAccessibleText(text);   // имя для скринридеров и UIA-тестов

        Label label = new Label(text);
        label.getStyleClass().add("nav-label");

        StackPane iconSlot = new StackPane(resolveIcon(icon));
        iconSlot.setMinSize(18, 18);
        iconSlot.setPrefSize(18, 18);
        iconSlot.setMaxSize(18, 18);
        iconSlot.setMouseTransparent(true);
        navIconSlots.add(new NavIconSlot(icon, iconSlot, java.util.List.of()));

        HBox graphic = new HBox(10, iconSlot, label);
        graphic.setAlignment(Pos.CENTER_LEFT);
        graphic.setMouseTransparent(true);
        button.setGraphic(graphic);

        navLabels.add(label);
        return button;
    }

    /**
     * Кнопка «Закрыть»: лаунчер закрывается только если её <b>зажать</b> —
     * вокруг иконки идёт круговой прогресс; отпустил раньше времени —
     * дуга плавно сбрасывается, окно остаётся открытым.
     */
    private Button holdToCloseButton() {
        Button button = new Button();
        button.getStyleClass().add("nav-button");
        button.setTooltip(new Tooltip("Закрыть — зажми кнопку до конца"));
        button.setAccessibleText("Закрыть");

        Label label = new Label("Закрыть");
        label.getStyleClass().add("nav-label");

        // круговой прогресс вокруг иконки
        Arc ring = new Arc();
        ring.setCenterX(9);
        ring.setCenterY(9);
        ring.setRadiusX(11.5);
        ring.setRadiusY(11.5);
        ring.setType(ArcType.OPEN);
        ring.setFill(null);
        ring.setStroke(Color.web("#ffc9c5"));   // светлая дуга — видно на красном
        ring.setStrokeWidth(2.8);
        ring.setStrokeLineCap(StrokeLineCap.ROUND);
        ring.setStartAngle(-90);        // старт сверху
        ring.setLength(0);
        ring.setEffect(new javafx.scene.effect.DropShadow(
                7, Color.web("#ff5f57", 0.9)));

        StackPane iconSlot = new StackPane(navIcon("close"), ring);
        iconSlot.setMinSize(18, 18);
        iconSlot.setPrefSize(18, 18);
        iconSlot.setMaxSize(18, 18);
        iconSlot.setMouseTransparent(true);
        navIconSlots.add(new NavIconSlot(
                "close", iconSlot, java.util.List.of(ring)));

        HBox graphic = new HBox(10, iconSlot, label);
        graphic.setAlignment(Pos.CENTER_LEFT);
        graphic.setMouseTransparent(true);
        button.setGraphic(graphic);
        navLabels.add(label);

        final double holdMillis = 1100;
        Timeline hold = new Timeline(new KeyFrame(
                Duration.millis(holdMillis),
                new KeyValue(ring.lengthProperty(), 360.0, Interpolator.LINEAR)
        ));
        Timeline reset = new Timeline(new KeyFrame(
                Duration.millis(200),
                new KeyValue(ring.lengthProperty(), 0.0, Interpolator.EASE_OUT)
        ));

        hold.setOnFinished(e -> {
            reset.stop();
            stage.close();
        });

        button.addEventHandler(javafx.scene.input.MouseEvent.MOUSE_PRESSED, e -> {
            reset.stop();
            ring.setLength(0);
            if (!button.getStyleClass().contains("closing")) {
                button.getStyleClass().add("closing");
            }
            hold.playFromStart();
        });

        Runnable cancel = () -> {
            if (hold.getStatus() == javafx.animation.Animation.Status.RUNNING) {
                hold.stop();
                reset.playFromStart();
            }
            button.getStyleClass().remove("closing");
        };
        button.addEventHandler(javafx.scene.input.MouseEvent.MOUSE_RELEASED, e -> cancel.run());
        button.addEventHandler(javafx.scene.input.MouseEvent.MOUSE_EXITED, e -> cancel.run());

        return button;
    }

    /** Иконка слота: аниме-оформление или стандартная штриховая. */
    private Node resolveIcon(String kind) {
        if (animeIcons) {
            Node anime = animeIcon(kind);
            if (anime != null) {
                return anime;
            }
        }
        return navIcon(kind);
    }

    /** Перерисовывает иконки бокового меню (после смены аниме-пака). */
    private void refreshNavIcons() {
        for (NavIconSlot slot : navIconSlots) {
            java.util.List<Node> children = new java.util.ArrayList<>();
            children.add(resolveIcon(slot.kind()));
            children.addAll(slot.extras());
            slot.slot().getChildren().setAll(children);
        }
    }

    /**
     * Аниме-иконки вместо штриховых: цветок сакуры, котик, лапка,
     * сердечко и искра. null — для вида, у которого аниме-версии нет.
     */
    private Node animeIcon(String kind) {
        javafx.scene.Group icon = new javafx.scene.Group();
        Color pink = Color.web("#ff9ecb");
        Color lightPink = Color.web("#ffc4dd");
        Color deepPink = Color.web("#ff6fae");

        switch (kind) {
            case "games" -> {
                // котик: мордочка с ушками и усиками
                Circle face = new Circle(9, 10, 6);
                face.setFill(lightPink);
                javafx.scene.shape.Path earL = new javafx.scene.shape.Path(
                        new MoveTo(4.2, 6.5), new LineTo(5.5, 1.5),
                        new LineTo(9, 5.5), new ClosePath());
                earL.setFill(pink);
                javafx.scene.shape.Path earR = new javafx.scene.shape.Path(
                        new MoveTo(13.8, 6.5), new LineTo(12.5, 1.5),
                        new LineTo(9, 5.5), new ClosePath());
                earR.setFill(pink);
                Circle eyeL = new Circle(6.8, 9.5, 0.9);
                Circle eyeR = new Circle(11.2, 9.5, 0.9);
                eyeL.setFill(Color.web("#4a2b3a"));
                eyeR.setFill(Color.web("#4a2b3a"));
                Line mouth = new Line(8, 12.4, 10, 12.4);
                mouth.setStroke(Color.web("#4a2b3a"));
                mouth.setStrokeWidth(1.1);
                mouth.setStrokeLineCap(StrokeLineCap.ROUND);
                icon.getChildren().addAll(earL, earR, face, eyeL, eyeR, mouth);
            }
            case "user" -> {
                // лапка: подушечка и четыре пальчика
                Circle pad = new Circle(9, 11.5, 4.2);
                pad.setFill(pink);
                double[][] toes = {{4.6, 6.4}, {7.3, 4.4}, {10.7, 4.4}, {13.4, 6.4}};
                for (double[] toe : toes) {
                    Circle dot = new Circle(toe[0], toe[1], 1.9);
                    dot.setFill(lightPink);
                    icon.getChildren().add(dot);
                }
                icon.getChildren().add(pad);
            }
            case "sliders" -> {
                // сердечко
                javafx.scene.shape.Path heart = new javafx.scene.shape.Path(
                        new MoveTo(9, 15.5),
                        new CubicCurveTo(2, 10.5, 3.5, 3.5, 9, 6.5),
                        new CubicCurveTo(14.5, 3.5, 16, 10.5, 9, 15.5),
                        new ClosePath());
                heart.setFill(deepPink);
                icon.getChildren().add(heart);
            }
            case "close" -> {
                // искра — четыре луча с перекрестием
                Line v = new Line(9, 1.5, 9, 16.5);
                Line h = new Line(1.5, 9, 16.5, 9);
                Line d1 = new Line(4, 4, 14, 14);
                Line d2 = new Line(14, 4, 4, 14);
                for (Line ray : new Line[]{v, h, d1, d2}) {
                    ray.setStroke(pink);
                    ray.setStrokeWidth(1.8);
                    ray.setStrokeLineCap(StrokeLineCap.ROUND);
                }
                icon.getChildren().addAll(d1, d2, v, h);
            }
            default -> {
                // цветок сакуры: пять лепестков вокруг серединки
                for (int i = 0; i < 5; i++) {
                    double angle = Math.toRadians(i * 72 - 90);
                    double cx = 9 + Math.cos(angle) * 4.6;
                    double cy = 9 + Math.sin(angle) * 4.6;
                    Ellipse petal = new Ellipse(cx, cy, 2.9, 3.6);
                    petal.setFill(pink);
                    petal.setRotate(i * 72);
                    icon.getChildren().add(petal);
                }
                Circle center = new Circle(9, 9, 2);
                center.setFill(Color.web("#ffe27a"));
                icon.getChildren().add(center);
            }
        }
        return icon;
    }

    /** Простые штриховые иконки (18×18), рисуются кодом — ресурсов нет. */
    private Node navIcon(String kind) {
        Group icon = new Group();

        switch (kind) {
            case "games" -> {
                Rectangle pad = new Rectangle(1.5, 5, 15, 9);
                pad.setArcWidth(6);
                pad.setArcHeight(6);
                // крестовина D-pad
                Line crossH = new Line(4, 9.5, 8, 9.5);
                Line crossV = new Line(6, 7.5, 6, 11.5);
                // кнопки
                Line btnA = new Line(12, 8, 14, 8);
                Line btnB = new Line(13, 7, 13, 9);
                icon.getChildren().addAll(pad, crossH, crossV, btnA, btnB);
            }
            case "user" -> {
                Circle head = new Circle(9, 6, 3);
                javafx.scene.shape.Path shoulders = new javafx.scene.shape.Path();
                shoulders.getElements().addAll(
                        new MoveTo(3, 16),
                        new ArcTo(6.5, 6.5, 0, 15, 16, false, true)
                );
                icon.getChildren().addAll(head, shoulders);
            }
            case "sliders" -> {
                Line l1 = new Line(2, 5.5, 16, 5.5);
                Line l2 = new Line(2, 9.5, 16, 9.5);
                Line l3 = new Line(2, 13.5, 16, 13.5);
                icon.getChildren().addAll(l1, l2, l3, sliderKnob(6, 5.5), sliderKnob(11.5, 9.5), sliderKnob(5, 13.5));
            }
            default -> icon.getChildren().addAll(
                    new Line(5, 5, 13, 13),
                    new Line(13, 5, 5, 13)
            );
        }

        for (Node child : icon.getChildren()) {
            if (child instanceof Shape shape) {
                shape.setFill(null);
            }
            child.getStyleClass().add("nav-icon");
        }
        return icon;
    }

    private Rectangle sliderKnob(double cx, double cy) {
        Rectangle knob = new Rectangle(cx - 1.7, cy - 1.7, 3.4, 3.4);
        knob.setArcWidth(3);
        knob.setArcHeight(3);
        return knob;
    }

    /* ---------- сворачивание боковой панели ---------- */

    private void setSidebarExpanded(boolean expand) {
        setSidebarExpanded(expand, true);
    }

    private void setSidebarExpanded(boolean expand, boolean animate) {
        if (sidebarNav == null) {
            return;
        }
        stopSidebarAnims();

        if (expand) {
            // 1) панель раскрывается, 2) затем проявляется текст
            animateSidebarWidth(SIDEBAR_EXPANDED, animate, () -> setLabelsVisible(true, animate));
        } else {
            // 1) текст уходит, 2) затем панель сжимается до иконок
            setLabelsVisible(false, animate);
            if (animate) {
                javafx.animation.PauseTransition delay =
                        new javafx.animation.PauseTransition(Duration.millis(150));
                delay.setOnFinished(e -> animateSidebarWidth(SIDEBAR_COLLAPSED, true, null));
                delay.play();
                sidebarAnims.add(delay);
            } else {
                sidebarNav.setPrefWidth(SIDEBAR_COLLAPSED);
            }
        }
    }

    private void animateSidebarWidth(double target, boolean animate, Runnable onDone) {
        if (!animate) {
            sidebarNav.setPrefWidth(target);
            if (onDone != null) {
                onDone.run();
            }
            return;
        }
        Timeline timeline = new Timeline(new KeyFrame(
                Duration.millis(180),
                new KeyValue(sidebarNav.prefWidthProperty(), target, Interpolator.EASE_OUT)
        ));
        if (onDone != null) {
            timeline.setOnFinished(e -> onDone.run());
        }
        timeline.play();
        sidebarAnims.add(timeline);
    }

    private void setLabelsVisible(boolean show, boolean animate) {
        Timeline timeline = new Timeline();
        double target = show ? 1 : 0;

        for (Label label : navLabels) {
            if (show) {
                label.setVisible(true);
                label.setManaged(true);
            }
            if (animate) {
                timeline.getKeyFrames().add(new KeyFrame(
                        Duration.millis(140),
                        new KeyValue(label.opacityProperty(), target, Interpolator.EASE_OUT)
                ));
            } else {
                label.setOpacity(target);
                if (!show) {
                    label.setVisible(false);
                    label.setManaged(false);
                }
            }
        }

        if (!show && animate) {
            timeline.setOnFinished(e -> navLabels.forEach(label -> {
                label.setVisible(false);
                label.setManaged(false);
            }));
        }
        if (animate) {
            timeline.play();
            sidebarAnims.add(timeline);
        }
    }

    private void stopSidebarAnims() {
        for (Animation anim : sidebarAnims) {
            if (anim.getStatus() == Animation.Status.RUNNING) {
                anim.stop();
            }
        }
        sidebarAnims.clear();
    }

    private void swap(Node node) {
        pageLayer.getChildren().setAll(node);

        if (!animationsEnabled) {
            node.setOpacity(1);
            node.setTranslateY(0);
            return;
        }

        // «живая» смена страницы: проявление + лёгкий сдвиг снизу вверх
        node.setOpacity(0);
        node.setTranslateY(12);

        FadeTransition fade = new FadeTransition(Duration.millis(240 * animSpeed), node);
        fade.setToValue(1);

        TranslateTransition slide = new TranslateTransition(Duration.millis(260 * animSpeed), node);
        slide.setToY(0);

        new ParallelTransition(fade, slide).play();
    }

    /** Покадровое появление карточек при открытии каталога игр. */
    private void animateCardsIn(FlowPane container) {
        if (!animationsEnabled) {
            return;
        }
        int index = 0;
        for (Node card : container.getChildren()) {
            card.setOpacity(0);
            FadeTransition fade = new FadeTransition(Duration.millis(280 * animSpeed), card);
            fade.setFromValue(0);
            fade.setToValue(1);
            fade.setDelay(Duration.millis((70 + index * 60L) * animSpeed));
            fade.play();
            index++;
        }
    }

    private void showGames() {
        refreshRpcIdentity();

        VBox page = page(
                "ИГРЫ",
                "Выберите игру",
                "Выберите игру, затем клиент или сборку."
        );

        FlowPane games = new FlowPane(14, 14);
        games.setPrefWrapLength(680);
        games.getChildren().addAll(
                minecraftCard(),
                simpleGameCard("Dota 2", "MOBA · официальный клиент", "/assets/wallpapers/wallpaper4.jpg", "/assets/dota2-icon.png")
        );

        page.getChildren().add(games);
        animateCardsIn(games);
        swap(page);
    }

    
    private StackPane minecraftCard() {
        StackPane card = new StackPane();
        card.getStyleClass().add("game-card-small");
        card.setPrefSize(245, 158);
        card.setClip(roundedClip(245, 158, 15));

        /* Плоская карточка: фото-фон заменён на ровный тёмный прямоугольник
           из стиля, так что остаётся только иконка и подпись. */
        StackPane overlay = new StackPane();
        overlay.getStyleClass().add("game-card-overlay");

        ImageView icon = image("/assets/minecraft-icon.png", 34, 34);
        icon.getStyleClass().add("game-card-icon");
        StackPane.setAlignment(icon, Pos.TOP_LEFT);
        StackPane.setMargin(icon, new Insets(12));

        VBox text = new VBox(3);
        text.setAlignment(Pos.BOTTOM_LEFT);
        text.setPadding(new Insets(14));

        Label title = new Label("Minecraft");
        title.getStyleClass().add("game-card-title");

        Label subtitle = new Label("Клиенты и сборки");
        subtitle.getStyleClass().add("game-card-subtitle");

        text.getChildren().addAll(title, subtitle);
        StackPane.setAlignment(text, Pos.BOTTOM_LEFT);

        overlay.getChildren().addAll(icon, text);
        card.getChildren().add(overlay);

        card.setOnMouseClicked(e -> showMinecraftClients());

        return card;
    }

    /**
     * Карточка игры без картины-фона: только плоский тёмный прямоугольник,
     * иконка и строки названия/подписи.
     *
     * @param backgroundPath не используется — оставлен только ради сигнатуры
     */
    private StackPane simpleGameCard(String title, String subtitle, String backgroundPath, String iconPath) {
        StackPane card = new StackPane();
        card.getStyleClass().add("game-card-small");
        card.setPrefSize(245, 158);
        card.setClip(roundedClip(245, 158, 15));

        StackPane overlay = new StackPane();
        overlay.getStyleClass().add("game-card-overlay");

        try {
            ImageView icon = image(iconPath, 34, 34);
            icon.getStyleClass().add("game-card-icon");
            StackPane.setAlignment(icon, Pos.TOP_LEFT);
            StackPane.setMargin(icon, new Insets(12));
            overlay.getChildren().add(icon);
        } catch (Exception ignored) {}

        VBox text = new VBox(3);
        text.setAlignment(Pos.BOTTOM_LEFT);
        text.setPadding(new Insets(14));
        Label t = new Label(title); t.getStyleClass().add("game-card-title");
        Label s = new Label(subtitle); s.getStyleClass().add("game-card-subtitle");
        text.getChildren().addAll(t, s);
        StackPane.setAlignment(text, Pos.BOTTOM_LEFT);
        overlay.getChildren().add(text);
        card.getChildren().add(overlay);
        card.setOnMouseClicked(e -> showUnsupportedGame(title));
        return card;
    }

    private Rectangle roundedClip(double width, double height, double radius) {
        Rectangle clip = new Rectangle(width, height);
        clip.setArcWidth(radius * 2);
        clip.setArcHeight(radius * 2);
        return clip;
    }

    private void setCardBackground(Region card, String path) {
        try {
            var stream = getClass().getResourceAsStream(path);
            if (stream == null) return;
            Image bg = new Image(stream);
            card.setBackground(new Background(new BackgroundImage(
                    bg, BackgroundRepeat.NO_REPEAT, BackgroundRepeat.NO_REPEAT,
                    BackgroundPosition.CENTER, new BackgroundSize(100, 100, false, false, true, true))));
        } catch (Exception ignored) {}
    }

    private void showUnsupportedGame(String game) {
        discordUpdate("Смотрит: " + game, "Выбор игры");
        VBox page = page("ИГРА", game, "Официальный клиент — запускается через Steam.");

        VBox card = new VBox(8);
        card.getStyleClass().add("empty-state-card");

        Label title = new Label("Запуск через Steam");
        title.getStyleClass().add("title-medium");

        Label text = new Label(
                "Для " + game + " модификации не нужны — игра откроется "
                        + "через официальный клиент Steam."
        );
        text.setWrapText(true);
        text.getStyleClass().add("muted");

        Label status = new Label("");
        status.getStyleClass().add("status");

        Button back = new Button("← Назад к играм");
        back.getStyleClass().add("ghost-button");
        back.setOnAction(e -> showGames());

        Button play = new Button("Запустить " + game);
        play.getStyleClass().add("primary-button");
        play.setOnAction(e -> launchSteamGame(game, "570", status, play));

        card.getChildren().addAll(title, text, new HBox(9, back, play), status);
        page.getChildren().add(card);
        swap(page);
    }

    /** Запуск игры через Steam (steam://run/&lt;appid&gt;). */
    private void launchSteamGame(String game, String appId,
                                 Label status, Button button) {
        button.setDisable(true);
        rememberModification(game);
        status.setText("Открываем Steam…");
        Thread.startVirtualThread(() -> {
            try {
                new ProcessBuilder(
                        "cmd", "/c", "start", "", "steam://run/" + appId)
                        .start();
                javafx.application.Platform.runLater(() ->
                        status.setText(game + " запускается в Steam."));
            } catch (Exception ex) {
                javafx.application.Platform.runLater(() -> {
                    status.setText("Steam не найден — установи его и повтори.");
                    button.setDisable(false);
                });
            }
        });
    }

    /**
     * Карточка клиента Minecraft: картинка в списке, иконка на странице
     * установки, описание и откуда качать сборку.
     *
     * @param picturePath картинка в карточке (может иметь собственный фон)
     * @param iconPath    иконка на странице установки — прозрачная, без фона
     * @param archiveUrl  zip-архив ветки репозитория, который качаем
     * @param installDir  папка, куда распаковывается инстанс
     */
    private record MinecraftClient(
            String name,
            String version,
            String description,
            String picturePath,
            String iconPath,
            String repository,
            String archiveUrl,
            Path installDir
    ) {
    }

    private void showMinecraftClients() {
        discordUpdate("Смотрит: Minecraft", "Выбор клиента");

        VBox page = page(
                "MINECRAFT",
                "Выберите клиент",
                "Установленные клиенты и сборки Minecraft."
        );

        FlowPane grid = new FlowPane(12, 12);
        clientsGrid = grid;
        for (MinecraftClient client : minecraftClients()) {
            if (isAttackClient(client) && !hasAttackAccess()) {
                // бесплатным карточка платного клиента не показывается
                continue;
            }
            grid.getChildren().add(clientCard(client));
        }

        page.getChildren().add(grid);
        swap(page);
    }

    /** Все карточки Minecraft-клиентов: общий список для страницы и проверки обновлений. */
    private java.util.List<MinecraftClient> minecraftClients() {
        MinecraftClient nemesis = new MinecraftClient(
                "Nemesis",
                "Fabric 1.21.11",
                "Официальная сборка Nemesis для Minecraft.",
                "/assets/nemesis-client.png",
                "/assets/nemesis-client.png",
                NEMA_REPOSITORY,
                GitHubClient.ARCHIVE_URL,
                gameDirectory()
        );

        MinecraftClient delta = new MinecraftClient(
                "Delta",
                "Дельта 1.21.4 → 1.21.11",
                "Дельта 1.21.4, перенесённая на 1.21.11.",
                "/assets/delta-background.png",
                "/assets/delta-icon.png",
                DELTA_REPOSITORY,
                DELTA_REPOSITORY + "/archive/refs/heads/main.zip",
                deltaDirectory()
        );

        MinecraftClient systemdlc = new MinecraftClient(
                "SystemDLC",
                "Fabric 1.21.4",
                "SystemDLC — клиент, который изменил стандарты премиальных читов: " +
                        "моды, ресурсы и версии входят в сборку.",
                "/assets/systemdlc-client.png",
                "/assets/systemdlc-icon.png",
                SYSTEMDLC_REPOSITORY,
                SYSTEMDLC_REPOSITORY + "/archive/refs/heads/main.zip",
                systemdlcDirectory()
        );

        MinecraftClient rockstar = new MinecraftClient(
                "Rockstar",
                "Fabric 1.21.11",
                "Кряк сделан twxlight — это порт. " +
                        "Этот чит — эволюция визуальной части среди читов.",
                "/assets/rockstar-client.png",
                "/assets/rockstar-icon.png",
                ROCKSTAR_REPOSITORY,
                ROCKSTAR_REPOSITORY + "/archive/refs/heads/main.zip",
                rockstarDirectory()
        );

        MinecraftClient dimasik = new MinecraftClient(
                "Dimasik",
                "Fabric 26.2",
                "Dimasik — клиент для Minecraft 26.2. " +
                        "Кряк сделан ",
                "/assets/dimasik-client.png",
                "/assets/dimasik-icon.png",
                DIMASIK26_REPOSITORY,
                DIMASIK26_REPOSITORY + "/archive/refs/heads/main.zip",
                dimasik26Directory()
        );

        MinecraftClient attack = new MinecraftClient(
                "Attack DLC",
                "Fabric 1.21.11",
                "Клиент Аттак — новый платный клиент, лучший из всех.",
                "/assets/attackdlc-client.jpg",
                "/assets/attackdlc-icon.png",
                ATTACKDLC_REPOSITORY,
                // инстанс MC 1.21.11 берём из Nema (Fabric), моды — из своего репозитория
                NEMA_REPOSITORY + "/archive/refs/heads/main.zip",
                attackdlcDirectory()
        );

        return java.util.List.of(attack, nemesis, delta, systemdlc, rockstar, dimasik);
    }

    /**
     * Есть ли доступ к платному клиенту Attack DLC.
     *
     * <p>Владелец — всегда. Остальным — только с активным ключом:
     * бесплатным пользователям карточка клиента не показывается вовсе,
     * а сам клиент качается по токену из закрытого хранилища, так что
     * скачать его мимо лаунчера нельзя.</p>
     */
    private boolean hasAttackAccess() {
        var profile = accountStore.profile();
        if (profile == null) {
            return false;
        }
        if ("owner".equals(profile.roleOrFree())) {
            return true;
        }
        // ютуберы получают доступ по роли, без ключа и подписки
        if (profile.isYoutuber()) {
            return true;
        }
        if (!profile.hasKey()) {
            return false;
        }
        var sub = accountStore.subscription();
        if (sub == null) {
            return false;
        }
        return sub.active() && sub.ownedBy(profile.uid());
    }

    /** Это платный клиент Attack (виден только тем, у кого есть доступ). */
    private boolean isAttackClient(MinecraftClient client) {
        return client != null && ATTACKDLC_REPOSITORY.equals(client.repository());
    }

    private VBox clientCard(MinecraftClient client) {
        VBox card = new VBox(8);
        card.getStyleClass().add("client-card");
        card.setPadding(new Insets(10));
        card.setPrefSize(205, 132);

        /* Вместо скриншота — только логотип клиента по центру плашки:
           карточка получается плоской, без картинок и теней. */
        ImageView image = image(client.iconPath(), 34, 34);
        image.setPreserveRatio(true);

        StackPane imageBox = new StackPane(image);
        imageBox.getStyleClass().add("client-image");
        imageBox.setPrefHeight(70);

        Label title = new Label(client.name());
        title.getStyleClass().add("card-title");

        Label versionLabel = new Label(client.version());
        versionLabel.getStyleClass().add("card-version");

        // клиент в репозитории отличается от установленного — метка на скриншоте
        if (updateMarkers.contains(client.name())
                || clientUpdates.containsKey(client.name())) {
            Label badge = new Label("Есть обновление");
            badge.getStyleClass().add("update-badge");
            StackPane.setAlignment(badge, Pos.TOP_RIGHT);
            StackPane.setMargin(badge, new Insets(6));
            imageBox.getChildren().add(badge);
        }

        Button open = new Button("Открыть");
        open.getStyleClass().add("card-button");
        open.setMaxWidth(Double.MAX_VALUE);
        open.setOnAction(e ->
                showGameDetails(client)
        );

        card.getChildren().addAll(imageBox, title, versionLabel, open);
        return card;
    }

    private void showGameDetails(MinecraftClient client) {
        VBox page = page(
                "MINECRAFT",
                client.name(),
                client.version() + " · установка и обновление"
        );

        HBox head = new HBox(14);

        ImageView icon = image(client.iconPath(), 90, 70);

        Label cardTitle = new Label(client.name());
        cardTitle.getStyleClass().add("title-medium");

        Label cardDescription = new Label(client.description());
        cardDescription.getStyleClass().add("muted");
        cardDescription.setWrapText(true);

        VBox info = new VBox(6, cardTitle, cardDescription);
        info.setFillWidth(true);
        info.setMaxWidth(Double.MAX_VALUE);

        // карточка с названием и описанием — как на странице обновления
        StackPane infoCard = new StackPane(info);
        infoCard.getStyleClass().add("client-info-card");
        infoCard.setMaxWidth(Double.MAX_VALUE);

        head.getChildren().addAll(icon, infoCard);
        HBox.setHgrow(infoCard, Priority.ALWAYS);

        ProgressBar progress = new ProgressBar(0);
        progress.setMaxWidth(Double.MAX_VALUE);
        progress.setVisible(false);

        Label status = new Label("Готово.");
        status.getStyleClass().add("status");

        boolean installed = githubClient.isInstalled(client.installDir());

        Button update = new Button("Проверить обновление");
        update.getStyleClass().add("primary-button");
        update.setOnAction(e -> {
            if (installed) {
                // установленный клиент сверяем по-настоящему: mods/ против репозитория
                checkClientUpdate(client, update, progress, status);
            } else {
                checkManifest(client.archiveUrl(), update, progress, status);
            }
        });

        Button download = new Button(
                installed ? "Запустить" : "Скачать / установить"
        );
        download.getStyleClass().add("secondary-button");
        download.setOnAction(e ->
                downloadClient(
                        client,
                        download,
                        progress,
                        status
                )
        );

        if (installed) {
            GitHubClient.ModsDiff pending = clientUpdates.get(client.name());
            status.setText(pending == null
                    ? "Сборка уже установлена · готова к запуску"
                    : "Доступно обновление: " + pending.download().size() + " файл(ов)"
                        + (pending.remove().isEmpty()
                            ? ""
                            : ", удалится " + pending.remove().size())
                        + " — нажмите «Проверить обновление»");
        }

        Button back = new Button("← Назад");
        back.getStyleClass().add("ghost-button");
        back.setOnAction(e -> showMinecraftClients());

        page.getChildren().addAll(
                head,
                new Separator(),
                new HBox(9, back, update, download),
                progress,
                status
        );

        swap(page);
    }

    private void checkManifest(
            String archiveUrl,
            Button button,
            ProgressBar progress,
            Label status
    ) {
        button.setDisable(true);
        progress.setVisible(true);
        progress.setProgress(-1);
        status.setText("Проверяем...");

        Thread.startVirtualThread(() -> {
            try {
                githubClient.checkArchive(archiveUrl);

                javafx.application.Platform.runLater(() ->
                        status.setText("Готово к скачиванию")
                );
            } catch (Exception ex) {
                javafx.application.Platform.runLater(() ->
                        showError("Не удалось проверить ", ex)
                );
            } finally {
                javafx.application.Platform.runLater(() -> {
                    progress.setVisible(false);
                    button.setDisable(false);
                });
            }
        });
    }

    private void downloadClient(
            MinecraftClient client,
            Button button,
            ProgressBar progress,
            Label status
    ) {
        button.setDisable(true);

        Thread.startVirtualThread(() -> {
            try {
                if (githubClient.isInstalled(client.installDir())) {
                    javafx.application.Platform.runLater(() -> {
                        status.setText("Сборка уже установлена · запускаем...");
                        progress.setVisible(false);
                        launchMinecraft(client, status);
                    });
                    return;
                }

                javafx.application.Platform.runLater(() -> {
                    progress.setVisible(true);
                    progress.setProgress(0);
                    status.setText("Подготовка скачивания...");
                });

                githubClient.downloadArchive(
                        client.archiveUrl(),
                        client.installDir(),
                        p -> javafx.application.Platform.runLater(
                                () -> progress.setProgress(p)
                        ),
                        text -> javafx.application.Platform.runLater(
                                () -> status.setText(text)
                        )
                );

                /*
                 * Клиент уезжает из mods/ в отдельное хранилище:
                 * папка mods инстанса остаётся пустой.
                 */
                ClientStore.moveModsOut(client.installDir(),
                        githubClient.remoteModFiles(client.repository()));

                /*
                 * Моды синхронизируем с репозиторием клиента: у Attack DLC
                 * инстанс общий с Dimasik, поэтому из mods/ убирается чужой
                 * мод и докладывается свой.
                 */
                githubClient.updateMods(
                        client.installDir(),
                        client.repository(),
                        p -> javafx.application.Platform.runLater(
                                () -> progress.setProgress(p)
                        ),
                        text -> javafx.application.Platform.runLater(
                                () -> status.setText(text)
                        )
                );

                // первая строка — с какой модификацией играют
                rememberModification(client.name());
                javafx.application.Platform.runLater(() -> {
                    status.setText("Файлы готовы. Запуск Minecraft...");
                    progress.setVisible(false);
                    launchMinecraft(client, status);
                });

            } catch (Exception ex) {
                javafx.application.Platform.runLater(() -> {
                    status.setText("Ошибка скачивания: " + safeError(ex));
                    showError("Ошибка скачивания Minecraft", ex);
                    button.setDisable(false);
                    progress.setVisible(false);
                });
            }
        });
    }

    /**
     * Оставляет клиенту файл входа: uid, ник, токены аккаунта и ключ.
     *
     * <p>Клиент читает его при старте, чтобы показать ник в меню,
     * ватермарке и Discord RPC и проверить подписку. Файл лежит в
     * {@code %LOCALAPPDATA%\AttackDLC} — в папке игры его нет.</p>
     */
    private void writeClientSession() {
        var session = accountStore.session();
        if (session == null) {
            return;
        }
        try {
            // токен должен быть живым — иначе клиент сразу увидит «сессия истекла»
            if (session.expiresAt() <= System.currentTimeMillis() / 1000 + 300) {
                var fresh = authService.refreshIfNeeded(session);
                if (fresh != null) {
                    session = fresh;
                    accountStore.save(fresh);
                }
            }
        } catch (Exception ignored) {
            // клиент обновит токен сам по refresh-токену
        }
        try {
            var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            var object = mapper.createObjectNode();
            object.put("uid", session.uid() == null ? "" : session.uid());
            object.put("nick", activeUsername() == null ? "" : activeUsername());
            object.put("idToken",
                    session.idToken() == null ? "" : session.idToken());
            object.put("refreshToken",
                    session.refreshToken() == null ? "" : session.refreshToken());
            var profile = accountStore.profile();
            object.put("activatedKey", profile == null || profile.activatedKey() == null
                    ? "" : profile.activatedKey());
            object.put("role", profile == null ? "free" : profile.roleOrFree());
            object.put("hwid", usa.kitikat.account.Hwid.get());
            var sub = accountStore.subscription();
            object.put("expiresAt", sub == null ? 0L : sub.expiresAt());
            // модификация, которой играют — первая строка Discord RPC клиента
            object.put("modification", lastModification == null ? "" : lastModification);
            object.put("funpayUrl", accountConfig.funpayUrl() == null
                    ? "" : accountConfig.funpayUrl());
            object.put("siteUrl", accountConfig.siteUrl() == null
                    ? "" : accountConfig.siteUrl());
            // когда записан файл — клиент покажет, насколько свежие данные
            object.put("writtenAt", System.currentTimeMillis());

            Path file = Path.of(System.getenv("LOCALAPPDATA"),
                    "AttackDLC", "client-session.json");
            Files.createDirectories(file.getParent());
            Files.writeString(file, mapper.writeValueAsString(object));
        } catch (Exception ex) {
            logUpdate("не удалось записать файл входа для клиента", ex);
        }
    }

    /** Удаляет файл входа: клиент сразу теряет доступ. */
    private void deleteClientSession() {
        try {
            String local = System.getenv("LOCALAPPDATA");
            if (local == null || local.isBlank()) {
                return;
            }
            Files.deleteIfExists(Path.of(local, "AttackDLC", "client-session.json"));
        } catch (Exception ignored) {
        }
    }

    private void launchMinecraft(
            MinecraftClient client,
            Label status
    ) {
        Thread.startVirtualThread(() -> {
            try {
                statusSafe(status, "Проверяем установленную сборку...");

                // запоминаем модификацию — её покажет Discord в первой строке
                rememberModification(client.name());

                // клиент живёт вне mods/ — переносим, если он ещё там
                ClientStore.moveModsOut(client.installDir(),
                        githubClient.remoteModFiles(client.repository()));

                // файл входа для клиента: uid, ник, токены, ключ
                writeClientSession();

                Process process = minecraftLauncher.launch(
                        client.installDir(),
                        activeUsername()
                );

                javafx.application.Platform.runLater(() ->
                        status.setText("Minecraft запущен · " + client.version())
                );

                process.onExit().thenRun(() -> {
                    int exitCode = process.exitValue();
                    String output = minecraftLauncher.getLastOutput();

                    javafx.application.Platform.runLater(() -> {
                        if (exitCode == 0) {
                            status.setText("Minecraft завершил работу.");
                        } else {
                            String text =
                                    "Minecraft завершился с кодом " + exitCode;

                            if (output != null && !output.isBlank()) {
                                text += "\n\nПоследний вывод Minecraft:\n" + output;
                            }

                            status.setText(
                                    "Ошибка Minecraft · код " + exitCode
                            );

                            showError(
                                    "Minecraft завершился с ошибкой",
                                    new RuntimeException(text)
                            );
                        }
                    });
                });

            } catch (Exception ex) {
                javafx.application.Platform.runLater(() -> {
                    status.setText(
                            "Ошибка запуска: " + safeError(ex)
                    );
                    showError(
                            "Не удалось запустить Minecraft",
                            ex
                    );
                });
            }
        });
    }

    private void showProfile() {
        String shown = activeUsername();
        discordUpdate("Профиль", "Смотрит профиль · " + shown);
        VBox page = page(
                "ПРОФИЛЬ",
                shown,
                accountStore.session() == null
                        ? "Создай ник с паролем — он подставится в Minecraft при запуске."
                        : "Аккаунт привязан: ник подставляется в Minecraft при запуске."
        );
        page.setId("profile-page");

        page.getChildren().add(accountCard());

        GridPane grid = new GridPane();
        grid.setHgap(32);
        grid.setVgap(15);
        grid.setPadding(new Insets(18));
        grid.getStyleClass().add("profile-card");
        grid.setMaxWidth(Double.MAX_VALUE);

        addInfo(
                grid,
                0,
                "Имя пользователя Windows",
                username
        );

        addInfo(
                grid,
                1,
                "Папка Minecraft",
                gameDirectory().toString()
        );

        addInfo(
                grid,
                2,
                "Статус",
                "Launcher готов к работе"
        );

        page.getChildren().add(grid);
        swap(page);
    }

    /** Ник из аккаунта, если вошёл; иначе имя пользователя Windows. */
    private String activeUsername() {
        String nick = accountStore.nick();
        return nick == null ? username : nick;
    }

    private void updateBarUser() {
        if (barUserLabel != null) {
            barUserLabel.setText(activeUsername());
        }
    }

    /**
     * Карточка аккаунта: вход по нику и паролю
     * или профиль с ником и выходом.
     */
    private Node accountCard() {
        VBox card = new VBox(12);
        card.getStyleClass().add("settings-section");
        card.setPadding(new Insets(18));

        var session = accountStore.session();

        if (session == null) {
            Label title = new Label("Аккаунт");
            title.getStyleClass().add("section-title");

            Label hint = new Label(
                    "Создай ник с паролем или войди в существующий: "
                            + "ник уникален, сервер не пропустит дубль.");
            hint.getStyleClass().add("muted");
            hint.setWrapText(true);

            TextField nickField = new TextField();
            nickField.setPromptText("Ник (3–16 символов: латиница, цифры, «_»)");
            nickField.setMaxWidth(280);

            PasswordField passwordField = new PasswordField();
            passwordField.setPromptText("Пароль (минимум 6 символов)");
            passwordField.setMaxWidth(280);
            passwordField.setOnAction(e -> startPasswordLogin(
                    nickField.getText(), passwordField.getText(), false));

            Label error = new Label(accountError == null ? "" : accountError);
            error.setStyle("-fx-text-fill:#ff8b8b; -fx-font-size:12px;");
            if (accountError == null || accountError.isBlank()) {
                error.setManaged(false);
                error.setVisible(false);
            }

            Label progress = new Label(accountStatus == null ? "" : accountStatus);
            progress.getStyleClass().add("muted");
            if (accountStatus == null || accountStatus.isBlank()) {
                progress.setManaged(false);
                progress.setVisible(false);
            }

            Button signIn = new Button("Войти");
            signIn.getStyleClass().add("primary-button");
            signIn.setDisable(accountBusy);
            signIn.setOnAction(e -> startPasswordLogin(
                    nickField.getText(), passwordField.getText(), false));

            Button signUp = new Button("Создать аккаунт");
            signUp.getStyleClass().add("secondary-button");
            signUp.setDisable(accountBusy);
            signUp.setOnAction(e -> startPasswordLogin(
                    nickField.getText(), passwordField.getText(), true));

            card.getChildren().addAll(title, hint, nickField, passwordField,
                    new HBox(10, signIn, signUp), error, progress);
            return card;
        }

        // вошёл
        Label title = new Label("Аккаунт");
        title.getStyleClass().add("section-title");

        Label nick = new Label(session.nick() == null || session.nick().isBlank()
                ? "ник ещё не задан" : session.nick());
        nick.getStyleClass().add("card-title");

        var profile = accountStore.profile();
        String role = profile == null ? "free" : profile.displayRole();

        Label email = new Label(session.email() == null ? "" : session.email());
        email.getStyleClass().add("muted");

        VBox who = new VBox(2, nick, email);
        who.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(who, Priority.ALWAYS);

        /*
         * Плашка роли: у чёрного минимализма акцент один — белый.
         * У ютубера (YT) — сплошная белая заливка с тёмной надписью,
         * у остальных — контурная плашка серого тона.
         */
        Label rolePill = new Label("Роль: " + ("yt".equals(role) ? "YT" : role));
        rolePill.setFont(javafx.scene.text.Font.font(11));
        if ("yt".equals(role)) {
            rolePill.setStyle("-fx-padding:3 10;"
                    + " -fx-background-radius:99; -fx-border-radius:99;"
                    + " -fx-background-color:#ffffff;"
                    + " -fx-border-color:#ffffff;"
                    + " -fx-text-fill:#0a0a0a; -fx-font-size:11px;"
                    + " -fx-font-weight:bold;");
        } else if ("owner".equals(role)) {
            rolePill.setStyle("-fx-padding:3 10;"
                    + " -fx-background-radius:99; -fx-border-radius:99;"
                    + " -fx-background-color:transparent;"
                    + " -fx-border-color:#ffffff;"
                    + " -fx-text-fill:#ffffff; -fx-font-size:11px;");
        } else {
            rolePill.setStyle("-fx-padding:3 10;"
                    + " -fx-background-radius:99; -fx-border-radius:99;"
                    + " -fx-background-color:#141414;"
                    + " -fx-border-color:#262626;"
                    + " -fx-text-fill:#a3a3a3; -fx-font-size:11px;");
        }

        HBox head = new HBox(12, who, rolePill);
        head.setAlignment(Pos.CENTER_LEFT);
        if (session.photoUrl() != null && !session.photoUrl().isBlank()) {
            ImageView avatar = new ImageView(
                    new Image(session.photoUrl(), true));
            avatar.setFitWidth(40);
            avatar.setFitHeight(40);
            avatar.setPreserveRatio(true);
            avatar.setClip(new Circle(20, 20, 20));
            head.getChildren().add(0, avatar);
        }

        Label note = new Label("При запуске Minecraft подставляется ник: "
                + activeUsername());
        note.getStyleClass().add("muted");

        Button signOut = new Button("Выйти");
        signOut.getStyleClass().add("ghost-button");
        signOut.setOnAction(e -> {
            accountStore.clear();
            // клиент сразу теряет доступ: файла входа больше нет
            deleteClientSession();
            updateBarUser();
            refreshRpcIdentity();
            showProfile();
        });

        HBox actions = new HBox(10, signOut);
        actions.setAlignment(Pos.CENTER_LEFT);

        card.getChildren().addAll(title, head, note,
                accessBlock(profile), sessionBlock(), actions);

        // панель управления ключами — только для владельца
        if (profile != null && "owner".equals(profile.roleOrFree())) {
            card.getChildren().add(ownerPanel());
        }
        return card;
    }

    /* ================= панель владельца: ключи ================= */

    private boolean ownerBusy;
    private String ownerStatus;
    private String ownerError;
    /** Список ключей уже загружен в текущем рендере панели. */
    private boolean ownerKeysLoaded;

    /**
     * Панель управления ключами (видна только роли owner):
     * создание ключа с произвольным сроком и привязкой к нику,
     * список ключей с освобождением/удалением и смена привязки ПК.
     */
    private Node ownerPanel() {
        VBox panel = new VBox(12);
        panel.getStyleClass().add("settings-section");

        Label title = new Label("Управление ключами");
        title.getStyleClass().add("section-title");

        Label hint = new Label(
                "Создай ключ: укажи срок, свой текст (или пусто — сгенерируем) "
                        + "и ник, за которым он закрепится. Ключ работает "
                        + "один раз и привязывается к ПК человека.");
        hint.getStyleClass().add("muted");
        hint.setWrapText(true);

        /* --- строка создания --- */
        TextField keyField = new TextField();
        keyField.setPromptText("Текст ключа (пусто — сгенерировать)");
        keyField.setPrefWidth(230);

        String[] daysOptions = {"7 дней", "30 дней", "60 дней", "90 дней", "120 дней", "Навсегда"};
        String[] selectedDays = {"30 дней"};
        HBox daysPills = new HBox(6);
        daysPills.setAlignment(Pos.CENTER_LEFT);
        for (String opt : daysOptions) {
            Button pill = new Button(opt);
            pill.getStyleClass().add("duration-pill");
            pill.setOnAction(e -> {
                selectedDays[0] = opt;
                daysPills.getChildren().forEach(n -> {
                    if (n instanceof Button b) b.getStyleClass().remove("duration-pill-selected");
                });
                pill.getStyleClass().add("duration-pill-selected");
            });
            if (opt.equals(selectedDays[0])) pill.getStyleClass().add("duration-pill-selected");
            daysPills.getChildren().add(pill);
        }

        TextField nickField = new TextField();
        nickField.setPromptText("Ник аккаунта (необязательно)");
        nickField.setPrefWidth(190);

        Button create = new Button("Создать ключ");
        create.getStyleClass().add("primary-button");
        create.setDisable(ownerBusy);
        create.setOnAction(e -> startCreateKey(
                keyField.getText(), selectedDays[0], nickField.getText(),
                keyField, nickField));

        HBox createRow = new HBox(8, keyField, daysPills, nickField, create);
        createRow.setAlignment(Pos.CENTER_LEFT);

        /* --- статус --- */
        Label status = new Label(ownerStatus == null ? "" : ownerStatus);
        status.getStyleClass().add("muted");
        if (ownerStatus == null || ownerStatus.isBlank()) {
            status.setManaged(false);
            status.setVisible(false);
        }

        Label error = new Label(ownerError == null ? "" : ownerError);
        error.setStyle("-fx-text-fill:#ff8b8b; -fx-font-size:12px;");
        if (ownerError == null || ownerError.isBlank()) {
            error.setManaged(false);
            error.setVisible(false);
        }

        /* --- список ключей --- */
        VBox list = new VBox(6);
        list.getChildren().add(new Label("Загрузка ключей…"));
        Button refresh = new Button("Обновить список");
        refresh.getStyleClass().add("ghost-button");
        refresh.setDisable(ownerBusy);
        refresh.setOnAction(e -> {
            ownerKeysLoaded = false;
            startLoadKeys(list);
        });

        panel.getChildren().addAll(title, hint, createRow,
                status, error, new HBox(10, refresh), list);

        // список подгружается при первом открытии; после действий
        // с ключами флаг сбрасывается и список обновится сам
        if (!ownerKeysLoaded && !ownerBusy) {
            ownerKeysLoaded = true;
            Thread.startVirtualThread(() -> startLoadKeys(list));
        }
        return panel;
    }

    /** Фон: создание ключа. */
    private void startCreateKey(String keyText, String daysLabel,
                                String nick, TextField keyField,
                                TextField nickField) {
        if (ownerBusy) {
            return;
        }
        var session = accountStore.session();
        if (session == null) {
            return;
        }
        int days = switch (daysLabel == null ? "" : daysLabel) {
            case "7 дней" -> 7;
            case "60 дней" -> 60;
            case "90 дней" -> 90;
            case "120 дней" -> 120;
            case "Навсегда" -> 0;
            default -> 30;
        };
        ownerBusy = true;
        ownerStatus = "Создаём ключ…";
        ownerError = null;
        showProfile();

        Thread.startVirtualThread(() -> {
            try {
                String uid = "";
                if (nick != null && !nick.isBlank()) {
                    uid = authService.findUidByNick(session, nick);
                }
                String created = authService.createKey(
                        session, keyText, days, uid);
                ownerStatus = "Ключ создан: " + created
                        + (uid.isBlank() ? "" : " · привязан к " + nick.trim());
                javafx.application.Platform.runLater(() -> {
                    keyField.clear();
                    nickField.clear();
                });
            } catch (Exception ex) {
                ownerStatus = null;
                ownerError = safeError(ex);
            } finally {
                ownerBusy = false;
                ownerKeysLoaded = false;   // после создания список изменился
                javafx.application.Platform.runLater(this::showProfile);
            }
        });
    }

    /** Фон: загрузка списка ключей в контейнер. */
    private void startLoadKeys(VBox list) {
        var session = accountStore.session();
        if (session == null) {
            return;
        }
        ownerBusy = true;
        java.util.List<usa.kitikat.account.AuthService.KeyInfo> keys;
        try {
            keys = authService.listKeys(session);
        } catch (Exception ex) {
            ownerBusy = false;
            String text = "Не загрузились: " + safeError(ex);
            javafx.application.Platform.runLater(() -> list.getChildren()
                    .setAll(new Label(text)));
            return;
        }
        ownerBusy = false;

        javafx.application.Platform.runLater(() -> {
            list.getChildren().clear();
            if (keys.isEmpty()) {
                list.getChildren().add(
                        new Label("Ключей пока нет — создай первый выше."));
                return;
            }
            for (var info : keys) {
                list.getChildren().add(keyRow(info));
            }
        });
    }

    /** Строка одного ключа в списке владельца. */
    private Node keyRow(usa.kitikat.account.AuthService.KeyInfo info) {
        String term = info.days() <= 0 ? "навсегда" : info.days() + " дн.";
        String state = info.free() ? "свободен" : "активирован";
        Label key = new Label(info.key() + "  ·  " + term + "  ·  " + state);
        key.getStyleClass().add("card-version");

        Button release = new Button("Освободить");
        release.getStyleClass().add("ghost-button");
        release.setDisable(ownerBusy || info.free());
        release.setOnAction(e -> startKeyAction(
                () -> authService.releaseKey(accountStore.session(), info.key()),
                "Ключ " + info.key() + " освобождён"));

        Button delete = new Button("Удалить");
        delete.getStyleClass().add("ghost-button");
        delete.setDisable(ownerBusy);
        delete.setOnAction(e -> startKeyAction(
                () -> authService.deleteKey(accountStore.session(), info.key()),
                "Ключ " + info.key() + " удалён"));

        HBox row = new HBox(10, key, release, delete);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    /** Фоновое действие владельца с ключом (освободить/удалить). */
    private void startKeyAction(KeyAction action, String success) {
        if (ownerBusy) {
            return;
        }
        ownerBusy = true;
        ownerStatus = "Выполняем…";
        ownerError = null;
        showProfile();

        Thread.startVirtualThread(() -> {
            try {
                action.run();
                ownerStatus = success;
            } catch (Exception ex) {
                ownerStatus = null;
                ownerError = safeError(ex);
            } finally {
                ownerBusy = false;
                ownerKeysLoaded = false;   // после удаления/освобождения — обновить
                javafx.application.Platform.runLater(this::showProfile);
            }
        });
    }

    /** Одно действие с ключом (для фонового запуска). */
    @FunctionalInterface
    private interface KeyAction {
        void run() throws Exception;
    }

    /** Сброс привязки ПК ключа: снимает hwid, ключ можно активировать на другом ПК. */
    private void startResetHwid(String key) {
        var session = accountStore.session();
        if (session == null || key == null || key.isBlank()) return;
        ownerBusy = true;
        ownerStatus = "Сбрасываем привязку ПК…";
        ownerError = null;
        showProfile();

        Thread.startVirtualThread(() -> {
            try {
                authService.resetKeyHwid(session, key);
                ownerStatus = "Привязка ПК сброшена — ключ можно активировать на другом компьютере.";
            } catch (Exception ex) {
                ownerStatus = null;
                ownerError = safeError(ex);
            } finally {
                ownerBusy = false;
                javafx.application.Platform.runLater(this::showProfile);
            }
        });
    }

    /**
     * Блок «доступ к DLC»: тарифы подписки, статус ключа
     * или поле ввода ключа покупки.
     */
    private Node accessBlock(usa.kitikat.account.AccountProfile profile) {
        VBox block = new VBox(8);

        Label keyTitle = new Label("Подписка Attack DLC");
        keyTitle.getStyleClass().add("section-title");

        if (profile != null && profile.hasKey()) {
            // срок берём из ключа (единственный источник истины)
            var sub = accountStore.subscription();
            int left = sub == null ? 0 : sub.daysLeft();
            String status;
            if (sub == null) {
                status = "Ключ активирован · уточняем срок…";
            } else if (left < 0) {
                status = "Подписка активна — навсегда.";
            } else if (left == 0) {
                status = "Срок подписки истёк — активируй новый ключ.";
            } else {
                status = "Подписка активна · осталось дней: " + left;
            }
            Label ok = new Label(status);
            ok.getStyleClass().add("muted");

            Label key = new Label("Ключ: " + profile.activatedKey());
            key.getStyleClass().add("card-version");

            Label hwid = new Label("Привязка ПК: "
                    + usa.kitikat.account.Hwid.shortForm());
            hwid.getStyleClass().add("muted");

            // Кнопка сброса привязки ПК (для владельца)
            Button resetHwidBtn = new Button("Сбросить привязку ПК");
            resetHwidBtn.getStyleClass().add("ghost-button");
            resetHwidBtn.setTooltip(new javafx.scene.control.Tooltip(
                    "Снимает привязку ключа к этому ПК. "
                            + "После сброса ключ можно активировать на другом компьютере."));
            resetHwidBtn.setOnAction(e -> startResetHwid(profile.activatedKey()));

            block.getChildren().addAll(keyTitle, ok, key, hwid, resetHwidBtn);
            return block;
        }

        // ютубер: доступ уже выдан ролью, поле активации ключа не нужно
        if (profile != null && profile.isYoutuber()) {
            Label granted = new Label(
                    "Доступ выдан по роли YouTuber — ключ активировать не нужно.");
            granted.getStyleClass().add("muted");
            Label hwid = new Label("Привязка ПК: "
                    + usa.kitikat.account.Hwid.shortForm());
            hwid.getStyleClass().add("card-version");
            block.getChildren().addAll(keyTitle, granted, hwid);
            return block;
        }

        Label hint = new Label(
                "Покупай ключ на FunPay и активируй его здесь — "
                        + "доступ привяжется к аккаунту и к этому ПК. "
                        + "Ключ работает только один раз.");
        hint.getStyleClass().add("muted");
        hint.setWrapText(true);

        // тарифы: 7/30/60/90/120 дней и «навсегда»
        FlowPane plans = new FlowPane(8, 8);
        for (String plan : new String[]{"7 дней", "30 дней", "60 дней",
                "90 дней", "120 дней", "Навсегда"}) {
            Label pill = new Label(plan);
            pill.getStyleClass().add("plan-pill");
            plans.getChildren().add(pill);
        }

        TextField keyField = new TextField();
        keyField.setPromptText("Ключ активации (например ATK-XXXX-…)");
        keyField.setMaxWidth(320);

        Label error = new Label(keyError == null ? "" : keyError);
        error.setStyle("-fx-text-fill:#ff8b8b; -fx-font-size:12px;");
        if (keyError == null || keyError.isBlank()) {
            error.setManaged(false);
            error.setVisible(false);
        }

        Label progress = new Label(keyStatus == null ? "" : keyStatus);
        progress.getStyleClass().add("muted");
        if (keyStatus == null || keyStatus.isBlank()) {
            progress.setManaged(false);
            progress.setVisible(false);
        }

        Button activate = new Button("Активировать");
        activate.getStyleClass().add("secondary-button");
        activate.setDisable(keyBusy);
        activate.setOnAction(e -> startKeyActivation(keyField.getText()));

        block.getChildren().addAll(keyTitle, hint, plans, keyField,
                new HBox(10, activate), error, progress);
        return block;
    }

    /** Вход или регистрация по нику и паролю (запрос идёт в фоне). */
    private void startPasswordLogin(String nick, String password, boolean register) {
        if (accountBusy) {
            return;
        }
        if (!authService.configured()) {
            showError("Вход не настроен",
                    new IllegalStateException(
                            "нет настроек аккаунтов — заполни "
                                    + usa.kitikat.account.AccountConfig.externalPath()));
            return;
        }
        accountBusy = true;
        accountStatus = register ? "Создаём аккаунт…" : "Входим…";
        accountError = null;
        showProfile();

        Thread.startVirtualThread(() -> {
            try {
                var session = register
                        ? authService.register(nick, password)
                        : authService.login(nick, password);
                var loaded = authService.fetchProfile(session);
                if (loaded == null) {
                    authService.ensureProfile(session);
                    loaded = authService.fetchProfile(session);
                }

                /*
                 * Привязка к ПК живёт в ключе: он записывается один раз
                 * при активации и дальше не меняется. С другого
                 * компьютера в этот аккаунт не пускаем.
                 */
                if (loaded != null && loaded.hasKey()) {
                    var key = authService.fetchKeyInfo(session, loaded.activatedKey());
                    if (key != null
                            && key.hwid() != null
                            && !key.hwid().isBlank()
                            && !key.hwid().equalsIgnoreCase(
                                    usa.kitikat.account.Hwid.get())) {
                        throw new usa.kitikat.account.AuthException(
                                "Аккаунт привязан к другому компьютеру — "
                                        + "напиши владельцу, чтобы перенёс привязку");
                    }
                }

                var profile = loaded != null
                        ? loaded
                        : new usa.kitikat.account.AccountProfile(
                                session.uid(), session.nick(), session.email(),
                                "free", "", System.currentTimeMillis(), "",
                                usa.kitikat.account.Hwid.get());
                // старый профиль без привязки — привязываем этот ПК
                if (profile.hwid() == null || profile.hwid().isBlank()) {
                    authService.bindHwid(session);
                }
                /*
                 * Новая сессия перезаписывает активную — прошлые входы
                 * на других ПК закрываются. Если записать не удалось,
                 * вход всё равно оставляем: просто авто-выхода не будет.
                 */
                String sid;
                try {
                    sid = authService.startSession(session, profile);
                } catch (Exception ignored) {
                    sid = null;
                }
                final String newSessionId = sid;
                javafx.application.Platform.runLater(() -> {
                    accountBusy = false;
                    accountStatus = null;
                    accountError = null;
                    accountStore.save(session);
                    accountStore.saveProfile(profile);
                    if (newSessionId != null) {
                        accountStore.saveSessionId(newSessionId);
                    }
                    updateBarUser();
                    refreshRpcIdentity();
                    // файл входа для клиента — сразу после входа
                    writeClientSession();
                    showProfile();
                });
            } catch (Exception ex) {
                javafx.application.Platform.runLater(() -> {
                    accountBusy = false;
                    accountStatus = null;
                    accountError = ex instanceof usa.kitikat.account.AuthException
                            ? ex.getMessage() : "Не удалось выполнить вход";
                    showProfile();
                });
            }
        });
    }

    /** Активация ключа покупки (запрос идёт в фоне). */
    private void startKeyActivation(String key) {
        if (keyBusy || accountBusy) {
            return;
        }
        if (accountStore.session() == null) {
            return;
        }
        keyBusy = true;
        keyStatus = "Активируем ключ…";
        keyError = null;
        showProfile();

        var session = accountStore.session();
        Thread.startVirtualThread(() -> {
            try {
                var updated = authService.activateKey(session, key);
                // срок берём из самого ключа — в профиле его нет
                var sub = fetchSubscription(session, updated);
                javafx.application.Platform.runLater(() -> {
                    keyBusy = false;
                    keyStatus = null;
                    keyError = null;
                    if (updated != null) {
                        accountStore.saveProfile(updated);
                    }
                    if (sub != null) {
                        accountStore.saveSubscription(sub);
                    }
                    updateBarUser();
                    refreshRpcIdentity();
                    showProfile();
                });
            } catch (Exception ex) {
                javafx.application.Platform.runLater(() -> {
                    keyBusy = false;
                    keyStatus = null;
                    keyError = ex instanceof usa.kitikat.account.AuthException
                            ? ex.getMessage()
                            : "Не удалось активировать ключ";
                    showProfile();
                });
            }
        });
    }

    private void addInfo(
            GridPane grid,
            int row,
            String label,
            String value
    ) {
        Label left = new Label(label);
        left.getStyleClass().add("muted");

        Label right = new Label(
                value == null ? "—" : value
        );
        right.getStyleClass().add("value");

        grid.add(left, 0, row);
        grid.add(right, 1, row);
    }

    private void showSettings() {
        discordUpdate("Настраивает Attack", "Настройки лаунчера");
        VBox page = page("НАСТРОЙКИ", "Персонализация", "Масштаб, прозрачность, анимации, кнопки окна, диск установки и Discord Rich Presence.");

        VBox windowSize = new VBox(12);
        windowSize.getStyleClass().add("settings-section");
        Label sizeTitle = new Label("Размер окна");
        sizeTitle.getStyleClass().add("section-title");
        Label sizeSub = new Label(
                "Масштаб интерфейса: окно, шрифты (включая боковое меню) и все элементы."
        );
        sizeSub.getStyleClass().add("muted");

        HBox scaleRow = new HBox(10);
        double currentScale = preferences.getDouble("uiScale", 1.0);
        for (double candidate : new double[]{0.5, 0.75, 1.0}) {
            Button option = new Button(Math.round(candidate * 100) + "%");
            boolean selected = Math.abs(candidate - currentScale) < 0.001;
            option.getStyleClass().add(selected ? "primary-button" : "secondary-button");
            option.setOnAction(e -> {
                preferences.putDouble("uiScale", candidate);
                if (scaleApplier != null) scaleApplier.accept(candidate);
                showSettings();
            });
            scaleRow.getChildren().add(option);
        }

        windowSize.setMaxWidth(Double.MAX_VALUE);
        windowSize.getChildren().addAll(sizeTitle, sizeSub, scaleRow);

        /* ---------- Прозрачность окна ---------- */
        double currentOpacity = preferences.getDouble("windowOpacity", 0.90);
        Label opacityValue = new Label(Math.round(currentOpacity * 100) + "%");
        opacityValue.getStyleClass().add("value");
        Slider opacity = new Slider(0.55, 1.0, currentOpacity);
        opacity.setShowTickLabels(false);
        opacity.setShowTickMarks(false);
        HBox opacityRow = new HBox(14, opacityValue, opacity);
        opacityRow.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(opacity, Priority.ALWAYS);
        opacity.valueProperty().addListener((obs, oldValue, value) -> {
            setWindowOpacity(value.doubleValue());
            opacityValue.setText(Math.round(value.doubleValue() * 100) + "%");
        });
        VBox transparency = settingsSection(
                "Прозрачность окна",
                "От 55% до 100%. Окно остаётся чёрным — меняется только плотность заливки.",
                opacityRow
        );

        /* ---------- Иконки меню ---------- */
        CheckBox anime = new CheckBox("Аниме-пак — милые иконки в меню вместо штриховых");
        anime.getStyleClass().add("settings-check");
        anime.setSelected(animeIcons);
        anime.selectedProperty().addListener((obs, oldValue, value) -> {
            animeIcons = value;
            preferences.putBoolean("animeIcons", value);
            refreshNavIcons();
        });

        VBox styleSection = settingsSection(
                "Иконки меню",
                "Аниме-пак меняет иконки бокового меню на цветные. По умолчанию — штриховые.",
                anime
        );


        /* ---------- Интерфейс ---------- */
        CheckBox anim = new CheckBox("Анимации интерфейса (смена страниц и появление карточек)");
        anim.getStyleClass().add("settings-check");
        anim.setSelected(animationsEnabled);
        anim.selectedProperty().addListener((obs, oldValue, value) -> {
            animationsEnabled = value;
            preferences.putBoolean("uiAnimations", value);
        });
        /* ---------- Скорость анимаций ---------- */
        double currentAnimSpeed = preferences.getDouble("animSpeed", 1.0);
        HBox animSpeedRow = new HBox(10);
        var speedOptions = new java.util.LinkedHashMap<String, Double>();
        speedOptions.put("Быстро", 0.6);
        speedOptions.put("Обычно", 1.0);
        speedOptions.put("Медленно", 1.6);
        for (var option : speedOptions.entrySet()) {
            Button speedButton = new Button(option.getKey());
            boolean selected = Math.abs(option.getValue() - currentAnimSpeed) < 0.001;
            speedButton.getStyleClass().add(selected ? "primary-button" : "secondary-button");
            speedButton.setOnAction(e -> {
                preferences.putDouble("animSpeed", option.getValue());
                animSpeed = option.getValue();
                showSettings();
            });
            animSpeedRow.getChildren().add(speedButton);
        }

        VBox interfaceSection = settingsSection(
                "Интерфейс",
                "Отключите анимации или задайте их скорость — переходы между страницами и появление карточек.",
                anim,
                animSpeedRow
        );
        VBox discord = new VBox(10);
        discord.getStyleClass().add("settings-section");
        Label discordTitle = new Label("Discord Rich Presence"); discordTitle.getStyleClass().add("section-title");
        Label discordSub = new Label(
                "Показывает, что вы делаете: смотрите игру, настраиваете лаунчер или во что-то играете."
        );
        discordSub.getStyleClass().add("muted");
        CheckBox rpc = new CheckBox("Включить Rich Presence");
        rpc.getStyleClass().add("settings-check"); rpc.setSelected(discordRpcEnabled);
        rpc.selectedProperty().addListener((obs, oldValue, value) -> {
            discordRpcEnabled = value; preferences.putBoolean("discordRpc", value);
            if (value) refreshRpcIdentity();
            else discordUpdate(null, null);
        });
        Label rpcStatus = new Label(runningGame != null
                ? "Сейчас в системе: " + runningGame
                : "Запущенных игр в системе нет");
        rpcStatus.getStyleClass().add("status");
        rpcStatusLabel = rpcStatus;
        discord.setMaxWidth(Double.MAX_VALUE);
        discord.getChildren().addAll(discordTitle, discordSub, rpc, rpcStatus);

        /* ---------- Диск установки ---------- */
        java.util.List<String> drives = installDrives();
        String selectedDrive = installDrive();
        if (!drives.contains(selectedDrive)) {
            drives.add(0, selectedDrive);
        }
        HBox drivePills = new HBox(6);
        drivePills.setAlignment(Pos.CENTER_LEFT);
        for (String drive : drives) {
            Button pill = new Button(drive);
            pill.getStyleClass().add("duration-pill");
            pill.setPrefSize(50, 28);
            pill.setOnAction(e -> {
                preferences.put("installDrive", drive);
                showSettings();
            });
            if (drive.equals(selectedDrive)) pill.getStyleClass().add("duration-pill-selected");
            drivePills.getChildren().add(pill);
        }
        Label driveSpaceLabel = new Label(driveSpace(selectedDrive));
        driveSpaceLabel.getStyleClass().add("value");
        HBox driveRow = new HBox(12, drivePills, driveSpaceLabel);
        driveRow.setAlignment(Pos.CENTER_LEFT);
        VBox driveSection = settingsSection(
                "Диск установки",
                null,
                driveRow
        );

        /* ---------- Кнопки окна ---------- */
        VBox windowControlsSection = settingsSection(
                "Кнопки окна",
                "Те же два варианта, что были при первоначальной настройке: "
                        + "точки как в macOS или обычные кнопки под дизайн лаунчера. "
                        + "Шапка окна меняется сразу.",
                windowControlsChoice()
        );

        /* ---------- Обновления ---------- */
        Button checkUpdates = new Button("Проверить обновления");
        checkUpdates.getStyleClass().add("secondary-button");
        Label updateStatus = new Label("Текущая версия: " + UpdateChecker.currentVersion());
        updateStatus.getStyleClass().add("status");
        HBox updateRow = new HBox(12, checkUpdates, updateStatus);
        updateRow.setAlignment(Pos.CENTER_LEFT);
        updateRow.getStyleClass().add("updates-row");
        checkUpdates.setOnAction(e -> {
            String current = UpdateChecker.currentVersion();
            if ("dev".equals(current)) {
                updateStatus.setText("Dev-сборка без версии — обновляться нечем.");
                return;
            }
            checkUpdates.setDisable(true);
            statusSafe(updateStatus, "Проверяем обновления лаунчера...");
            Thread thread = new Thread(() -> {
                try {
                    UpdateChecker.Info info = UpdateChecker.fetch(UpdateChecker.versionUrl());
                    if (UpdateChecker.isNewer(current, info.version())) {
                        statusSafe(updateStatus, "Доступна версия " + info.version()
                                + " — качаем в фоне");
                        logUpdate("доступна версия " + info.version()
                                + ": " + (info.notes() == null ? "" : info.notes()));
                        javafx.application.Platform.runLater(() ->
                                startBackgroundUpdate(info));
                    } else {
                        statusSafe(updateStatus, "У вас последняя версия (" + current + ")");
                    }
                } catch (Throwable ex) {
                    statusSafe(updateStatus, "Ошибка: " + safeError(ex));
                } finally {
                    javafx.application.Platform.runLater(() -> checkUpdates.setDisable(false));
                }
            }, "update-check");
            thread.setDaemon(true);
            thread.start();
        });
        VBox updatesSection = settingsSection(
                "Обновления",
                null,
                updateRow
        );

        page.getChildren().addAll(
                windowSize, transparency, styleSection,
                interfaceSection, windowControlsSection, driveSection,
                updatesSection, discord
        );
        swap(page);
    }
    /** Единый блок настроек: заголовок + описание + содержимое. */
    private VBox settingsSection(String title, String subtitle, Node... children) {
        VBox box = new VBox(12);
        box.getStyleClass().add("settings-section");
        box.setMaxWidth(Double.MAX_VALUE);

        Label heading = new Label(title);
        heading.getStyleClass().add("section-title");

        box.getChildren().add(heading);
        if (subtitle != null && !subtitle.isBlank()) {
            Label hint = new Label(subtitle);
            hint.getStyleClass().add("muted");
            hint.setWrapText(true);
            box.getChildren().add(hint);
        }
        box.getChildren().addAll(children);
        return box;
    }

    private StackPane wallpaperChoice(int index) {
        StackPane box = new StackPane();
        box.getStyleClass().add("wallpaper-choice");
        box.setPrefSize(150, 84);
        box.setClip(roundedClip(150, 84, 10));
        ImageView image = new ImageView(new Image(getClass().getResourceAsStream("/assets/wallpapers/wallpaper" + index + ".jpg")));
        image.setFitWidth(150); image.setFitHeight(84); image.setPreserveRatio(false); image.setSmooth(true);
        Rectangle clip = new Rectangle(150, 84);
        clip.setArcWidth(18); clip.setArcHeight(18);
        image.setClip(clip);
        image.getStyleClass().add("wallpaper-thumb");
        box.getChildren().add(image);
        box.setOnMouseClicked(e -> loadWallpaper("/assets/wallpapers/wallpaper" + index + ".jpg"));
        return box;
    }

    private void chooseWallpaper() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Выберите фон");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Изображения", "*.png", "*.jpg", "*.jpeg", "*.webp"));
        var file = chooser.showOpenDialog(stage);
        if (file != null) loadWallpaper(file.getAbsolutePath());
    }

    /* ================= обновление клиентов ================= */

    /**
     * Проверка обновлений при запуске: только установленные сборки, только
     * папка mods/, сверка по git blob sha — один запрос на клиента, кэш на
     * 5 минут, чтобы перезапуски не упирались в лимит GitHub API.
     * Если есть что обновить — показываем диалог с выбором.
     */
    private void checkClientUpdatesOnStart() {
        long now = System.currentTimeMillis();
        if (now - lastClientCheck < 5 * 60_000L) {
            return;
        }
        lastClientCheck = now;

        Thread thread = new Thread(() -> {
            try {
                Thread.sleep(1200L);   // даём окну отрисоваться
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }

            java.util.List<MinecraftClient> installed = new java.util.ArrayList<>();
            for (MinecraftClient client : minecraftClients()) {
                if (githubClient.isInstalled(client.installDir())) installed.add(client);
            }
            StringBuilder names = new StringBuilder();
            for (MinecraftClient client : installed) {
                if (names.length() > 0) names.append(", ");
                names.append(client.name());
            }
            logUpdate("проверка клиентов: установлено " + installed.size()
                    + (names.length() == 0 ? "" : " (" + names + ")"));

            /*
             * Клиенты проверяются одновременно: локальные хэши плюс запрос
             * к GitHub дают 8–20 секунд на каждого, а по очереди диалог
             * пришёл бы почти через минуту после запуска.
             */
            java.util.Set<String> checkedOk = java.util.concurrent.ConcurrentHashMap.newKeySet();
            java.util.Map<String, GitHubClient.ModsDiff> found =
                    new java.util.concurrent.ConcurrentHashMap<>();
            try (var pool = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
                java.util.List<java.util.concurrent.Future<?>> futures = new java.util.ArrayList<>();
                for (MinecraftClient client : installed) {
                    futures.add(pool.submit(() -> {
                        try {
                            GitHubClient.ModsDiff diff = githubClient.checkMods(
                                    client.installDir(), client.repository(),
                                    clientAddonFiles());
                            checkedOk.add(client.name());
                            if (!diff.isEmpty()) found.put(client.name(), diff);
                            logUpdate("сверка " + client.name() + ": докачать "
                                    + diff.download().size() + ", удалить " + diff.remove().size());
                        } catch (Throwable ex) {
                            // нет сети или лимит запросов — проверим в следующий запуск
                            logUpdate("сверка " + client.name() + " не удалась", ex);
                        }
                        return null;
                    }));
                }
                for (java.util.concurrent.Future<?> future : futures) {
                    try {
                        future.get();
                    } catch (Exception ignored) {
                        // сама ошибка уже внутри задачи
                    }
                }
            }

            java.util.List<MinecraftClient> outdated = new java.util.ArrayList<>();
            for (MinecraftClient client : installed) {
                // клиент, который не удалось проверить, оставляем как есть
                if (!checkedOk.contains(client.name())) continue;

                if (found.containsKey(client.name())) {
                    clientUpdates.put(client.name(), found.get(client.name()));
                    outdated.add(client);
                } else {
                    clientUpdates.remove(client.name());
                }
                setMarker(client.name(), found.containsKey(client.name()));
            }
            logUpdate("проверка завершена: проверено " + checkedOk.size() + " из "
                    + installed.size() + ", с обновлениями " + found.keySet()
                    + ", к показу " + outdated.size());

            javafx.application.Platform.runLater(() -> {
                refreshClientCards();
                if (!outdated.isEmpty()) {
                    showClientUpdatesDialog(outdated);
                }
            });
        }, "client-update-check");
        thread.setDaemon(true);
        thread.start();
    }

    /** Метка «Есть обновление» кладётся в настройки — видна уже при следующем старте. */
    private void setMarker(String name, boolean hasUpdate) {
        boolean changed = hasUpdate ? updateMarkers.add(name) : updateMarkers.remove(name);
        if (changed) {
            preferences.put("clientUpdateMarkers", String.join(",", updateMarkers));
        }
    }

    /** Перерисовывает карточки клиентов, если страница клиентов сейчас открыта. */
    private void refreshClientCards() {
        if (clientsGrid != null && clientsGrid.getScene() != null) {
            showMinecraftClients();
        }
    }

    /**
     * Журнал проверки обновлений: файл update-check.log в папке NemesisLauncher
     * внутри LOCALAPPDATA. Без него сбой сети выглядит так, будто обновлений
     * просто нет.
     */
    private void logUpdate(String message) {
        try {
            String base = System.getenv("LOCALAPPDATA");
            Path dir = Path.of(base == null || base.isBlank()
                    ? System.getProperty("java.io.tmpdir", ".")
                    : base, "AttackDLC");
            Files.createDirectories(dir);
            String line = java.time.LocalDateTime.now().withNano(0)
                    + "  " + message + System.lineSeparator();
            Files.write(dir.resolve("update-check.log"),
                    line.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                    java.nio.file.StandardOpenOption.CREATE,
                    java.nio.file.StandardOpenOption.APPEND);
        } catch (Exception ignored) {
            // журнал вспомогательный: его отсутствие не должно ломать лаунчер
        }
    }

    private void logUpdate(String message, Throwable error) {
        if (error == null) {
            logUpdate(message);   // сообщение без ошибки — так и пишем
            return;
        }
        StringBuilder text = new StringBuilder(message).append(": ")
                .append(error.getClass().getName());
        if (error.getMessage() != null) text.append(" — ").append(error.getMessage());
        Throwable cause = error.getCause();
        if (cause != null) {
            text.append(" (причина: ").append(cause.getClass().getName());
            if (cause.getMessage() != null) text.append(" — ").append(cause.getMessage());
            text.append(')');
        }
        logUpdate(text.toString());
    }

    /**
     * Диалог «доступны обновления»: список с количеством файлов и три кнопки —
     * обновить выбранное, обновить всё, позже.
     */
    private void showClientUpdatesDialog(java.util.List<MinecraftClient> outdated) {
        if (clientUpdateOverlay != null) {
            return;   // уже показан
        }

        StackPane overlay = new StackPane();
        overlay.getStyleClass().add("onboarding-overlay");
        overlay.setManaged(false);
        overlay.setLayoutX(0);
        overlay.setLayoutY(0);
        clientUpdateOverlay = overlay;

        VBox card = new VBox(14);
        card.getStyleClass().add("onboarding-card");
        card.setPadding(new Insets(26, 28, 28, 28));
        card.setMaxWidth(560);
        card.setMaxHeight(560);
        card.setAlignment(Pos.CENTER_LEFT);

        Label eyebrow = new Label("ОБНОВЛЕНИЕ КЛИЕНТОВ");
        eyebrow.getStyleClass().add("eyebrow");

        Label title = new Label("Найдены новые версии");
        title.getStyleClass().add("title-large");

        Label subtitle = new Label(
                "Лаунчер сверил файлы сборки: изменённые заменятся, "
                        + "пропавшие восстановятся. Остальные файлы не трогаются.");
        subtitle.getStyleClass().add("muted");
        subtitle.setWrapText(true);

        java.util.List<MinecraftClient> selectable = new java.util.ArrayList<>();
        java.util.List<CheckBox> boxes = new java.util.ArrayList<>();
        VBox list = new VBox(6);
        for (MinecraftClient client : outdated) {
            GitHubClient.ModsDiff diff = clientUpdates.get(client.name());
            if (diff == null) continue;

            StringBuilder details = new StringBuilder();
            details.append(diff.download().size()).append(" файл(ов) заменится");
            if (!diff.remove().isEmpty()) {
                details.append(", ").append(diff.remove().size()).append(" удалится");
            }

            CheckBox box = new CheckBox(client.name() + " — " + details);
            box.getStyleClass().add("settings-check");
            box.setSelected(true);

            selectable.add(client);
            boxes.add(box);
            list.getChildren().add(box);
        }

        ProgressBar bar = new ProgressBar(0);
        bar.setMaxWidth(Double.MAX_VALUE);
        bar.setPrefHeight(8);
        bar.setVisible(false);
        bar.setManaged(false);

        Label status = new Label("Отметьте клиенты и нажмите «Обновить».");
        status.getStyleClass().add("status");
        status.setWrapText(true);

        Button applySelected = new Button("Обновить выбранное");
        applySelected.getStyleClass().add("primary-button");

        Button applyAll = new Button("Обновить всё");
        applyAll.getStyleClass().add("secondary-button");

        Button later = new Button("Позже");
        later.getStyleClass().add("ghost-button");
        later.setOnAction(e -> closeClientUpdates());

        Button[] lock = {applySelected, applyAll, later};

        applySelected.setOnAction(e -> {
            java.util.List<MinecraftClient> picked = new java.util.ArrayList<>();
            for (int i = 0; i < boxes.size(); i++) {
                if (boxes.get(i).isSelected()) picked.add(selectable.get(i));
            }
            if (picked.isEmpty()) {
                status.setText("Отметьте хотя бы одного клиента.");
                return;
            }
            runClientUpdate(picked, bar, status, lock);
        });

        applyAll.setOnAction(e -> runClientUpdate(selectable, bar, status, lock));

        HBox actions = new HBox(9, applySelected, applyAll, later);
        actions.setAlignment(Pos.CENTER_LEFT);

        card.getChildren().addAll(
                eyebrow, title, subtitle, list, bar, status, actions);
        overlay.getChildren().add(card);
        getChildren().add(overlay);

        Runnable resize = () -> overlay.resize(getWidth(), getHeight());
        widthProperty().addListener((o, a, b) -> resize.run());
        heightProperty().addListener((o, a, b) -> resize.run());
        resize.run();

        if (animationsEnabled) {
            overlay.setOpacity(0);
            FadeTransition fade = new FadeTransition(
                    Duration.millis(280 * animSpeed), overlay);
            fade.setToValue(1);
            fade.play();
        }
    }

    /** Обновляет отмеченные клиенты по очереди: сверка → удаление → докачка. */
    private void runClientUpdate(java.util.List<MinecraftClient> clients,
                                 ProgressBar bar,
                                 Label status,
                                 Button[] lock) {
        for (Button button : lock) button.setDisable(true);
        bar.setVisible(true);
        bar.setManaged(true);
        bar.setProgress(0);

        Thread thread = new Thread(() -> {
            int ok = 0;
            int failed = 0;

            for (int i = 0; i < clients.size(); i++) {
                MinecraftClient client = clients.get(i);
                int index = i;
                statusSafe(status, client.name() + ": подготовка…");
                try {
                    githubClient.updateMods(
                            client.installDir(),
                            client.repository(),
                            value -> javafx.application.Platform.runLater(() ->
                                    bar.setProgress((index + value) / (double) clients.size())),
                            text -> statusSafe(status, client.name() + " · " + text),
                            clientAddonFiles());
                    clientUpdates.remove(client.name());
                    setMarker(client.name(), false);
                    ok++;
                } catch (Throwable ex) {
                    failed++;
                    statusSafe(status, client.name() + " — ошибка: " + safeError(ex));
                }
            }

            int okFinal = ok;
            int failedFinal = failed;
            javafx.application.Platform.runLater(() -> {
                bar.setVisible(false);
                bar.setManaged(false);

                if (failedFinal > 0) {
                    status.setText("Обновлено: " + okFinal + ", с ошибкой: "
                            + failedFinal + ". Попробуйте ещё раз.");
                    for (Button button : lock) button.setDisable(false);
                    return;
                }

                status.setText("Обновлено клиентов: " + okFinal + ".");
                closeClientUpdates();
            });
        }, "client-updater");
        thread.setDaemon(true);
        thread.start();
    }

    /** Свежая сверка mods/ с репозиторием по кнопке — всегда без кэша. */
    private void checkClientUpdate(MinecraftClient client, Button button,
                                   ProgressBar progress, Label status) {
        if (!githubClient.isInstalled(client.installDir())) {
            checkManifest(client.archiveUrl(), button, progress, status);
            return;
        }

        button.setDisable(true);
        progress.setVisible(true);
        progress.setProgress(-1);
        status.setText("Сверяем файлы сборки...");
status.setText("Сверяем файлы сборки...");
        Thread.startVirtualThread(() -> {
            try {
                GitHubClient.ModsDiff diff = githubClient.checkMods(
                        client.installDir(), client.repository(),
                        clientAddonFiles());

                if (diff.isEmpty()) {
                    clientUpdates.remove(client.name());
                    setMarker(client.name(), false);
                    javafx.application.Platform.runLater(() ->
                            status.setText("Обновлений нет — установлена последняя версия."));
                } else {
                    clientUpdates.put(client.name(), diff);
                    setMarker(client.name(), true);
                    java.util.List<MinecraftClient> list = new java.util.ArrayList<>();
                    list.add(client);
                    javafx.application.Platform.runLater(() -> {
                        status.setText("Доступно обновление: " + diff.download().size()
                                + " файл(ов)" + (diff.remove().isEmpty()
                                ? ""
                                : ", удалится " + diff.remove().size()));
                        showClientUpdatesDialog(list);
                    });
                }
            } catch (Throwable ex) {
                javafx.application.Platform.runLater(() ->
                        status.setText("Не удалось проверить: " + safeError(ex)));
            } finally {
                javafx.application.Platform.runLater(() -> {
                    progress.setVisible(false);
                    button.setDisable(false);
                });
            }
        });
    }

    private void closeClientUpdates() {
        if (clientUpdateOverlay != null) {
            getChildren().remove(clientUpdateOverlay);
            clientUpdateOverlay = null;
        }
    }

    /* ================= обновление лаунчера ================= */

    /** Док внизу окна: фоновое скачивание обновления с прогрессом. */
    private HBox updateDock;
    private ProgressBar updateDockBar;
    private Label updateDockTitle;
    private Label updateDockLabel;
    private Button updateDockAction;
    /** Идёт ли сейчас фоновое скачивание (чтобы не начать второе). */
    private boolean backgroundUpdateRunning;
    /** Скачанный архив обновления, ждущий применения. */
    private Path pendingUpdateZip;
    /** Версия, которую скачиваем. */
    private String pendingUpdateVersion = "";

    /** Папка, где лежит скачанное обновление. */
    private static Path updateDir() {
        String local = System.getenv("LOCALAPPDATA");
        if (local == null || local.isBlank()) {
            local = System.getProperty("user.home", ".");
        }
        return Path.of(local, "AttackDLC", "launcher-update");
    }

    /**
     * Миграция: если старый лаунчер сохранял данные в {@code NemesisLauncher},
     * переносим их в {@code AttackDLC}. Работает один раз — после переноса
     * старая папка остаётся, но новый лаунчер уже не читает из неё.
     */
    private static void migrateFromOldLauncher() {
        try {
            String local = System.getenv("LOCALAPPDATA");
            if (local == null || local.isBlank()) return;
            Path oldDir = Path.of(local, "NemesisLauncher");
            Path newDir = Path.of(local, "AttackDLC");
            if (!Files.isDirectory(oldDir)) return;
            if (Files.isDirectory(newDir)) return; // уже мигрировано
            Files.createDirectories(newDir);
            for (var child : Files.list(oldDir).toList()) {
                // пропускаем системные файлы и логи обновлений
                String name = child.getFileName().toString();
                if (name.endsWith(".log") || name.endsWith(".logs")) continue;
                if ("update".equals(name)) continue; // свежее обновление — своё
                if ("plugins".equals(name)) continue; // плагины не трогаем
                try {
                    Files.move(child, newDir.resolve(name),
                            java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                } catch (Exception ignored) {}
            }
            System.out.println("[AttackLauncher] migrated data from NemesisLauncher → AttackDLC");
        } catch (Throwable t) {
            System.err.println("[AttackLauncher] migrate failed: " + t.getMessage());
        }
    }

    /** Файл-флаг: если он есть, обновление перезапустит лаунчер. */
    private static Path restartFlag() {
        return updateDir().resolve("restart.flag");
    }

    /**
     * Фоновое скачивание обновления: пользователь продолжает работать,
     * прогресс виден в доке снизу.
     */
    private void startBackgroundUpdate(UpdateChecker.Info info) {
        if (backgroundUpdateRunning) {
            return;
        }
        Path appDir = UpdateChecker.appDirectory();
        if (appDir == null) {
            // запуск из исходников — обновляться нечем
            return;
        }
        backgroundUpdateRunning = true;
        pendingUpdateVersion = info.version();

        showUpdateDock("Обновление " + info.version(), "Скачивание…", 0.0);

        Thread thread = new Thread(() -> {
            try {
                Path dir = Files.createDirectories(updateDir());
                Path zip = dir.resolve("pending.zip");
                UpdateChecker.download(
                        info.url(),
                        zip,
                        value -> javafx.application.Platform.runLater(() -> {
                            double percent = Math.max(0.0, Math.min(1.0, value));
                            updateDockBar.setProgress(percent);
                            if (value >= 0.0) {
                                updateDockLabel.setText(String.format(
                                        java.util.Locale.ROOT,
                                        "Скачивание %d%%", Math.round(percent * 100)));
                            }
                        }),
                        text -> javafx.application.Platform.runLater(() ->
                                updateDockLabel.setText(text))
                );
                pendingUpdateZip = zip;
                javafx.application.Platform.runLater(() -> {
                    updateDockBar.setProgress(1.0);
                    updateDockLabel.setText("Обновление " + info.version()
                            + " готово — перезапустите лаунчер");
                    updateDockAction.setText("Перезапустить");
                    updateDockAction.setVisible(true);
                    updateDockAction.setDisable(false);
                });
            } catch (Throwable ex) {
                javafx.application.Platform.runLater(() -> {
                    backgroundUpdateRunning = false;
                    updateDockLabel.setText("Не удалось скачать обновление: "
                            + safeError(ex));
                    updateDockAction.setVisible(false);
                });
            }
        }, "update-download");
        thread.setDaemon(true);
        thread.start();
    }

    /** Показывает док обновления снизу. */
    private void showUpdateDock(String title, String status, double progress) {
        if (updateDock == null) {
            updateDock = new HBox(12);
            updateDock.getStyleClass().add("update-dock");
            updateDock.setAlignment(Pos.CENTER_LEFT);
            updateDock.setVisible(false);

            Label titleLabel = new Label();
            titleLabel.getStyleClass().add("update-dock-title");
            updateDockTitle = titleLabel;

            updateDockBar = new ProgressBar(0);
            updateDockBar.setPrefWidth(180);
            updateDockBar.setPrefHeight(6);
            updateDockBar.setMaxHeight(6);

            updateDockLabel = new Label();
            updateDockLabel.getStyleClass().add("update-dock-status");

            updateDockAction = new Button("Перезапустить");
            updateDockAction.getStyleClass().add("primary-button");
            updateDockAction.setVisible(false);
            updateDockAction.setOnAction(e -> applyPendingUpdate());

            Button hide = new Button("×");
            hide.getStyleClass().add("ghost-button");
            hide.setOnAction(e -> updateDock.setVisible(false));

            updateDock.getChildren().addAll(titleLabel, updateDockBar,
                    updateDockLabel, updateDockAction, hide);
            content.getChildren().add(updateDock);
            StackPane.setAlignment(updateDock, Pos.BOTTOM_CENTER);
            StackPane.setMargin(updateDock, new Insets(0, 0, 14, 0));
        }
        updateDockTitle.setText(title);
        updateDockLabel.setText(status);
        updateDockBar.setProgress(progress);
        updateDock.setVisible(true);
    }

    /** Применяет скачанное обновление и перезапускает лаунчер. */
    private void applyPendingUpdate() {
        Path appDir = UpdateChecker.appDirectory();
        if (appDir == null || pendingUpdateZip == null) {
            return;
        }
        try {
            Path flag = restartFlag();
            Files.createDirectories(flag.getParent());
            Files.writeString(flag, "restart");
            UpdateChecker.spawnUpdate(appDir, pendingUpdateZip, flag);
            stage.close();
        } catch (Throwable ex) {
            showError("Не удалось обновить лаунчер", ex);
        }
    }

    /** Проверка обновления при запуске: тихо, если скачивать нечего. */
    private void checkUpdatesOnStart() {
        if ("dev".equals(UpdateChecker.currentVersion())) {
            return;   // dev-сборка — версии нет, обновляться нечем
        }

        // уже скачанное обновление применяем при выходе из лаунчера
        applyPendingOnExit();

        Thread thread = new Thread(() -> {
            try {
                UpdateChecker.Info info = UpdateChecker.fetch(UpdateChecker.versionUrl());
                if (UpdateChecker.isNewer(UpdateChecker.currentVersion(), info.version())) {
                    javafx.application.Platform.runLater(() ->
                            startBackgroundUpdate(info));
                }
            } catch (Throwable ignored) {
                // нет сети или JSON не доступен — лаунчер обязан работать и без этого
            }
        }, "update-check");
        thread.setDaemon(true);
        thread.start();
    }

    /**
     * Если обновление уже скачано — ставим его скриптом: файлы подменятся,
     * когда лаунчер закроется. Перезапускаться при этом не будем.
     */
    private void applyPendingOnExit() {
        try {
            Path zip = updateDir().resolve("pending.zip");
            Path appDir = UpdateChecker.appDirectory();
            if (appDir == null || !Files.isRegularFile(zip)
                    || Files.size(zip) <= 0) {
                return;
            }
            UpdateChecker.spawnUpdate(appDir, zip, null);
            logUpdate("скачанное обновление будет установлено при выходе");
        } catch (Throwable ignored) {
        }
    }

    /** Закрывает старый оверлей обновления (оставлен для совместимости). */
    private void closeUpdate() {
        if (updateOverlay != null) {
            getChildren().remove(updateOverlay);
            updateOverlay = null;
        }
    }

    /* ================= первоначальная настройка ================= */

    /**
     * Экран первоначальной настройки — показывается один раз, пока
     * пользователь не нажал «Продолжить». Пока здесь только один выбор:
     * стиль кнопок окна (точки как в macOS или обычные, под дизайн).
     */
    private void showOnboarding() {
        StackPane overlay = new StackPane();
        overlay.getStyleClass().add("onboarding-overlay");
        // лежит поверх всего окна вручную: BorderPane сам не раскладывает
        // детей без роли (top/left/center/…), размер задаём сами
        overlay.setManaged(false);
        overlay.setLayoutX(0);
        overlay.setLayoutY(0);

        VBox card = new VBox(14);
        card.getStyleClass().add("onboarding-card");
        card.setPadding(new Insets(26, 28, 28, 28));
        card.setMaxWidth(600);
        // карточка обнимает содержимое, а не тянется на всю высоту окна
        card.setMaxHeight(480);
        card.setAlignment(Pos.CENTER_LEFT);

        Label eyebrow = new Label("ПЕРВОНАЧАЛЬНАЯ НАСТРОЙКА");
        eyebrow.getStyleClass().add("eyebrow");

        Label title = new Label("Настройка окна");
        title.getStyleClass().add("title-large");

        Label subtitle = new Label(
                "Один шаг: выберите, как выглядят кнопки окна. " +
                "Всё остальное — в настройках, потом."
        );
        subtitle.getStyleClass().add("muted");
        subtitle.setWrapText(true);

        Label question = new Label("Стиль кнопок окна");
        question.getStyleClass().add("section-title");

        HBox row = windowControlsChoice();

        Button apply = new Button("Продолжить");
        apply.getStyleClass().add("primary-button");
        apply.setMaxWidth(Double.MAX_VALUE);
        apply.setOnAction(e -> {
            // сам стиль уже сохранён в windowControlsChoice()
            preferences.putBoolean("onboardingDone", true);
            getChildren().remove(overlay);
        });

        card.getChildren().addAll(eyebrow, title, subtitle, question, row, apply);
        overlay.getChildren().add(card);

        getChildren().add(overlay);

        Runnable resize = () -> overlay.resize(getWidth(), getHeight());
        widthProperty().addListener((o, a, b) -> resize.run());
        heightProperty().addListener((o, a, b) -> resize.run());
        resize.run();

        if (animationsEnabled) {
            overlay.setOpacity(0);
            FadeTransition fade = new FadeTransition(
                    Duration.millis(280 * animSpeed), overlay);
            fade.setToValue(1);
            fade.play();
        }
    }

    /**
     * Два варианта стиля кнопок окна — общий блок для первоначальной
     * настройки и раздела «Настройки». Клик сразу сохраняет выбор в
     * {@code windowControls} и перестраивает шапку окна.
     *
     * <p>Первый вариант — «точки как в macOS», второй — обычные кнопки.</p>
     */
    private HBox windowControlsChoice() {
        String[] choice = {preferences.get("windowControls", "dots")};
        VBox[] options = new VBox[2];

        Runnable repaint = () -> {
            boolean dots = "dots".equals(choice[0]);
            markSelected(options[0], dots);
            markSelected(options[1], !dots);
        };

        java.util.function.Consumer<String> pick = value -> {
            choice[0] = value;
            preferences.put("windowControls", value);
            buildTitlebar();
            repaint.run();
        };

        options[0] = choiceOption(
                "Точки как в macOS",
                "Три кружка слева: закрыть, свернуть, развернуть.",
                previewControls(true),
                () -> pick.accept("dots")
        );

        options[1] = choiceOption(
                "Обычные кнопки",
                "Знаки справа: свернуть, развернуть, закрыть — в стиле лаунчера.",
                previewControls(false),
                () -> pick.accept("classic")
        );

        repaint.run();

        HBox row = new HBox(12, options[0], options[1]);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    private VBox choiceOption(
            String title,
            String description,
            Node preview,
            Runnable onSelect
    ) {
        VBox box = new VBox(9);
        box.getStyleClass().addAll("client-card", "choice-option");
        box.setPadding(new Insets(15));
        box.setPrefWidth(252);
        box.setMaxWidth(252);
        box.setAlignment(Pos.CENTER_LEFT);
        box.setOnMouseClicked(e -> onSelect.run());

        Label t = new Label(title);
        t.getStyleClass().add("card-title");

        Label d = new Label(description);
        d.getStyleClass().add("card-version");
        d.setWrapText(true);

        box.getChildren().addAll(preview, t, d);
        return box;
    }

    private void markSelected(VBox card, boolean selected) {
        String cls = "selected";
        if (selected) {
            if (!card.getStyleClass().contains(cls)) {
                card.getStyleClass().add(cls);
            }
        } else {
            card.getStyleClass().remove(cls);
        }
    }

    /** Мини-превью кнопок окна: настоящие узлы, но некликабельные. */
    private HBox previewControls(boolean dotsStyle) {
        HBox row = new HBox(7);
        row.setAlignment(Pos.CENTER_LEFT);

        String[] order = dotsStyle
                ? new String[]{"close", "min", "max"}
                : new String[]{"min", "max", "close"};

        for (String kind : order) {
            Button button = dotsStyle ? dot(kind) : winButton(kind);
            button.setMouseTransparent(true);
            row.getChildren().add(button);
        }
        return row;
    }

    private VBox page(
            String eyebrow,
            String title,
            String subtitle
    ) {
        VBox page = new VBox(12);
        page.setPadding(new Insets(24, 28, 28, 28));
        page.setMaxWidth(Double.MAX_VALUE);
        page.setFillWidth(true);

        Label e = new Label(eyebrow);
        e.getStyleClass().add("eyebrow");

        Label t = new Label(title);
        t.getStyleClass().add("title-large");

        Label s = new Label(subtitle);
        s.getStyleClass().add("muted");

        page.getChildren().addAll(e, t, s);
        return page;
    }

    private void statusSafe(Label status, String text) {
        javafx.application.Platform.runLater(
                () -> status.setText(text)
        );
    }

    private String safeError(Throwable ex) {
        String message = ex.getMessage();
        return message == null || message.isBlank()
                ? ex.getClass().getSimpleName()
                : message;
    }

    private void showError(
            String title,
            Throwable error
    ) {
        if (errorOverlay != null) {
            getChildren().remove(errorOverlay);
            errorOverlay = null;
        }

        StackPane overlay = new StackPane();
        overlay.getStyleClass().add("onboarding-overlay");
        overlay.setManaged(false);
        overlay.setLayoutX(0);
        overlay.setLayoutY(0);
        errorOverlay = overlay;

        VBox card = new VBox(14);
        card.getStyleClass().add("onboarding-card");
        card.setPadding(new Insets(26, 28, 28, 28));
        card.setMaxWidth(540);
        card.setMaxHeight(javafx.scene.layout.Region.USE_PREF_SIZE);
        card.setAlignment(Pos.CENTER_LEFT);

        Label eyebrow = new Label("ОШИБКА");
        eyebrow.getStyleClass().add("error-eyebrow");

        Label heading = new Label(title);
        heading.getStyleClass().add("title-large");
        heading.setWrapText(true);

        Label message = new Label(safeError(error));
        message.getStyleClass().add("error-message");
        message.setWrapText(true);

        Node details = message;
        // длинный вывод Minecraft прокручивается, а не растягивает карточку
        if (safeError(error).length() > 300 || safeError(error).contains("\n")) {
            ScrollPane scroll = new ScrollPane(message);
            scroll.getStyleClass().add("error-scroll");
            scroll.setFitToWidth(true);
            scroll.setPannable(true);
            scroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
            scroll.setMaxHeight(220);
            details = scroll;
        }

        Button copy = new Button("Копировать текст");
        copy.getStyleClass().add("ghost-button");
        copy.setOnAction(e -> {
            javafx.scene.input.ClipboardContent content =
                    new javafx.scene.input.ClipboardContent();
            content.putString(title + "\n\n" + safeError(error));
            javafx.scene.input.Clipboard.getSystemClipboard()
                    .setContent(content);
            copy.setText("Скопировано");
        });

        Button close = new Button("Закрыть");
        close.getStyleClass().add("primary-button");
        close.setOnAction(e -> {
            getChildren().remove(overlay);
            errorOverlay = null;
        });

        HBox buttons = new HBox(10, copy, close);
        buttons.setAlignment(Pos.CENTER_RIGHT);

        card.getChildren().addAll(eyebrow, heading, details, buttons);
        overlay.getChildren().add(card);
        getChildren().add(overlay);

        Runnable resize = () -> overlay.resize(getWidth(), getHeight());
        widthProperty().addListener((o, a, b) -> resize.run());
        heightProperty().addListener((o, a, b) -> resize.run());
        resize.run();

        if (animationsEnabled) {
            overlay.setOpacity(0);
            FadeTransition fade = new FadeTransition(
                    Duration.millis(240 * animSpeed), overlay);
            fade.setToValue(1);
            fade.play();
        }
    }

    private ImageView image(
            String path,
            double width,
            double height
    ) {
        return new ImageView(
                new Image(
                        getClass().getResourceAsStream(path),
                        width,
                        height,
                        true,
                        true
                )
        );
    }

    /**
     * Версия клиента и ссылки-кнопки для Discord RPC
     * (задаются один раз при подключении Rich Presence).
     */
    void applyDiscordConfig(DiscordRPC rpc) {
        if (rpc == null) {
            return;
        }
        rpc.configure(
                "attackdlc",
                "Attack DLC",
                "Покупка ключа",
                accountConfig.funpayUrl(),
                "Сайт",
                accountConfig.siteUrl());
    }

    /**
     * Разово подтягивает профиль при старте: роль и ключ могли
     * измениться с прошлого запуска (например, владелец выдал доступ).
     */
    private void refreshProfileAsync() {
        var session = accountStore.session();
        if (session == null || !authService.configured()) {
            return;
        }
        Thread.startVirtualThread(() -> {
            try {
                var profile = authService.fetchProfile(session);
                if (profile == null) {
                    return;
                }
                var sub = fetchSubscription(session, profile);
                javafx.application.Platform.runLater(() -> {
                    applyFreshProfile(profile);
                    if (sub != null) {
                        accountStore.saveSubscription(sub);
                    }
                });
            } catch (Exception ignored) {
                // нет связи — просто остаёмся на сохранённом профиле
            }
        });
    }

    /**
     * Подписка по ключу аккаунта: срок и момент активации.
     * null — ключа нет или база недоступна.
     */
    private usa.kitikat.account.KeySubscription fetchSubscription(
            usa.kitikat.account.AuthService.Session session,
            usa.kitikat.account.AccountProfile profile) {
        if (profile == null || !profile.hasKey()) {
            return null;
        }
        try {
            var sub = authService.fetchKeyInfo(session, profile.activatedKey());
            if (sub == null || !sub.ownedBy(session.uid())) {
                return null;
            }
            return sub;
        } catch (Exception ex) {
            return null;
        }
    }

    /**
     * Применяет свежеподтянутый профиль: если аккаунт вошли с другого ПК,
     * локальную сессию закрываем.
     */
    private void applyFreshProfile(
            usa.kitikat.account.AccountProfile profile) {
        if (!profile.sessionMatches(accountStore.sessionId())) {
            accountStore.clear();
            deleteClientSession();
            updateBarUser();
            accountError = "Сессия закрыта: вход выполнен с другого устройства";
            refreshRpcIdentity();
            return;
        }
        accountStore.saveProfile(profile);
        // файл входа всегда соответствует текущему аккаунту лаунчера
        writeClientSession();
        updateBarUser();
        refreshRpcIdentity();
    }

    /** Открыта ли прямо сейчас страница профиля. */
    private boolean isProfilePage() {
        return pageLayer.getChildren().stream()
                .anyMatch(node -> "profile-page".equals(node.getId()));
    }

    /**
     * Периодически сверяет локальную сессию с сервером:
     * как только её сменили на другом ПК, здесь выходим.
     */
    private void startSessionWatcher() {
        Thread thread = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    Thread.sleep(60_000);
                } catch (InterruptedException ex) {
                    return;
                }
                var session = accountStore.session();
                if (session == null || !authService.configured()) {
                    continue;
                }
                try {
                    var profile = authService.fetchProfile(session);
                    if (profile == null) {
                        continue;
                    }
                    javafx.application.Platform.runLater(() -> {
                        if (accountStore.session() == null) {
                            return;
                        }
                        boolean onProfile = isProfilePage();
                        applyFreshProfile(profile);
                        if (onProfile) {
                            showProfile();
                        }
                    });
                } catch (Exception ignored) {
                }

                /*
                 * Файл входа для клиента обновляем каждый цикл: он должен
                 * всегда соответствовать текущему аккаунту лаунчера, иначе
                 * клиент покажет чужой ник и чужую лицензию.
                 */
                writeClientSession();
            }
        }, "attack-session-watcher");
        thread.setDaemon(true);
        sessionWatcher = thread;
        thread.start();
    }

    /**
     * Выйти со всех остальных устройств: ротируем сессию.
     * Своя остаётся, прошлые входы закрываются.
     */
    private void rotateSession() {
        var session = accountStore.session();
        if (session == null || sessionBusy) {
            return;
        }
        sessionBusy = true;
        sessionStatus = "Закрываем другие входы…";
        sessionError = null;
        showProfile();

        Thread.startVirtualThread(() -> {
            try {
                var sid = authService.startSession(
                        session, accountStore.profile());
                javafx.application.Platform.runLater(() -> {
                    sessionBusy = false;
                    sessionStatus = null;
                    sessionError = null;
                    accountStore.saveSessionId(sid);
                    showProfile();
                });
            } catch (Exception ex) {
                javafx.application.Platform.runLater(() -> {
                    sessionBusy = false;
                    sessionStatus = null;
                    sessionError = ex instanceof usa.kitikat.account.AuthException
                            ? ex.getMessage()
                            : "Не удалось закрыть другие входы";
                    showProfile();
                });
            }
        });
    }

    /**
     * Блок управления входами: один активный ПК, ротация сессии.
     */
    private Node sessionBlock() {
        VBox block = new VBox(8);

        Label title = new Label("Устройства");
        title.getStyleClass().add("section-title");

        Label hint = new Label(
                "Вход выполняется только на одном ПК: как только ты "
                        + "зайдёшь с другого, прошлый компьютер закроется сам.");
        hint.getStyleClass().add("muted");
        hint.setWrapText(true);

        Button rotate = new Button("Выйти со всех устройок");
        rotate.getStyleClass().add("ghost-button");
        rotate.setDisable(sessionBusy);
        rotate.setOnAction(e -> rotateSession());

        Label error = new Label(sessionError == null ? "" : sessionError);
        error.setStyle("-fx-text-fill:#ff8b8b; -fx-font-size:12px;");
        if (sessionError == null || sessionError.isBlank()) {
            error.setManaged(false);
            error.setVisible(false);
        }

        Label progress = new Label(sessionStatus == null ? "" : sessionStatus);
        progress.getStyleClass().add("muted");
        if (sessionStatus == null || sessionStatus.isBlank()) {
            progress.setManaged(false);
            progress.setVisible(false);
        }

        block.getChildren().addAll(title, hint, rotate, error, progress);
        return block;
    }

    /**
     * Identity в Discord RPC: версия клиента в первой строке,
     * роль и ник — во второй. Не трогаем RPC, пока запущена игра.
     */
    private void refreshRpcIdentity() {
        if (!discordRpcEnabled) {
            return;
        }
        /*
         * Первая строка — название модификации, с которой играют.
         * Если ещё ни разу не запускали — просто лаунчер.
         */
        String details = lastModification.isBlank()
                ? "Attack Launcher"
                : lastModification;

        var profile = accountStore.profile();
        String nick = accountStore.nick();
        String state;
        if (nick == null || nick.isBlank()) {
            state = "Не в аккаунте";
        } else {
            String role = profile == null ? "Free" : profile.roleTitle();
            state = "Role: " + role + " · " + nick;
        }
        discordUpdate(details, state);
    }

    /**
     * Запоминает, с какой модификацией играют: её название попадает
     * в первую строку Discord RPC и переживает перезапуск лаунчера.
     */
    private void rememberModification(String name) {
        if (name == null || name.isBlank()) {
            return;
        }
        lastModification = name;
        preferences.put("lastModification", name);
        // обновляем presence, если игра ещё не считается запущенной
        if (runningGame == null) {
            refreshRpcIdentity();
        }
    }

    /**
     * details == null &amp;&amp; state == null → выключить Rich Presence
     * (используется, когда пользователь снимает галочку Discord RPC).
     *
     * <p>Пока в системе запущена игра — активность показывает именно её,
     * состояние страницы просто запоминается и вернётся после закрытия игры.</p>
     */
    private void discordUpdate(
            String details,
            String state
    ) {
        try {
            if (discordUpdateCallback == null) {
                return;
            }
            if (details == null && state == null) {
                discordUpdateCallback.accept(null, null);
                return;
            }
            if (!discordRpcEnabled) {
                return;
            }
            lastDetails = details;
            lastState = state;
            if (runningGame != null) {
                /*
                 * Игра запущена: в первой строке — игра,
                 * во второй — то, что человек делает в лаунчере.
                 */
                discordUpdateCallback.accept("Играет: " + runningGame, state);
                return;
            }
            discordUpdateCallback.accept(details, state);
        } catch (Exception ignored) {
        }
    }

    /* ================= персонализация ================= */

    /** Прозрачность всего окна. Никогда не доходит до 100% — окно всегда чуть прозрачное. */
    private void setWindowOpacity(double value) {
        double clamped = Math.max(0.5, Math.min(1.0, value));
        preferences.putDouble("windowOpacity", clamped);
        try {
            stage.setOpacity(clamped);
        } catch (Exception ignored) {
        }
    }

    /** Сгенерировать override-CSS акцента и подключить его после styles.css. */
    private void applyAccentTheme() {
        try {
            String hex = null;
            if (accentFromWallpaper) {
                if (accentFromWallpaperHex == null) {
                    accentFromWallpaperHex = pickWallpaperAccent();
                }
                hex = accentFromWallpaperHex;
            }
            if (hex == null || !hex.matches("#[0-9a-fA-F]{6}")) {
                hex = AccentTheme.byId(accentId).hex();
            }
            String url = AccentTheme.write(
                    hex,
                    preferences.getDouble("panelDensity", 1.0)
            );
            javafx.application.Platform.runLater(() -> attachAccentCss(url));
        } catch (Exception ignored) {
        }
    }

    /**
     * Имена файлов аддонов клиента, поставленных раньше.
     *
     * <p>Сверка mods/ с репозиторием клиента считает любой файл не из
     * репозитория «пропавшим» и предлагает удалить — эти имена попадают
     * в игнор-список, чтобы обновление клиента такие файлы не трогало.</p>
     */
    private java.util.Set<String> clientAddonFiles() {
        String raw = preferences.get("clientAddonFiles", "");
        java.util.Set<String> out = new java.util.LinkedHashSet<>();
        for (String part : raw.split(",")) {
            if (!part.isBlank()) {
                out.add(part.trim());
            }
        }
        return out;
    }

    private void attachAccentCss(String url) {
        var scene = getScene();
        if (scene != null) {
            if (accentCssUrl != null) {
                scene.getStylesheets().remove(accentCssUrl);
            }
            if (!scene.getStylesheets().contains(url)) {
                scene.getStylesheets().add(url);
            }
            scene.getRoot().applyCss();
        }
        accentCssUrl = url;
    }

    /** Живой фон: медленное «дыхание» обоев. */
    private void startWallpaperPulse() {
        stopWallpaperPulse();
        if (!liveWallpaper) {
            wallpaperView.setScaleX(1);
            wallpaperView.setScaleY(1);
            return;
        }
        double amount = Math.max(0.0, Math.min(0.15, preferences.getDouble("pulseAmount", 0.06)));
        double seconds = Math.max(3.0, Math.min(30.0, preferences.getDouble("pulseSeconds", 10)));
        double target = 1.0 + amount;

        wallpaperPulse = new Timeline(new KeyFrame(
                Duration.seconds(seconds),
                new KeyValue(wallpaperView.scaleXProperty(), target, Interpolator.EASE_BOTH),
                new KeyValue(wallpaperView.scaleYProperty(), target, Interpolator.EASE_BOTH)
        ));
        wallpaperPulse.setCycleCount(Animation.INDEFINITE);
        wallpaperPulse.setAutoReverse(true);
        wallpaperPulse.play();
    }

    private void stopWallpaperPulse() {
        if (wallpaperPulse != null) {
            wallpaperPulse.stop();
            wallpaperPulse = null;
        }
    }

    /* ================= активность в Discord ================= */

    private void startGameWatcher() {
        try {
            gameWatcher = new GameActivityWatcher(
                    game -> javafx.application.Platform.runLater(() -> onRunningGame(game))
            );
        } catch (Throwable ignored) {
            gameWatcher = null;
        }
    }

    private void onRunningGame(String game) {
        runningGame = game;
        if (game != null) {
            // первая строка — игра, вторая — текущее дело в лаунчере
            discordUpdateNow("Играет: " + game, lastState);
        } else if (lastDetails != null) {
            discordUpdateNow(lastDetails, lastState);
        }
        if (rpcStatusLabel != null) {
            rpcStatusLabel.setText(game != null
                    ? "Сейчас в системе: " + game
                    : "Запущенных игр в системе нет");
        }
    }

    private void discordUpdateNow(String details, String state) {
        try {
            if (discordUpdateCallback == null || !discordRpcEnabled) {
                return;
            }
            discordUpdateCallback.accept(details, state);
        } catch (Exception ignored) {
        }
    }

    /** Остановить фоновые задачи (вызывается при выходе). */
    public void shutdown() {
        stopSidebarAnims();
        stopWallpaperPulse();
        if (sessionWatcher != null) {
            sessionWatcher.interrupt();
            sessionWatcher = null;
        }
        if (gameWatcher != null) {
            gameWatcher.close();
            gameWatcher = null;
        }
    }
}
