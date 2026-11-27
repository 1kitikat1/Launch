package usa.kitikat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ConcurrentHashMap.KeySetView;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.prefs.Preferences;
import java.util.stream.Stream;
import javafx.animation.Animation;
import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.ParallelTransition;
import javafx.animation.PauseTransition;
import javafx.animation.Timeline;
import javafx.animation.TranslateTransition;
import javafx.animation.Animation.Status;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.Slider;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.control.ScrollPane.ScrollBarPolicy;
import javafx.scene.effect.DropShadow;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.image.PixelReader;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundImage;
import javafx.scene.layout.BackgroundPosition;
import javafx.scene.layout.BackgroundRepeat;
import javafx.scene.layout.BackgroundSize;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Arc;
import javafx.scene.shape.ArcTo;
import javafx.scene.shape.ArcType;
import javafx.scene.shape.Circle;
import javafx.scene.shape.ClosePath;
import javafx.scene.shape.CubicCurveTo;
import javafx.scene.shape.Ellipse;
import javafx.scene.shape.Line;
import javafx.scene.shape.LineTo;
import javafx.scene.shape.MoveTo;
import javafx.scene.shape.PathElement;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.Shape;
import javafx.scene.shape.StrokeLineCap;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.stage.FileChooser.ExtensionFilter;
import javafx.util.Duration;
import usa.kitikat.AccentTheme.Option;
import usa.kitikat.GitHubClient.ModsDiff;
import usa.kitikat.UpdateChecker.Info;
import usa.kitikat.account.AccountConfig;
import usa.kitikat.account.AccountProfile;
import usa.kitikat.account.AccountStore;
import usa.kitikat.account.AuthException;
import usa.kitikat.account.AuthService;
import usa.kitikat.account.Hwid;
import usa.kitikat.account.KeySubscription;
import usa.kitikat.account.AuthService.KeyInfo;
import usa.kitikat.account.AuthService.Session;

public final class MainShell extends BorderPane {
   private static final String NEMA_REPOSITORY = "https://github.com/1kitikat1/Nema";
   private static final String DELTA_REPOSITORY = "https://github.com/1kitikat1/Delta";
   private static final String SYSTEMDLC_REPOSITORY = "https://github.com/1kitikat1/SystemDLC";
   private static final String ROCKSTAR_REPOSITORY = "https://github.com/1kitikat1/Rockstar";
   private static final String DIMASIK26_REPOSITORY = "https://github.com/1kitikat1/Dimasik26";
   private static final String ATTACKDLC_REPOSITORY = "https://github.com/1kitikat1/AttackDLC";
   private final Stage stage;
   private final Runnable minimizeWindow;
   private final BiConsumer<String, String> discordUpdateCallback;
   private final DoubleConsumer scaleApplier;
   private final StackPane content = new StackPane();
   private final StackPane pageLayer = new StackPane();
   private StackPane updateOverlay;
   private StackPane errorOverlay;
   private StackPane clientUpdateOverlay;
   private final Map<String, ModsDiff> clientUpdates = new ConcurrentHashMap<>();
   private final Set<String> updateMarkers = ConcurrentHashMap.newKeySet();
   private long lastClientCheck;
   private FlowPane clientsGrid;
   private final ScrollPane pageScroll = new ScrollPane();
   private final ImageView wallpaperView = new ImageView();
   private final Rectangle wallpaperShade = new Rectangle();
   private final Preferences preferences = Preferences.userNodeForPackage(usa.kitikat.MainShell.class);
   private boolean discordRpcEnabled = true;
   private final MinecraftLauncher minecraftLauncher = new MinecraftLauncher();
   private final GitHubClient githubClient = new GitHubClient();
   private final String username = System.getProperty("user.name", "Player");
   private double dragX;
   private double dragY;
   private HBox titlebar;
   private static final double SIDEBAR_EXPANDED = 174.0;
   private static final double SIDEBAR_COLLAPSED = 62.0;
   private VBox sidebarNav;
   private final List<Animation> sidebarAnims = new ArrayList<>();
   private final List<Label> navLabels = new ArrayList<>();
   private final List<usa.kitikat.MainShell.NavIconSlot> navIconSlots = new ArrayList<>();
   private final AccountConfig accountConfig = AccountConfig.load();
   private final AccountStore accountStore = new AccountStore();
   private final AuthService authService = new AuthService(this.accountConfig);
   private boolean accountBusy;
   private String accountStatus;
   private String accountError;
   private boolean keyBusy;
   private String keyStatus;
   private String keyError;
   private boolean sessionBusy;
   private String sessionStatus;
   private String sessionError;
   private Label barUserLabel;
   private boolean animationsEnabled;
   private double animSpeed = 1.0;
   private boolean liveWallpaper;
   private String accentId;
   private Timeline wallpaperPulse;
   private String accentCssUrl;
   private boolean accentFromWallpaper;
   private String accentFromWallpaperHex;
   private boolean animeIcons;
   private String lastDetails = "Attack Launcher";
   private String lastState = "Смотрит каталог игр";
   private String lastModification = "";
   private volatile String runningGame;
   private GameActivityWatcher gameWatcher;
   private Thread sessionWatcher;
   private Label rpcStatusLabel;
   private boolean ownerBusy;
   private String ownerStatus;
   private String ownerError;
   private boolean ownerKeysLoaded;
   private HBox updateDock;
   private ProgressBar updateDockBar;
   private Label updateDockTitle;
   private Label updateDockLabel;
   private Button updateDockAction;
   private boolean backgroundUpdateRunning;
   private Path pendingUpdateZip;
   private String pendingUpdateVersion = "";

   public MainShell(Stage var1, Runnable var2, BiConsumer<String, String> var3, DoubleConsumer var4) {
      this.stage = var1;
      this.minimizeWindow = var2;
      this.discordUpdateCallback = var3;
      this.scaleApplier = var4;
      this.getStyleClass().add("window-root");
      this.discordRpcEnabled = this.preferences.getBoolean("discordRpc", true);
      this.animationsEnabled = this.preferences.getBoolean("uiAnimations", true);
      this.animSpeed = this.preferences.getDouble("animSpeed", 1.0);
      this.liveWallpaper = this.preferences.getBoolean("liveWallpaper", true);
      this.accentId = this.preferences.get("accent", "blue");
      this.animeIcons = this.preferences.getBoolean("animeIcons", false);
      this.accentFromWallpaper = this.preferences.getBoolean("accentFromWallpaper", false);

      for (String var8 : this.preferences.get("clientUpdateMarkers", "").split(",")) {
         if (!var8.isBlank()) {
            this.updateMarkers.add(var8.trim());
         }
      }

      this.lastModification = this.preferences.get("lastModification", "").trim();
      Thread.setDefaultUncaughtExceptionHandler((var1x, var2x) -> this.logUpdate("необработанная ошибка в потоке " + var1x.getName(), var2x));
      migrateFromOldLauncher();
      this.build();
      this.setupWallpaperLayer();
      this.setWindowOpacity(this.preferences.getDouble("windowOpacity", 0.9));
      this.applyAccentTheme();
      this.startGameWatcher();
      this.showGames();
      this.refreshProfileAsync();
      this.startSessionWatcher();
      if (!this.preferences.getBoolean("onboardingDone", false)) {
         this.showOnboarding();
      } else {
         this.checkUpdatesOnStart();
         this.checkClientUpdatesOnStart();
      }
   }

   private String installDrive() {
      String var1 = this.preferences.get("installDrive", "C:");
      return var1 != null && var1.length() >= 2 ? var1.substring(0, 2) : "C:";
   }

   private Path gameDirectory() {
      return Path.of(this.installDrive(), "Nemesis");
   }

   private Path deltaDirectory() {
      return Path.of(this.installDrive(), "Delta");
   }

   private Path systemdlcDirectory() {
      return Path.of(this.installDrive(), "SystemDLC");
   }

   private Path rockstarDirectory() {
      return Path.of(this.installDrive(), "Rockstar");
   }

   private Path dimasik26Directory() {
      return Path.of(this.installDrive(), "Dimasik26");
   }

   private Path attackdlcDirectory() {
      Path var1 = Path.of(this.installDrive(), "minecraft");

      for (String var5 : new String[]{"AttackDLC", "Minecraft"}) {
         Path var6 = Path.of(this.installDrive(), var5);
         if (!var6.toString().equals(var1.toString())) {
            if (var6.toString().equalsIgnoreCase(var1.toString())) {
               try {
                  Files.move(var6, var1);
               } catch (Throwable var8) {
               }
            } else {
               migrateLegacyGameDir(var6, var1);
            }
         }
      }

      return var1;
   }

   private static void migrateLegacyGameDir(Path var0, Path var1) {
      try {
         if (var0.toString().equalsIgnoreCase(var1.toString())) {
            return;
         }

         if (!Files.exists(var0)) {
            return;
         }

         if (!Files.exists(var1)) {
            Files.move(var0, var1);
            System.out.println("[AttackLauncher] game dir " + var0.getFileName() + " → " + var1.getFileName());
            return;
         }

         try (Stream<Path> var2 = Files.list(var0)) {
            for (Path var4 : var2.toList()) {
               Path var5 = var1.resolve(var4.getFileName().toString());
               if (Files.isDirectory(var4) && Files.isDirectory(var5)) {
                  moveRecursive(var4, var5);
                  deleteRecursive(var4);
               } else {
                  Files.move(var4, var5, StandardCopyOption.REPLACE_EXISTING);
               }
            }
         }

         deleteRecursive(var0);
         System.out.println("[AttackLauncher] merged game dir " + var0.getFileName() + " → " + var1.getFileName() + " (old folder removed)");
      } catch (Throwable var8) {
         System.err.println("[AttackLauncher] game dir migrate failed: " + var8.getMessage());
      }
   }

   private static void moveRecursive(Path var0, Path var1) throws IOException {
      try (Stream<Path> var2 = Files.list(var0)) {
         for (Path var4 : var2.toList()) {
            Path var5 = var1.resolve(var4.getFileName().toString());
            if (Files.isDirectory(var4)) {
               Files.createDirectories(var5);
               moveRecursive(var4, var5);
            } else {
               Files.move(var4, var5, StandardCopyOption.REPLACE_EXISTING);
            }
         }
      }
   }

   private static void deleteRecursive(Path var0) throws IOException {
      if (Files.exists(var0)) {
         try (Stream<Path> var1 = Files.walk(var0)) {
            for (Path var3 : var1.sorted(Comparator.reverseOrder()).toList()) {
               Files.deleteIfExists(var3);
            }
         }
      }
   }

   private List<String> installDrives() {
      ArrayList<String> var1 = new ArrayList<>();

      for (File var5 : File.listRoots()) {
         try {
            if (var5.getTotalSpace() > 0L) {
               var1.add(var5.getAbsolutePath().substring(0, 2));
            }
         } catch (Throwable var7) {
         }
      }

      if (var1.isEmpty()) {
         var1.add("C:");
      }

      return var1;
   }

   private String driveSpace(String var1) {
      try {
         File var2 = new File(var1 + File.separator);
         long var3 = var2.getTotalSpace();
         return var3 <= 0L ? "диск недоступен" : gb(var2.getFreeSpace()) + " ГБ свободно из " + gb(var3) + " ГБ";
      } catch (Throwable var5) {
         return "место неизвестно";
      }
   }

   private static String gb(long var0) {
      return Long.toString(Math.max(0L, Math.round((double)var0 / 1.0737418E9F)));
   }

   private void build() {
      this.buildTitlebar();
      VBox var1 = new VBox(6.0);
      var1.getStyleClass().add("sidebar");
      var1.setPadding(new Insets(18.0, 12.0, 16.0, 12.0));
      this.sidebarNav = var1;
      var1.getChildren()
         .addAll(
            new Node[]{
               this.navButton("Игры", "games", this::showGames),
               this.navButton("Профиль", "user", this::showProfile),
               this.navButton("Настройки", "sliders", this::showSettings)
            }
         );
      Region var2 = new Region();
      VBox.setVgrow(var2, Priority.ALWAYS);
      var1.getChildren().addAll(new Node[]{var2, this.holdToCloseButton()});
      this.setSidebarExpanded(false, false);
      var1.setOnMouseEntered(var1x -> this.setSidebarExpanded(true));
      var1.setOnMouseExited(var1x -> this.setSidebarExpanded(false));
      this.setLeft(var1);
      this.content.getStyleClass().add("content");
      this.setCenter(this.content);
      BorderPane.setMargin(this.content, new Insets(0.0, 14.0, 14.0, 0.0));
   }

   private void setupWallpaperLayer() {
      this.wallpaperView.getStyleClass().add("wallpaper-view");
      this.wallpaperView.setPreserveRatio(false);
      this.wallpaperView.setManaged(false);
      this.wallpaperView.setLayoutX(0.0);
      this.wallpaperView.setLayoutY(0.0);
      this.wallpaperView.fitWidthProperty().bind(this.widthProperty());
      this.wallpaperView.fitHeightProperty().bind(this.heightProperty());
      this.wallpaperView.setMouseTransparent(true);
      this.wallpaperShade.getStyleClass().add("wallpaper-shade");
      this.wallpaperShade.setManaged(false);
      this.wallpaperShade.setMouseTransparent(true);
      this.wallpaperShade.setFill(Color.BLACK);
      this.wallpaperShade.widthProperty().bind(this.widthProperty());
      this.wallpaperShade.heightProperty().bind(this.heightProperty());
      Rectangle var1 = new Rectangle();
      var1.widthProperty().bind(this.widthProperty());
      var1.heightProperty().bind(this.heightProperty());
      var1.setArcWidth(36.0);
      var1.setArcHeight(36.0);
      this.setClip(var1);
      this.getChildren().addAll(0, List.of(this.wallpaperView, this.wallpaperShade));
      this.pageLayer.getStyleClass().add("page-layer");
      this.pageLayer.setMouseTransparent(false);
      this.pageLayer.setAlignment(Pos.TOP_LEFT);
      this.pageScroll.setContent(this.pageLayer);
      this.pageScroll.setFitToWidth(true);
      this.pageScroll.setHbarPolicy(ScrollBarPolicy.NEVER);
      this.pageScroll.setVbarPolicy(ScrollBarPolicy.AS_NEEDED);
      this.pageScroll.setPannable(false);
      this.pageScroll.getStyleClass().add("page-scroll");
      this.pageScroll.setMouseTransparent(false);
      this.pageScroll.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
      this.content.getChildren().setAll(new Node[]{this.pageScroll});
      String var2 = this.preferences.get("wallpaper", "/assets/wallpapers/wallpaper1.jpg");
      this.loadWallpaper(var2);
      this.setWallpaperDim(this.preferences.getDouble("wallpaperDim", 0.28));
      this.startWallpaperPulse();
   }

   private void loadWallpaper(String var1) {
      try {
         Image var2;
         if (var1.startsWith("/")) {
            InputStream var5 = this.getClass().getResourceAsStream(var1);
            if (var5 == null) {
               throw new IOException("Ресурс не найден: " + var1);
            }

            var2 = new Image(var5);
         } else {
            var2 = new Image(Path.of(var1).toUri().toString());
         }

         this.wallpaperView.setImage(var2);
         this.preferences.put("wallpaper", var1);
      } catch (Exception var4) {
         InputStream var3 = this.getClass().getResourceAsStream("/assets/wallpapers/wallpaper1.jpg");
         if (var3 != null) {
            this.wallpaperView.setImage(new Image(var3));
         }
      }

      if (this.accentFromWallpaper) {
         this.accentFromWallpaperHex = null;
         this.applyAccentTheme();
      }
   }

   private String pickWallpaperAccent() {
      Image var1 = this.wallpaperView.getImage();
      if (var1 != null && !(var1.getWidth() < 1.0) && !(var1.getHeight() < 1.0)) {
         try {
            int var2 = (int)var1.getWidth();
            int var3 = (int)var1.getHeight();
            PixelReader var4 = var1.getPixelReader();
            if (var4 == null) {
               return null;
            } else {
               long var5 = 0L;
               long var7 = 0L;
               long var9 = 0L;
               long var11 = 0L;

               for (int var13 = var3 / 4; var13 < var3 * 3 / 4; var13 += Math.max(1, var3 / 16)) {
                  for (int var14 = var2 / 4; var14 < var2 * 3 / 4; var14 += Math.max(1, var2 / 16)) {
                     int var15 = var4.getArgb(var14, var13);
                     var5 += (long)(var15 >> 16 & 0xFF);
                     var7 += (long)(var15 >> 8 & 0xFF);
                     var9 += (long)(var15 & 0xFF);
                     var11++;
                  }
               }

               if (var11 == 0L) {
                  return null;
               } else {
                  java.awt.Color var17 = new java.awt.Color(
                     (int)Math.min(255.0, (double)(var5 / var11) * 1.4),
                     (int)Math.min(255.0, (double)(var7 / var11) * 1.4),
                     (int)Math.min(255.0, (double)(var9 / var11) * 1.4)
                  );
                  return String.format("#%02x%02x%02x", var17.getRed(), var17.getGreen(), var17.getBlue());
               }
            }
         } catch (Throwable var16) {
            return null;
         }
      } else {
         return null;
      }
   }

   private void setWallpaperDim(double var1) {
      double var3 = Math.max(0.0, Math.min(0.75, var1));
      this.wallpaperShade.setOpacity(var3);
      this.preferences.putDouble("wallpaperDim", var3);
   }

   private Button dot(String var1) {
      Button var2 = new Button();
      var2.getStyleClass().addAll(new String[]{"traffic-dot", var1});
      switch (var1) {
         case "close":
            var2.setOnAction(var1x -> this.stage.close());
            break;
         case "min":
            var2.setOnAction(var1x -> this.minimizeWindow.run());
            break;
         case "max":
            var2.setOnAction(var1x -> this.stage.setMaximized(!this.stage.isMaximized()));
            break;
         default:
            var2.setOnAction(var1x -> this.stage.setIconified(true));
      }

      return var2;
   }

   private void buildTitlebar() {
      if (this.titlebar == null) {
         this.titlebar = new HBox(12.0);
         this.titlebar.getStyleClass().add("titlebar");
         this.titlebar.setAlignment(Pos.CENTER_LEFT);
         this.titlebar.setPadding(new Insets(13.0, 18.0, 11.0, 18.0));
         this.titlebar.setOnMousePressed(var1x -> {
            this.dragX = var1x.getSceneX();
            this.dragY = var1x.getSceneY();
         });
         this.titlebar.setOnMouseDragged(var1x -> {
            this.stage.setX(var1x.getScreenX() - this.dragX);
            this.stage.setY(var1x.getScreenY() - this.dragY);
         });
      }

      boolean var1 = this.isClassicWindowControls();
      ImageView var2 = this.image("/assets/nemesis.png", 26.0, 26.0);
      var2.getStyleClass().add("brand-logo");
      var2.setClip(this.roundedClip(26.0, 26.0, 8.0));
      Label var3 = new Label("ATTACK");
      var3.getStyleClass().add("bar-brand");
      Region var4 = new Region();
      HBox.setHgrow(var4, Priority.ALWAYS);
      Label var5 = new Label(this.activeUsername());
      var5.getStyleClass().add("bar-user");
      this.barUserLabel = var5;
      ArrayList<Node> var6 = new ArrayList<>();
      if (!var1) {
         HBox var7 = new HBox(7.0, new Node[]{this.dot("close"), this.dot("min"), this.dot("max")});
         var7.setAlignment(Pos.CENTER_LEFT);
         var6.add(var7);
      }

      var6.add(var2);
      var6.add(var3);
      var6.add(var4);
      var6.add(var5);
      if (var1) {
         HBox var8 = new HBox(6.0, new Node[]{this.winButton("min"), this.winButton("max"), this.winButton("close")});
         var8.setAlignment(Pos.CENTER_LEFT);
         var8.setPadding(new Insets(0.0, 0.0, 0.0, 6.0));
         var6.add(var8);
      }

      this.titlebar.getChildren().setAll(var6);
      this.setTop(this.titlebar);
   }

   private Button winButton(String var1) {
      Button var2 = new Button();
      var2.setGraphic(this.winGlyph(var1));
      var2.setFocusTraversable(false);
      var2.getStyleClass().addAll(new String[]{"win-button", var1});
      switch (var1) {
         case "close":
            var2.setOnAction(var1x -> this.stage.close());
            var2.setTooltip(new Tooltip("Закрыть"));
            break;
         case "max":
            var2.setOnAction(var1x -> this.stage.setMaximized(!this.stage.isMaximized()));
            var2.setTooltip(new Tooltip("Развернуть"));
            break;
         default:
            var2.setOnAction(var1x -> this.minimizeWindow.run());
            var2.setTooltip(new Tooltip("Свернуть"));
      }

      return var2;
   }

   private Group winGlyph(String var1) {
      javafx.scene.shape.Path var2 = new javafx.scene.shape.Path();
      var2.getStyleClass().add("win-glyph");
      var2.setFill(null);
      switch (var1) {
         case "close":
            var2.getElements().addAll(new PathElement[]{new MoveTo(3.5, 3.5), new LineTo(12.5, 12.5), new MoveTo(12.5, 3.5), new LineTo(3.5, 12.5)});
            break;
         case "max":
            Rectangle var5 = new Rectangle(3.5, 3.5, 9.0, 9.0);
            var5.setArcWidth(2.5);
            var5.setArcHeight(2.5);
            var5.getStyleClass().add("win-glyph");
            var5.setFill(null);
            return new Group(new Node[]{var5});
         default:
            var2.getElements().addAll(new PathElement[]{new MoveTo(3.0, 9.5), new LineTo(13.0, 9.5)});
      }

      return new Group(new Node[]{var2});
   }

   private boolean isClassicWindowControls() {
      return "classic".equals(this.preferences.get("windowControls", "dots"));
   }

   private Button navButton(String var1, String var2, Runnable var3) {
      Button var4 = new Button();
      var4.getStyleClass().add("nav-button");
      var4.setOnAction(var1x -> var3.run());
      var4.setTooltip(new Tooltip(var1));
      var4.setAccessibleText(var1);
      Label var5 = new Label(var1);
      var5.getStyleClass().add("nav-label");
      StackPane var6 = new StackPane(new Node[]{this.resolveIcon(var2)});
      var6.setMinSize(18.0, 18.0);
      var6.setPrefSize(18.0, 18.0);
      var6.setMaxSize(18.0, 18.0);
      var6.setMouseTransparent(true);
      this.navIconSlots.add(new usa.kitikat.MainShell.NavIconSlot(var2, var6, List.of()));
      HBox var7 = new HBox(10.0, new Node[]{var6, var5});
      var7.setAlignment(Pos.CENTER_LEFT);
      var7.setMouseTransparent(true);
      var4.setGraphic(var7);
      this.navLabels.add(var5);
      return var4;
   }

   private Button holdToCloseButton() {
      Button var1 = new Button();
      var1.getStyleClass().add("nav-button");
      var1.setTooltip(new Tooltip("Закрыть — зажми кнопку до конца"));
      var1.setAccessibleText("Закрыть");
      Label var2 = new Label("Закрыть");
      var2.getStyleClass().add("nav-label");
      Arc var3 = new Arc();
      var3.setCenterX(9.0);
      var3.setCenterY(9.0);
      var3.setRadiusX(11.5);
      var3.setRadiusY(11.5);
      var3.setType(ArcType.OPEN);
      var3.setFill(null);
      var3.setStroke(Color.web("#ffc9c5"));
      var3.setStrokeWidth(2.8);
      var3.setStrokeLineCap(StrokeLineCap.ROUND);
      var3.setStartAngle(-90.0);
      var3.setLength(0.0);
      var3.setEffect(new DropShadow(7.0, Color.web("#ff5f57", 0.9)));
      StackPane var4 = new StackPane(new Node[]{this.navIcon("close"), var3});
      var4.setMinSize(18.0, 18.0);
      var4.setPrefSize(18.0, 18.0);
      var4.setMaxSize(18.0, 18.0);
      var4.setMouseTransparent(true);
      this.navIconSlots.add(new usa.kitikat.MainShell.NavIconSlot("close", var4, List.of(var3)));
      HBox var5 = new HBox(10.0, new Node[]{var4, var2});
      var5.setAlignment(Pos.CENTER_LEFT);
      var5.setMouseTransparent(true);
      var1.setGraphic(var5);
      this.navLabels.add(var2);
      Timeline var6 = new Timeline(
         new KeyFrame[]{new KeyFrame(Duration.millis(1100.0), new KeyValue[]{new KeyValue(var3.lengthProperty(), 360.0, Interpolator.LINEAR)})}
      );
      Timeline var7 = new Timeline(
         new KeyFrame[]{new KeyFrame(Duration.millis(200.0), new KeyValue[]{new KeyValue(var3.lengthProperty(), 0.0, Interpolator.EASE_OUT)})}
      );
      var6.setOnFinished(var2x -> {
         var7.stop();
         this.stage.close();
      });
      var1.addEventHandler(MouseEvent.MOUSE_PRESSED, var4x -> {
         var7.stop();
         var3.setLength(0.0);
         if (!var1.getStyleClass().contains("closing")) {
            var1.getStyleClass().add("closing");
         }

         var6.playFromStart();
      });
      Runnable var8 = () -> {
         if (var6.getStatus() == Status.RUNNING) {
            var6.stop();
            var7.playFromStart();
         }

         var1.getStyleClass().remove("closing");
      };
      var1.addEventHandler(MouseEvent.MOUSE_RELEASED, var1x -> var8.run());
      var1.addEventHandler(MouseEvent.MOUSE_EXITED, var1x -> var8.run());
      return var1;
   }

   private Node resolveIcon(String var1) {
      if (this.animeIcons) {
         Node var2 = this.animeIcon(var1);
         if (var2 != null) {
            return var2;
         }
      }

      return this.navIcon(var1);
   }

   private void refreshNavIcons() {
      for (usa.kitikat.MainShell.NavIconSlot var2 : this.navIconSlots) {
         ArrayList<Node> var3 = new ArrayList<>();
         var3.add(this.resolveIcon(var2.kind()));
         var3.addAll(var2.extras());
         var2.slot().getChildren().setAll(var3);
      }
   }

   private Node animeIcon(String var1) {
      Group var2 = new Group();
      Color var3 = Color.web("#ff9ecb");
      Color var4 = Color.web("#ffc4dd");
      Color var5 = Color.web("#ff6fae");
      switch (var1) {
         case "games":
            Circle var20 = new Circle(9.0, 10.0, 6.0);
            var20.setFill(var4);
            javafx.scene.shape.Path var23 = new javafx.scene.shape.Path(
               new PathElement[]{new MoveTo(4.2, 6.5), new LineTo(5.5, 1.5), new LineTo(9.0, 5.5), new ClosePath()}
            );
            var23.setFill(var3);
            javafx.scene.shape.Path var25 = new javafx.scene.shape.Path(
               new PathElement[]{new MoveTo(13.8, 6.5), new LineTo(12.5, 1.5), new LineTo(9.0, 5.5), new ClosePath()}
            );
            var25.setFill(var3);
            Circle var28 = new Circle(6.8, 9.5, 0.9);
            Circle var30 = new Circle(11.2, 9.5, 0.9);
            var28.setFill(Color.web("#4a2b3a"));
            var30.setFill(Color.web("#4a2b3a"));
            Line var33 = new Line(8.0, 12.4, 10.0, 12.4);
            var33.setStroke(Color.web("#4a2b3a"));
            var33.setStrokeWidth(1.1);
            var33.setStrokeLineCap(StrokeLineCap.ROUND);
            var2.getChildren().addAll(new Node[]{var23, var25, var20, var28, var30, var33});
            break;
         case "user":
            Circle var19 = new Circle(9.0, 11.5, 4.2);
            var19.setFill(var3);
            double[][] var22 = new double[][]{{4.6, 6.4}, {7.3, 4.4}, {10.7, 4.4}, {13.4, 6.4}};

            for (double[] var32 : var22) {
               Circle var34 = new Circle(var32[0], var32[1], 1.9);
               var34.setFill(var4);
               var2.getChildren().add(var34);
            }

            var2.getChildren().add(var19);
            break;
         case "sliders":
            javafx.scene.shape.Path var18 = new javafx.scene.shape.Path(
               new PathElement[]{
                  new MoveTo(9.0, 15.5), new CubicCurveTo(2.0, 10.5, 3.5, 3.5, 9.0, 6.5), new CubicCurveTo(14.5, 3.5, 16.0, 10.5, 9.0, 15.5), new ClosePath()
               }
            );
            var18.setFill(var5);
            var2.getChildren().add(var18);
            break;
         case "close":
            Line var17 = new Line(9.0, 1.5, 9.0, 16.5);
            Line var21 = new Line(1.5, 9.0, 16.5, 9.0);
            Line var10 = new Line(4.0, 4.0, 14.0, 14.0);
            Line var26 = new Line(14.0, 4.0, 4.0, 14.0);

            for (Line var35 : new Line[]{var17, var21, var10, var26}) {
               var35.setStroke(var3);
               var35.setStrokeWidth(1.8);
               var35.setStrokeLineCap(StrokeLineCap.ROUND);
            }

            var2.getChildren().addAll(new Node[]{var10, var26, var17, var21});
            break;
         default:
            for (int var8 = 0; var8 < 5; var8++) {
               double var9 = Math.toRadians((double)(var8 * 72 - 90));
               double var11 = 9.0 + Math.cos(var9) * 4.6;
               double var13 = 9.0 + Math.sin(var9) * 4.6;
               Ellipse var15 = new Ellipse(var11, var13, 2.9, 3.6);
               var15.setFill(var3);
               var15.setRotate((double)(var8 * 72));
               var2.getChildren().add(var15);
            }

            Circle var16 = new Circle(9.0, 9.0, 2.0);
            var16.setFill(Color.web("#ffe27a"));
            var2.getChildren().add(var16);
      }

      return var2;
   }

   private Node navIcon(String var1) {
      Group var2 = new Group();
      switch (var1) {
         case "games":
            Rectangle var12 = new Rectangle(1.5, 5.0, 15.0, 9.0);
            var12.setArcWidth(6.0);
            var12.setArcHeight(6.0);
            Line var15 = new Line(4.0, 9.5, 8.0, 9.5);
            Line var16 = new Line(6.0, 7.5, 6.0, 11.5);
            Line var8 = new Line(12.0, 8.0, 14.0, 8.0);
            Line var9 = new Line(13.0, 7.0, 13.0, 9.0);
            var2.getChildren().addAll(new Node[]{var12, var15, var16, var8, var9});
            break;
         case "user":
            Circle var11 = new Circle(9.0, 6.0, 3.0);
            javafx.scene.shape.Path var14 = new javafx.scene.shape.Path();
            var14.getElements().addAll(new PathElement[]{new MoveTo(3.0, 16.0), new ArcTo(6.5, 6.5, 0.0, 15.0, 16.0, false, true)});
            var2.getChildren().addAll(new Node[]{var11, var14});
            break;
         case "sliders":
            Line var5 = new Line(2.0, 5.5, 16.0, 5.5);
            Line var6 = new Line(2.0, 9.5, 16.0, 9.5);
            Line var7 = new Line(2.0, 13.5, 16.0, 13.5);
            var2.getChildren().addAll(new Node[]{var5, var6, var7, this.sliderKnob(6.0, 5.5), this.sliderKnob(11.5, 9.5), this.sliderKnob(5.0, 13.5)});
            break;
         default:
            var2.getChildren().addAll(new Node[]{new Line(5.0, 5.0, 13.0, 13.0), new Line(13.0, 5.0, 5.0, 13.0)});
      }

      for (Node var10 : var2.getChildren()) {
         if (var10 instanceof Shape var13) {
            var13.setFill(null);
         }

         var10.getStyleClass().add("nav-icon");
      }

      return var2;
   }

   private Rectangle sliderKnob(double var1, double var3) {
      Rectangle var5 = new Rectangle(var1 - 1.7, var3 - 1.7, 3.4, 3.4);
      var5.setArcWidth(3.0);
      var5.setArcHeight(3.0);
      return var5;
   }

   private void setSidebarExpanded(boolean var1) {
      this.setSidebarExpanded(var1, true);
   }

   private void setSidebarExpanded(boolean var1, boolean var2) {
      if (this.sidebarNav != null) {
         this.stopSidebarAnims();
         if (var1) {
            this.animateSidebarWidth(174.0, var2, () -> this.setLabelsVisible(true, var2));
         } else {
            this.setLabelsVisible(false, var2);
            if (var2) {
               PauseTransition var3 = new PauseTransition(Duration.millis(150.0));
               var3.setOnFinished(var1x -> this.animateSidebarWidth(62.0, true, null));
               var3.play();
               this.sidebarAnims.add(var3);
            } else {
               this.sidebarNav.setPrefWidth(62.0);
            }
         }
      }
   }

   private void animateSidebarWidth(double var1, boolean var3, Runnable var4) {
      if (!var3) {
         this.sidebarNav.setPrefWidth(var1);
         if (var4 != null) {
            var4.run();
         }
      } else {
         Timeline var5 = new Timeline(
            new KeyFrame[]{
               new KeyFrame(Duration.millis(180.0), new KeyValue[]{new KeyValue(this.sidebarNav.prefWidthProperty(), var1, Interpolator.EASE_OUT)})
            }
         );
         if (var4 != null) {
            var5.setOnFinished(var1x -> var4.run());
         }

         var5.play();
         this.sidebarAnims.add(var5);
      }
   }

   private void setLabelsVisible(boolean var1, boolean var2) {
      Timeline var3 = new Timeline();
      double var4 = var1 ? 1.0 : 0.0;

      for (Label var7 : this.navLabels) {
         if (var1) {
            var7.setVisible(true);
            var7.setManaged(true);
         }

         if (var2) {
            var3.getKeyFrames().add(new KeyFrame(Duration.millis(140.0), new KeyValue[]{new KeyValue(var7.opacityProperty(), var4, Interpolator.EASE_OUT)}));
         } else {
            var7.setOpacity(var4);
            if (!var1) {
               var7.setVisible(false);
               var7.setManaged(false);
            }
         }
      }

      if (!var1 && var2) {
         var3.setOnFinished(var1x -> this.navLabels.forEach(var0 -> {
               var0.setVisible(false);
               var0.setManaged(false);
            }));
      }

      if (var2) {
         var3.play();
         this.sidebarAnims.add(var3);
      }
   }

   private void stopSidebarAnims() {
      for (Animation var2 : this.sidebarAnims) {
         if (var2.getStatus() == Status.RUNNING) {
            var2.stop();
         }
      }

      this.sidebarAnims.clear();
   }

   private void swap(Node var1) {
      this.pageLayer.getChildren().setAll(new Node[]{var1});
      if (!this.animationsEnabled) {
         var1.setOpacity(1.0);
         var1.setTranslateY(0.0);
      } else {
         var1.setOpacity(0.0);
         var1.setTranslateY(12.0);
         FadeTransition var2 = new FadeTransition(Duration.millis(240.0 * this.animSpeed), var1);
         var2.setToValue(1.0);
         TranslateTransition var3 = new TranslateTransition(Duration.millis(260.0 * this.animSpeed), var1);
         var3.setToY(0.0);
         new ParallelTransition(new Animation[]{var2, var3}).play();
      }
   }

   private void animateCardsIn(FlowPane var1) {
      if (this.animationsEnabled) {
         int var2 = 0;

         for (Node var4 : var1.getChildren()) {
            var4.setOpacity(0.0);
            FadeTransition var5 = new FadeTransition(Duration.millis(280.0 * this.animSpeed), var4);
            var5.setFromValue(0.0);
            var5.setToValue(1.0);
            var5.setDelay(Duration.millis((double)(70L + (long)var2 * 60L) * this.animSpeed));
            var5.play();
            var2++;
         }
      }
   }

   private void showGames() {
      this.refreshRpcIdentity();
      VBox var1 = this.page("ИГРЫ", "Выберите игру", "Выберите игру, затем клиент или сборку.");
      FlowPane var2 = new FlowPane(14.0, 14.0);
      var2.setPrefWrapLength(680.0);
      var2.getChildren()
         .addAll(
            new Node[]{
               this.minecraftCard(), this.simpleGameCard("Dota 2", "MOBA · официальный клиент", "/assets/wallpapers/wallpaper4.jpg", "/assets/dota2-icon.png")
            }
         );
      var1.getChildren().add(var2);
      this.animateCardsIn(var2);
      this.swap(var1);
   }

   private StackPane minecraftCard() {
      StackPane var1 = new StackPane();
      var1.getStyleClass().add("game-card-small");
      var1.setPrefSize(245.0, 158.0);
      var1.setClip(this.roundedClip(245.0, 158.0, 15.0));

      try {
         Image var2 = new Image(this.getClass().getResourceAsStream("/assets/minecraft-background.png"));
         var1.setBackground(
            new Background(
               new BackgroundImage[]{
                  new BackgroundImage(
                     var2,
                     BackgroundRepeat.NO_REPEAT,
                     BackgroundRepeat.NO_REPEAT,
                     BackgroundPosition.CENTER,
                     new BackgroundSize(100.0, 100.0, false, false, true, true)
                  )
               }
            )
         );
      } catch (Exception var7) {
      }

      StackPane var8 = new StackPane();
      var8.getStyleClass().add("game-card-overlay");
      ImageView var3 = this.image("/assets/minecraft-icon.png", 34.0, 34.0);
      var3.getStyleClass().add("game-card-icon");
      StackPane.setAlignment(var3, Pos.TOP_LEFT);
      StackPane.setMargin(var3, new Insets(12.0));
      VBox var4 = new VBox(3.0);
      var4.setAlignment(Pos.BOTTOM_LEFT);
      var4.setPadding(new Insets(14.0));
      Label var5 = new Label("Minecraft");
      var5.getStyleClass().add("game-card-title");
      Label var6 = new Label("Клиенты и сборки");
      var6.getStyleClass().add("game-card-subtitle");
      var4.getChildren().addAll(new Node[]{var5, var6});
      StackPane.setAlignment(var4, Pos.BOTTOM_LEFT);
      var8.getChildren().addAll(new Node[]{var3, var4});
      var1.getChildren().add(var8);
      var1.setOnMouseClicked(var1x -> this.showMinecraftClients());
      return var1;
   }

   private StackPane simpleGameCard(String var1, String var2, String var3, String var4) {
      StackPane var5 = new StackPane();
      var5.getStyleClass().add("game-card-small");
      var5.setPrefSize(245.0, 158.0);
      var5.setClip(this.roundedClip(245.0, 158.0, 15.0));
      this.setCardBackground(var5, var3);
      StackPane var6 = new StackPane();
      var6.getStyleClass().add("game-card-overlay");

      try {
         ImageView var7 = this.image(var4, 34.0, 34.0);
         var7.getStyleClass().add("game-card-icon");
         StackPane.setAlignment(var7, Pos.TOP_LEFT);
         StackPane.setMargin(var7, new Insets(12.0));
         var6.getChildren().add(var7);
      } catch (Exception var10) {
      }

      VBox var11 = new VBox(3.0);
      var11.setAlignment(Pos.BOTTOM_LEFT);
      var11.setPadding(new Insets(14.0));
      Label var8 = new Label(var1);
      var8.getStyleClass().add("game-card-title");
      Label var9 = new Label(var2);
      var9.getStyleClass().add("game-card-subtitle");
      var11.getChildren().addAll(new Node[]{var8, var9});
      StackPane.setAlignment(var11, Pos.BOTTOM_LEFT);
      var6.getChildren().add(var11);
      var5.getChildren().add(var6);
      var5.setOnMouseClicked(var2x -> this.showUnsupportedGame(var1));
      return var5;
   }

   private Rectangle roundedClip(double var1, double var3, double var5) {
      Rectangle var7 = new Rectangle(var1, var3);
      var7.setArcWidth(var5 * 2.0);
      var7.setArcHeight(var5 * 2.0);
      return var7;
   }

   private void setCardBackground(Region var1, String var2) {
      try {
         InputStream var3 = this.getClass().getResourceAsStream(var2);
         if (var3 == null) {
            return;
         }

         Image var4 = new Image(var3);
         var1.setBackground(
            new Background(
               new BackgroundImage[]{
                  new BackgroundImage(
                     var4,
                     BackgroundRepeat.NO_REPEAT,
                     BackgroundRepeat.NO_REPEAT,
                     BackgroundPosition.CENTER,
                     new BackgroundSize(100.0, 100.0, false, false, true, true)
                  )
               }
            )
         );
      } catch (Exception var5) {
      }
   }

   private void showUnsupportedGame(String var1) {
      this.discordUpdate("Смотрит: " + var1, "Выбор игры");
      VBox var2 = this.page("ИГРА", var1, "Официальный клиент — запускается через Steam.");
      VBox var3 = new VBox(8.0);
      var3.getStyleClass().add("empty-state-card");
      Label var4 = new Label("Запуск через Steam");
      var4.getStyleClass().add("title-medium");
      Label var5 = new Label("Для " + var1 + " модификации не нужны — игра откроется через официальный клиент Steam.");
      var5.setWrapText(true);
      var5.getStyleClass().add("muted");
      Label var6 = new Label("");
      var6.getStyleClass().add("status");
      Button var7 = new Button("← Назад к играм");
      var7.getStyleClass().add("ghost-button");
      var7.setOnAction(var1x -> this.showGames());
      Button var8 = new Button("Запустить " + var1);
      var8.getStyleClass().add("primary-button");
      var8.setOnAction(var4x -> this.launchSteamGame(var1, "570", var6, var8));
      var3.getChildren().addAll(new Node[]{var4, var5, new HBox(9.0, new Node[]{var7, var8}), var6});
      var2.getChildren().add(var3);
      this.swap(var2);
   }

   private void launchSteamGame(String var1, String var2, Label var3, Button var4) {
      var4.setDisable(true);
      this.rememberModification(var1);
      var3.setText("Открываем Steam…");
      Thread.startVirtualThread(() -> {
         try {
            new ProcessBuilder("cmd", "/c", "start", "", "steam://run/" + var2).start();
            Platform.runLater(() -> var3.setText(var1 + " запускается в Steam."));
         } catch (Exception var5) {
            Platform.runLater(() -> {
               var3.setText("Steam не найден — установи его и повтори.");
               var4.setDisable(false);
            });
         }
      });
   }

   private void showMinecraftClients() {
      this.discordUpdate("Смотрит: Minecraft", "Выбор клиента");
      VBox var1 = this.page("MINECRAFT", "Выберите клиент", "Установленные клиенты и сборки Minecraft.");
      FlowPane var2 = new FlowPane(12.0, 12.0);
      this.clientsGrid = var2;

      for (usa.kitikat.MainShell.MinecraftClient var4 : this.minecraftClients()) {
         if (!this.isAttackClient(var4) || this.hasAttackAccess()) {
            var2.getChildren().add(this.clientCard(var4));
         }
      }

      var1.getChildren().add(var2);
      this.swap(var1);
   }

   private List<usa.kitikat.MainShell.MinecraftClient> minecraftClients() {
      usa.kitikat.MainShell.MinecraftClient var1 = new usa.kitikat.MainShell.MinecraftClient(
         "Nemesis",
         "Fabric 1.21.11",
         "Официальная сборка Nemesis для Minecraft.",
         "/assets/nemesis-client.png",
         "/assets/nemesis-client.png",
         "https://github.com/1kitikat1/Nema",
         "https://github.com/1kitikat1/Nema/archive/refs/heads/main.zip",
         this.gameDirectory()
      );
      usa.kitikat.MainShell.MinecraftClient var2 = new usa.kitikat.MainShell.MinecraftClient(
         "Delta",
         "Дельта 1.21.4 → 1.21.11",
         "Дельта 1.21.4, перенесённая на 1.21.11.",
         "/assets/delta-background.png",
         "/assets/delta-icon.png",
         "https://github.com/1kitikat1/Delta",
         "https://github.com/1kitikat1/Delta/archive/refs/heads/main.zip",
         this.deltaDirectory()
      );
      usa.kitikat.MainShell.MinecraftClient var3 = new usa.kitikat.MainShell.MinecraftClient(
         "SystemDLC",
         "Fabric 1.21.4",
         "SystemDLC — Клиент который изменил стандарты премиальных читов моды, ресурсы и версии подтягиваются с GitHub.",
         "/assets/systemdlc-client.png",
         "/assets/systemdlc-icon.png",
         "https://github.com/1kitikat1/SystemDLC",
         "https://github.com/1kitikat1/SystemDLC/archive/refs/heads/main.zip",
         this.systemdlcDirectory()
      );
      usa.kitikat.MainShell.MinecraftClient var4 = new usa.kitikat.MainShell.MinecraftClient(
         "Rockstar",
         "Fabric 1.21.11",
         "Кряк сделан twxlight — это порт. Этот чит — эволюция визуальной части среди читов.",
         "/assets/rockstar-client.png",
         "/assets/rockstar-icon.png",
         "https://github.com/1kitikat1/Rockstar",
         "https://github.com/1kitikat1/Rockstar/archive/refs/heads/main.zip",
         this.rockstarDirectory()
      );
      usa.kitikat.MainShell.MinecraftClient var5 = new usa.kitikat.MainShell.MinecraftClient(
         "Dimasik",
         "Fabric 26.2",
         "Dimasik — клиент для Minecraft 26.2. Кряк сделан ",
         "/assets/dimasik-client.png",
         "/assets/dimasik-icon.png",
         "https://github.com/1kitikat1/Dimasik26",
         "https://github.com/1kitikat1/Dimasik26/archive/refs/heads/main.zip",
         this.dimasik26Directory()
      );
      usa.kitikat.MainShell.MinecraftClient var6 = new usa.kitikat.MainShell.MinecraftClient(
         "Attack DLC",
         "Fabric 26.2",
         "Клиент Аттак — новый платный клиент, лучший из всех.",
         "/assets/attackdlc-client.jpg",
         "/assets/attackdlc-icon.png",
         "https://github.com/1kitikat1/AttackDLC",
         "https://github.com/1kitikat1/Dimasik26/archive/refs/heads/main.zip",
         this.attackdlcDirectory()
      );
      return List.of(var6, var1, var2, var3, var4, var5);
   }

   private boolean hasAttackAccess() {
      AccountProfile var1 = this.accountStore.profile();
      if (var1 == null) {
         return false;
      } else if ("owner".equals(var1.roleOrFree())) {
         return true;
      } else if (var1.isYoutuber()) {
         return true;
      } else if (!var1.hasKey()) {
         return false;
      } else {
         KeySubscription var2 = this.accountStore.subscription();
         return var2 == null ? false : var2.active() && var2.ownedBy(var1.uid());
      }
   }

   private boolean isAttackClient(usa.kitikat.MainShell.MinecraftClient var1) {
      return var1 != null && "https://github.com/1kitikat1/AttackDLC".equals(var1.repository());
   }

   private VBox clientCard(usa.kitikat.MainShell.MinecraftClient var1) {
      VBox var2 = new VBox(8.0);
      var2.getStyleClass().add("client-card");
      var2.setPadding(new Insets(10.0));
      var2.setPrefSize(205.0, 132.0);
      ImageView var3 = this.image(var1.picturePath(), 185.0, 70.0);
      var3.setPreserveRatio(false);
      StackPane var4 = new StackPane(new Node[]{var3});
      var4.getStyleClass().add("client-image");
      var4.setPrefHeight(70.0);
      Label var5 = new Label(var1.name());
      var5.getStyleClass().add("card-title");
      Label var6 = new Label(var1.version());
      var6.getStyleClass().add("card-version");
      if (this.updateMarkers.contains(var1.name()) || this.clientUpdates.containsKey(var1.name())) {
         Label var7 = new Label("Есть обновление");
         var7.getStyleClass().add("update-badge");
         StackPane.setAlignment(var7, Pos.TOP_RIGHT);
         StackPane.setMargin(var7, new Insets(6.0));
         var4.getChildren().add(var7);
      }

      Button var8 = new Button("Открыть");
      var8.getStyleClass().add("card-button");
      var8.setMaxWidth(Double.MAX_VALUE);
      var8.setOnAction(var2x -> this.showGameDetails(var1));
      var2.getChildren().addAll(new Node[]{var4, var5, var6, var8});
      return var2;
   }

   private void showGameDetails(usa.kitikat.MainShell.MinecraftClient var1) {
      VBox var2 = this.page("MINECRAFT", var1.name(), var1.version() + " · установка и обновление");
      HBox var3 = new HBox(14.0);
      ImageView var4 = this.image(var1.iconPath(), 90.0, 70.0);
      Label var5 = new Label(var1.name());
      var5.getStyleClass().add("title-medium");
      Label var6 = new Label(var1.description());
      var6.getStyleClass().add("muted");
      var6.setWrapText(true);
      VBox var7 = new VBox(6.0, new Node[]{var5, var6});
      var7.setFillWidth(true);
      var7.setMaxWidth(Double.MAX_VALUE);
      StackPane var8 = new StackPane(new Node[]{var7});
      var8.getStyleClass().add("client-info-card");
      var8.setMaxWidth(Double.MAX_VALUE);
      var3.getChildren().addAll(new Node[]{var4, var8});
      HBox.setHgrow(var8, Priority.ALWAYS);
      ProgressBar var9 = new ProgressBar(0.0);
      var9.setMaxWidth(Double.MAX_VALUE);
      var9.setVisible(false);
      Label var10 = new Label("Готово.");
      var10.getStyleClass().add("status");
      boolean var11 = this.githubClient.isInstalled(var1.installDir());
      Button var12 = new Button("Проверить обновление");
      var12.getStyleClass().add("primary-button");
      var12.setOnAction(var6x -> {
         if (var11) {
            this.checkClientUpdate(var1, var12, var9, var10);
         } else {
            this.checkManifest(var1.archiveUrl(), var12, var9, var10);
         }
      });
      Button var13 = new Button(var11 ? "Запустить" : "Скачать / установить");
      var13.getStyleClass().add("secondary-button");
      var13.setOnAction(var5x -> this.downloadClient(var1, var13, var9, var10));
      if (var11) {
         ModsDiff var14 = this.clientUpdates.get(var1.name());
         var10.setText(
            var14 == null
               ? "Сборка уже установлена · готова к запуску"
               : "Доступно обновление: "
                  + var14.download().size()
                  + " файл(ов)"
                  + (var14.remove().isEmpty() ? "" : ", удалится " + var14.remove().size())
                  + " — нажмите «Проверить обновление»"
         );
      }

      Button var15 = new Button("← Назад");
      var15.getStyleClass().add("ghost-button");
      var15.setOnAction(var1x -> this.showMinecraftClients());
      var2.getChildren().addAll(new Node[]{var3, new Separator(), new HBox(9.0, new Node[]{var15, var12, var13}), var9, var10});
      this.swap(var2);
   }

   private void checkManifest(String var1, Button var2, ProgressBar var3, Label var4) {
      var2.setDisable(true);
      var3.setVisible(true);
      var3.setProgress(-1.0);
      var4.setText("Проверяем...");
      Thread.startVirtualThread(() -> {
         try {
            this.githubClient.checkArchive(var1);
            Platform.runLater(() -> var4.setText("Готово к скачиванию"));
         } catch (Exception var9) {
            Platform.runLater(() -> this.showError("Не удалось проверить ", var9));
         } finally {
            Platform.runLater(() -> {
               var3.setVisible(false);
               var2.setDisable(false);
            });
         }
      });
   }

   private void downloadClient(usa.kitikat.MainShell.MinecraftClient var1, Button var2, ProgressBar var3, Label var4) {
      var2.setDisable(true);
      Thread.startVirtualThread(
         () -> {
            try {
               if (this.githubClient.isInstalled(var1.installDir())) {
                  Platform.runLater(() -> {
                     var4.setText("Сборка уже установлена · запускаем...");
                     var3.setVisible(false);
                     this.launchMinecraft(var1, var4);
                  });
                  return;
               }

               Platform.runLater(() -> {
                  var3.setVisible(true);
                  var3.setProgress(0.0);
                  var4.setText("Подготовка скачивания...");
               });
               this.githubClient
                  .downloadArchive(
                     var1.archiveUrl(),
                     var1.installDir(),
                     var1xx -> Platform.runLater(() -> var3.setProgress(var1xx)),
                     var1xx -> Platform.runLater(() -> var4.setText(var1xx))
                  );
               ClientStore.moveModsOut(var1.installDir(), this.githubClient.remoteModFiles(var1.repository()));
               this.githubClient
                  .updateMods(
                     var1.installDir(),
                     var1.repository(),
                     var1xx -> Platform.runLater(() -> var3.setProgress(var1xx)),
                     var1xx -> Platform.runLater(() -> var4.setText(var1xx))
                  );
               this.rememberModification(var1.name());
               Platform.runLater(() -> {
                  var4.setText("Файлы готовы. Запуск Minecraft...");
                  var3.setVisible(false);
                  this.launchMinecraft(var1, var4);
               });
            } catch (Exception var6) {
               Platform.runLater(() -> {
                  var4.setText("Ошибка скачивания: " + this.safeError(var6));
                  this.showError("Ошибка скачивания Minecraft", var6);
                  var2.setDisable(false);
                  var3.setVisible(false);
               });
            }
         }
      );
   }

   private void writeClientSession() {
      Session var1 = this.accountStore.session();
      if (var1 != null) {
         try {
            if (var1.expiresAt() <= System.currentTimeMillis() / 1000L + 300L) {
               Session var2 = this.authService.refreshIfNeeded(var1);
               if (var2 != null) {
                  var1 = var2;
                  this.accountStore.save(var2);
               }
            }
         } catch (Exception var7) {
         }

         try {
            ObjectMapper var9 = new ObjectMapper();
            ObjectNode var3 = var9.createObjectNode();
            var3.put("uid", var1.uid() == null ? "" : var1.uid());
            var3.put("nick", this.activeUsername() == null ? "" : this.activeUsername());
            var3.put("idToken", var1.idToken() == null ? "" : var1.idToken());
            var3.put("refreshToken", var1.refreshToken() == null ? "" : var1.refreshToken());
            AccountProfile var4 = this.accountStore.profile();
            var3.put("activatedKey", var4 != null && var4.activatedKey() != null ? var4.activatedKey() : "");
            var3.put("role", var4 == null ? "free" : var4.roleOrFree());
            var3.put("hwid", Hwid.get());
            KeySubscription var5 = this.accountStore.subscription();
            var3.put("expiresAt", var5 == null ? 0L : var5.expiresAt());
            var3.put("modification", this.lastModification == null ? "" : this.lastModification);
            var3.put("funpayUrl", this.accountConfig.funpayUrl() == null ? "" : this.accountConfig.funpayUrl());
            var3.put("siteUrl", this.accountConfig.siteUrl() == null ? "" : this.accountConfig.siteUrl());
            var3.put("writtenAt", System.currentTimeMillis());
            Path var6 = Path.of(System.getenv("LOCALAPPDATA"), "AttackDLC", "client-session.json");
            Files.createDirectories(var6.getParent());
            Files.writeString(var6, var9.writeValueAsString(var3));
         } catch (Exception var8) {
            this.logUpdate("не удалось записать файл входа для клиента", var8);
         }
      }
   }

   private void deleteClientSession() {
      try {
         String var1 = System.getenv("LOCALAPPDATA");
         if (var1 == null || var1.isBlank()) {
            return;
         }

         Files.deleteIfExists(Path.of(var1, "AttackDLC", "client-session.json"));
      } catch (Exception var2) {
      }
   }

   private void launchMinecraft(usa.kitikat.MainShell.MinecraftClient var1, Label var2) {
      Thread.startVirtualThread(() -> {
         try {
            this.statusSafe(var2, "Проверяем установленную сборку...");
            this.rememberModification(var1.name());
            ClientStore.moveModsOut(var1.installDir(), this.githubClient.remoteModFiles(var1.repository()));
            this.writeClientSession();
            Process var3 = MinecraftLauncher.launch(var1.installDir(), this.activeUsername());
            Platform.runLater(() -> var2.setText("Minecraft запущен · " + var1.version()));
            var3.onExit().thenRun(() -> {
               int var3x = var3.exitValue();
               String var4x = this.minecraftLauncher.getLastOutput();
               Platform.runLater(() -> {
                  if (var3x == 0) {
                     var2.setText("Minecraft завершил работу.");
                  } else {
                     String var4xx = "Minecraft завершился с кодом " + var3x;
                     if (var4x != null && !var4x.isBlank()) {
                        var4xx = var4xx + "\n\nПоследний вывод Minecraft:\n" + var4x;
                     }

                     var2.setText("Ошибка Minecraft · код " + var3x);
                     this.showError("Minecraft завершился с ошибкой", new RuntimeException(var4xx));
                  }
               });
            });
         } catch (Exception var4) {
            Platform.runLater(() -> {
               var2.setText("Ошибка запуска: " + this.safeError(var4));
               this.showError("Не удалось запустить Minecraft", var4);
            });
         }
      });
   }

   private void showProfile() {
      String var1 = this.activeUsername();
      this.discordUpdate("Профиль", "Смотрит профиль · " + var1);
      VBox var2 = this.page(
         "ПРОФИЛЬ",
         var1,
         this.accountStore.session() == null
            ? "Создай ник с паролем — он подставится в Minecraft при запуске."
            : "Аккаунт привязан: ник подставляется в Minecraft при запуске."
      );
      var2.setId("profile-page");
      var2.getChildren().add(this.accountCard());
      GridPane var3 = new GridPane();
      var3.setHgap(32.0);
      var3.setVgap(15.0);
      var3.setPadding(new Insets(18.0));
      var3.getStyleClass().add("profile-card");
      var3.setMaxWidth(Double.MAX_VALUE);
      this.addInfo(var3, 0, "Имя пользователя Windows", this.username);
      this.addInfo(var3, 1, "Папка Minecraft", this.gameDirectory().toString());
      this.addInfo(var3, 2, "Статус", "Launcher готов к работе");
      var2.getChildren().add(var3);
      this.swap(var2);
   }

   private String activeUsername() {
      String var1 = this.accountStore.nick();
      return var1 == null ? this.username : var1;
   }

   private void updateBarUser() {
      if (this.barUserLabel != null) {
         this.barUserLabel.setText(this.activeUsername());
      }
   }

   private Node accountCard() {
      VBox var1 = new VBox(12.0);
      var1.getStyleClass().add("settings-section");
      var1.setPadding(new Insets(18.0));
      Session var2 = this.accountStore.session();
      if (var2 == null) {
         Label var14 = new Label("Аккаунт");
         var14.getStyleClass().add("section-title");
         Label var15 = new Label("Создай ник с паролем или войди в существующий: ник уникален, сервер не пропустит дубль.");
         var15.getStyleClass().add("muted");
         var15.setWrapText(true);
         TextField var16 = new TextField();
         var16.setPromptText("Ник (3–16 символов: латиница, цифры, «_»)");
         var16.setMaxWidth(280.0);
         PasswordField var17 = new PasswordField();
         var17.setPromptText("Пароль (минимум 6 символов)");
         var17.setMaxWidth(280.0);
         var17.setOnAction(var3x -> this.startPasswordLogin(var16.getText(), var17.getText(), false));
         Label var18 = new Label(this.accountError == null ? "" : this.accountError);
         var18.setStyle("-fx-text-fill:#ff8b8b; -fx-font-size:12px;");
         if (this.accountError == null || this.accountError.isBlank()) {
            var18.setManaged(false);
            var18.setVisible(false);
         }

         Label var19 = new Label(this.accountStatus == null ? "" : this.accountStatus);
         var19.getStyleClass().add("muted");
         if (this.accountStatus == null || this.accountStatus.isBlank()) {
            var19.setManaged(false);
            var19.setVisible(false);
         }

         Button var20 = new Button("Войти");
         var20.getStyleClass().add("primary-button");
         var20.setDisable(this.accountBusy);
         var20.setOnAction(var3x -> this.startPasswordLogin(var16.getText(), var17.getText(), false));
         Button var21 = new Button("Создать аккаунт");
         var21.getStyleClass().add("secondary-button");
         var21.setDisable(this.accountBusy);
         var21.setOnAction(var3x -> this.startPasswordLogin(var16.getText(), var17.getText(), true));
         var1.getChildren().addAll(new Node[]{var14, var15, var16, var17, new HBox(10.0, new Node[]{var20, var21}), var18, var19});
         return var1;
      } else {
         Label var3 = new Label("Аккаунт");
         var3.getStyleClass().add("section-title");
         Label var4 = new Label(var2.nick() != null && !var2.nick().isBlank() ? var2.nick() : "ник ещё не задан");
         var4.getStyleClass().add("card-title");
         AccountProfile var5 = this.accountStore.profile();
         String var6 = var5 == null ? "free" : var5.displayRole();
         Label var7 = new Label(var2.email() == null ? "" : var2.email());
         var7.getStyleClass().add("muted");
         VBox var8 = new VBox(2.0, new Node[]{var4, var7});
         var8.setAlignment(Pos.CENTER_LEFT);
         HBox.setHgrow(var8, Priority.ALWAYS);
         Label var9 = new Label("Роль: " + (var5 != null && var5.isYoutuber() ? "YT" : var6));
         if (var5 != null && var5.isYoutuber()) {
            var9.setStyle(
               "-fx-padding:3 10; -fx-background-radius:99; -fx-border-radius:99; -fx-background-color:#ffffff; -fx-border-color:#ffffff; -fx-text-fill:#0a0a0a; -fx-font-size:11px; -fx-font-weight:bold;"
            );
         } else {
            var9.setStyle(
               "-fx-padding:3 9; -fx-background-radius:99; -fx-border-radius:99; -fx-background-color:rgba(96,165,250,0.14); -fx-border-color:rgba(96,165,250,0.34); -fx-text-fill:#9ecbff; -fx-font-size:11px;"
            );
         }

         HBox var10 = new HBox(12.0, new Node[]{var8, var9});
         var10.setAlignment(Pos.CENTER_LEFT);
         if (var2.photoUrl() != null && !var2.photoUrl().isBlank()) {
            ImageView var11 = new ImageView(new Image(var2.photoUrl(), true));
            var11.setFitWidth(40.0);
            var11.setFitHeight(40.0);
            var11.setPreserveRatio(true);
            var11.setClip(new Circle(20.0, 20.0, 20.0));
            var10.getChildren().add(0, var11);
         }

         Label var22 = new Label("При запуске Minecraft подставляется ник: " + this.activeUsername());
         var22.getStyleClass().add("muted");
         Button var12 = new Button("Выйти");
         var12.getStyleClass().add("ghost-button");
         var12.setOnAction(var1x -> {
            this.accountStore.clear();
            this.deleteClientSession();
            this.updateBarUser();
            this.refreshRpcIdentity();
            this.showProfile();
         });
         HBox var13 = new HBox(10.0, new Node[]{var12});
         var13.setAlignment(Pos.CENTER_LEFT);
         var1.getChildren().addAll(new Node[]{var3, var10, var22, this.accessBlock(var5), this.sessionBlock(), var13});
         if (var5 != null && "owner".equals(var5.roleOrFree())) {
            var1.getChildren().add(this.ownerPanel());
         }

         return var1;
      }
   }

   private Node ownerPanel() {
      VBox var1 = new VBox(12.0);
      var1.getStyleClass().add("settings-section");
      Label var2 = new Label("Управление ключами");
      var2.getStyleClass().add("section-title");
      Label var3 = new Label(
         "Создай ключ: укажи срок, свой текст (или пусто — сгенерируем) и ник, за которым он закрепится. Ключ работает один раз и привязывается к ПК человека."
      );
      var3.getStyleClass().add("muted");
      var3.setWrapText(true);
      TextField var4 = new TextField();
      var4.setPromptText("Текст ключа (пусто — сгенерировать)");
      var4.setPrefWidth(230.0);
      String[] var5 = new String[]{"7 дней", "30 дней", "60 дней", "90 дней", "120 дней", "Навсегда"};
      String[] var6 = new String[]{"30 дней"};
      HBox var7 = new HBox(6.0);
      var7.setAlignment(Pos.CENTER_LEFT);

      for (String var11 : var5) {
         Button var12 = new Button(var11);
         var12.getStyleClass().add("duration-pill");
         var12.setOnAction(var4x -> {
            var6[0] = var11;
            var7.getChildren().forEach(var0x -> {
               if (var0x instanceof Button var1xx) {
                  var1xx.getStyleClass().remove("duration-pill-selected");
               }
            });
            var12.getStyleClass().add("duration-pill-selected");
         });
         if (var11.equals(var6[0])) {
            var12.getStyleClass().add("duration-pill-selected");
         }

         var7.getChildren().add(var12);
      }

      TextField var15 = new TextField();
      var15.setPromptText("Ник аккаунта (необязательно)");
      var15.setPrefWidth(190.0);
      Button var16 = new Button("Создать ключ");
      var16.getStyleClass().add("primary-button");
      var16.setDisable(this.ownerBusy);
      var16.setOnAction(var4x -> this.startCreateKey(var4.getText(), var6[0], var15.getText(), var4, var15));
      HBox var17 = new HBox(8.0, new Node[]{var4, var7, var15, var16});
      var17.setAlignment(Pos.CENTER_LEFT);
      Label var18 = new Label(this.ownerStatus == null ? "" : this.ownerStatus);
      var18.getStyleClass().add("muted");
      if (this.ownerStatus == null || this.ownerStatus.isBlank()) {
         var18.setManaged(false);
         var18.setVisible(false);
      }

      Label var19 = new Label(this.ownerError == null ? "" : this.ownerError);
      var19.setStyle("-fx-text-fill:#ff8b8b; -fx-font-size:12px;");
      if (this.ownerError == null || this.ownerError.isBlank()) {
         var19.setManaged(false);
         var19.setVisible(false);
      }

      VBox var13 = new VBox(6.0);
      var13.getChildren().add(new Label("Загрузка ключей…"));
      Button var14 = new Button("Обновить список");
      var14.getStyleClass().add("ghost-button");
      var14.setDisable(this.ownerBusy);
      var14.setOnAction(var2x -> {
         this.ownerKeysLoaded = false;
         this.startLoadKeys(var13);
      });
      var1.getChildren().addAll(new Node[]{var2, var3, var17, var18, var19, new HBox(10.0, new Node[]{var14}), var13});
      if (!this.ownerKeysLoaded && !this.ownerBusy) {
         this.ownerKeysLoaded = true;
         Thread.startVirtualThread(() -> this.startLoadKeys(var13));
      }

      return var1;
   }

   private void startCreateKey(String var1, String var2, String var3, TextField var4, TextField var5) {
      if (!this.ownerBusy) {
         Session var6 = this.accountStore.session();
         if (var6 != null) {
            String var8 = var2 == null ? "" : var2;

            byte var7 = switch (var8) {
               case "7 дней" -> 7;
               case "60 дней" -> 60;
               case "90 дней" -> 90;
               case "120 дней" -> 120;
               case "Навсегда" -> 0;
               default -> 30;
            };
            this.ownerBusy = true;
            this.ownerStatus = "Создаём ключ…";
            this.ownerError = null;
            this.showProfile();
            Thread.startVirtualThread(() -> {
               try {
                  String var7x = "";
                  if (var3 != null && !var3.isBlank()) {
                     var7x = this.authService.findUidByNick(var6, var3);
                  }

                  String var8x = this.authService.createKey(var6, var1, var7, var7x);
                  this.ownerStatus = "Ключ создан: " + var8x + (var7x.isBlank() ? "" : " · привязан к " + var3.trim());
                  Platform.runLater(() -> {
                     var4.clear();
                     var5.clear();
                  });
               } catch (Exception var12) {
                  this.ownerStatus = null;
                  this.ownerError = this.safeError(var12);
               } finally {
                  this.ownerBusy = false;
                  this.ownerKeysLoaded = false;
                  Platform.runLater(this::showProfile);
               }
            });
         }
      }
   }

   private void startLoadKeys(VBox var1) {
      Session var2 = this.accountStore.session();
      if (var2 != null) {
         this.ownerBusy = true;

         List<KeyInfo> var3;
         try {
            var3 = this.authService.listKeys(var2);
         } catch (Exception var6) {
            this.ownerBusy = false;
            String var5 = "Не загрузились: " + this.safeError(var6);
            Platform.runLater(() -> var1.getChildren().setAll(new Node[]{new Label(var5)}));
            return;
         }

         this.ownerBusy = false;
         Platform.runLater(() -> {
            var1.getChildren().clear();
            if (var3.isEmpty()) {
               var1.getChildren().add(new Label("Ключей пока нет — создай первый выше."));
            } else {
               for (KeyInfo var4 : var3) {
                  var1.getChildren().add(this.keyRow(var4));
               }
            }
         });
      }
   }

   private Node keyRow(KeyInfo var1) {
      String var2 = var1.days() <= 0 ? "навсегда" : var1.days() + " дн.";
      String var3 = var1.free() ? "свободен" : "активирован";
      Label var4 = new Label(var1.key() + "  ·  " + var2 + "  ·  " + var3);
      var4.getStyleClass().add("card-version");
      Button var5 = new Button("Освободить");
      var5.getStyleClass().add("ghost-button");
      var5.setDisable(this.ownerBusy || var1.free());
      var5.setOnAction(
         var2x -> this.startKeyAction(() -> this.authService.releaseKey(this.accountStore.session(), var1.key()), "Ключ " + var1.key() + " освобождён")
      );
      Button var6 = new Button("Удалить");
      var6.getStyleClass().add("ghost-button");
      var6.setDisable(this.ownerBusy);
      var6.setOnAction(
         var2x -> this.startKeyAction(() -> this.authService.deleteKey(this.accountStore.session(), var1.key()), "Ключ " + var1.key() + " удалён")
      );
      HBox var7 = new HBox(10.0, new Node[]{var4, var5, var6});
      var7.setAlignment(Pos.CENTER_LEFT);
      return var7;
   }

   private void startKeyAction(usa.kitikat.MainShell.KeyAction var1, String var2) {
      if (!this.ownerBusy) {
         this.ownerBusy = true;
         this.ownerStatus = "Выполняем…";
         this.ownerError = null;
         this.showProfile();
         Thread.startVirtualThread(() -> {
            try {
               var1.run();
               this.ownerStatus = var2;
            } catch (Exception var7) {
               this.ownerStatus = null;
               this.ownerError = this.safeError(var7);
            } finally {
               this.ownerBusy = false;
               this.ownerKeysLoaded = false;
               Platform.runLater(this::showProfile);
            }
         });
      }
   }

   private void startResetHwid(String var1) {
      Session var2 = this.accountStore.session();
      if (var2 != null && var1 != null && !var1.isBlank()) {
         this.ownerBusy = true;
         this.ownerStatus = "Сбрасываем привязку ПК…";
         this.ownerError = null;
         this.showProfile();
         Thread.startVirtualThread(() -> {
            try {
               this.authService.resetKeyHwid(var2, var1);
               this.ownerStatus = "Привязка ПК сброшена — ключ можно активировать на другом компьютере.";
            } catch (Exception var7) {
               this.ownerStatus = null;
               this.ownerError = this.safeError(var7);
            } finally {
               this.ownerBusy = false;
               Platform.runLater(this::showProfile);
            }
         });
      }
   }

   private Node accessBlock(AccountProfile var1) {
      VBox var2 = new VBox(8.0);
      Label var3 = new Label("Подписка Attack DLC");
      var3.getStyleClass().add("section-title");
      if (var1 != null && var1.hasKey()) {
         KeySubscription var11 = this.accountStore.subscription();
         int var12 = var11 == null ? 0 : var11.daysLeft();
         String var14;
         if (var11 == null) {
            var14 = "Ключ активирован · уточняем срок…";
         } else if (var12 < 0) {
            var14 = "Подписка активна — навсегда.";
         } else if (var12 == 0) {
            var14 = "Срок подписки истёк — активируй новый ключ.";
         } else {
            var14 = "Подписка активна · осталось дней: " + var12;
         }

         Label var16 = new Label(var14);
         var16.getStyleClass().add("muted");
         Label var18 = new Label("Ключ: " + var1.activatedKey());
         var18.getStyleClass().add("card-version");
         Label var20 = new Label("Привязка ПК: " + Hwid.shortForm());
         var20.getStyleClass().add("muted");
         Button var21 = new Button("Сбросить привязку ПК");
         var21.getStyleClass().add("ghost-button");
         var21.setTooltip(new Tooltip("Снимает привязку ключа к этому ПК. После сброса ключ можно активировать на другом компьютере."));
         var21.setOnAction(var2x -> this.startResetHwid(var1.activatedKey()));
         var2.getChildren().addAll(new Node[]{var3, var16, var18, var20, var21});
         return var2;
      } else {
         Label var4 = new Label("Покупай ключ на FunPay и активируй его здесь — доступ привяжется к аккаунту и к этому ПК. Ключ работает только один раз.");
         var4.getStyleClass().add("muted");
         var4.setWrapText(true);
         FlowPane var5 = new FlowPane(8.0, 8.0);

         for (String var9 : new String[]{"7 дней", "30 дней", "60 дней", "90 дней", "120 дней", "Навсегда"}) {
            Label var10 = new Label(var9);
            var10.getStyleClass().add("plan-pill");
            var5.getChildren().add(var10);
         }

         TextField var13 = new TextField();
         var13.setPromptText("Ключ активации (например ATK-XXXX-…)");
         var13.setMaxWidth(320.0);
         Label var15 = new Label(this.keyError == null ? "" : this.keyError);
         var15.setStyle("-fx-text-fill:#ff8b8b; -fx-font-size:12px;");
         if (this.keyError == null || this.keyError.isBlank()) {
            var15.setManaged(false);
            var15.setVisible(false);
         }

         Label var17 = new Label(this.keyStatus == null ? "" : this.keyStatus);
         var17.getStyleClass().add("muted");
         if (this.keyStatus == null || this.keyStatus.isBlank()) {
            var17.setManaged(false);
            var17.setVisible(false);
         }

         Button var19 = new Button("Активировать");
         var19.getStyleClass().add("secondary-button");
         var19.setDisable(this.keyBusy);
         var19.setOnAction(var2x -> this.startKeyActivation(var13.getText()));
         var2.getChildren().addAll(new Node[]{var3, var4, var5, var13, new HBox(10.0, new Node[]{var19}), var15, var17});
         return var2;
      }
   }

   private void startPasswordLogin(String var1, String var2, boolean var3) {
      if (!this.accountBusy) {
         if (!this.authService.configured()) {
            this.showError("Вход не настроен", new IllegalStateException("нет настроек аккаунтов — заполни " + AccountConfig.externalPath()));
         } else {
            this.accountBusy = true;
            this.accountStatus = var3 ? "Создаём аккаунт…" : "Входим…";
            this.accountError = null;
            this.showProfile();
            Thread.startVirtualThread(
               () -> {
                  try {
                     Session var4 = var3 ? this.authService.register(var1, var2) : this.authService.login(var1, var2);
                     AccountProfile var5 = this.authService.fetchProfile(var4);
                     if (var5 == null) {
                        this.authService.ensureProfile(var4);
                        var5 = this.authService.fetchProfile(var4);
                     }

                     if (var5 != null && var5.hasKey()) {
                        KeySubscription var6 = this.authService.fetchKeyInfo(var4, var5.activatedKey());
                        if (var6 != null && var6.hwid() != null && !var6.hwid().isBlank() && !var6.hwid().equalsIgnoreCase(Hwid.get())) {
                           throw new AuthException("Аккаунт привязан к другому компьютеру — напиши владельцу, чтобы перенёс привязку");
                        }
                     }

                     AccountProfile var11 = var5 != null
                        ? var5
                        : new AccountProfile(var4.uid(), var4.nick(), var4.email(), "free", "", System.currentTimeMillis(), "", Hwid.get());
                     if (var11.hwid() == null || var11.hwid().isBlank()) {
                        this.authService.bindHwid(var4);
                     }

                     String var7;
                     try {
                        var7 = this.authService.startSession(var4, var11);
                     } catch (Exception var9) {
                        var7 = null;
                     }

                     String var8 = var7;
                     Platform.runLater(() -> {
                        this.accountBusy = false;
                        this.accountStatus = null;
                        this.accountError = null;
                        this.accountStore.save(var4);
                        this.accountStore.saveProfile(var11);
                        if (var8 != null) {
                           this.accountStore.saveSessionId(var8);
                        }

                        this.updateBarUser();
                        this.refreshRpcIdentity();
                        this.writeClientSession();
                        this.showProfile();
                     });
                  } catch (Exception var10) {
                     Platform.runLater(() -> {
                        this.accountBusy = false;
                        this.accountStatus = null;
                        this.accountError = var10 instanceof AuthException ? var10.getMessage() : "Не удалось выполнить вход";
                        this.showProfile();
                     });
                  }
               }
            );
         }
      }
   }

   private void startKeyActivation(String var1) {
      if (!this.keyBusy && !this.accountBusy) {
         if (this.accountStore.session() != null) {
            this.keyBusy = true;
            this.keyStatus = "Активируем ключ…";
            this.keyError = null;
            this.showProfile();
            Session var2 = this.accountStore.session();
            Thread.startVirtualThread(() -> {
               try {
                  AccountProfile var3 = this.authService.activateKey(var2, var1);
                  KeySubscription var4 = this.fetchSubscription(var2, var3);
                  Platform.runLater(() -> {
                     this.keyBusy = false;
                     this.keyStatus = null;
                     this.keyError = null;
                     if (var3 != null) {
                        this.accountStore.saveProfile(var3);
                     }

                     if (var4 != null) {
                        this.accountStore.saveSubscription(var4);
                     }

                     this.updateBarUser();
                     this.refreshRpcIdentity();
                     this.showProfile();
                  });
               } catch (Exception var5) {
                  Platform.runLater(() -> {
                     this.keyBusy = false;
                     this.keyStatus = null;
                     this.keyError = var5 instanceof AuthException ? var5.getMessage() : "Не удалось активировать ключ";
                     this.showProfile();
                  });
               }
            });
         }
      }
   }

   private void addInfo(GridPane var1, int var2, String var3, String var4) {
      Label var5 = new Label(var3);
      var5.getStyleClass().add("muted");
      Label var6 = new Label(var4 == null ? "—" : var4);
      var6.getStyleClass().add("value");
      var1.add(var5, 0, var2);
      var1.add(var6, 1, var2);
   }

   private void showSettings() {
      this.discordUpdate("Настраивает Attack", "Настройки лаунчера");
      VBox var1 = this.page(
         "НАСТРОЙКИ", "Персонализация", "Масштаб, прозрачность, тема, оформление, фон, анимации, кнопки окна, диск установки и Discord Rich Presence."
      );
      VBox var2 = new VBox(12.0);
      var2.getStyleClass().add("settings-section");
      Label var3 = new Label("Размер окна");
      var3.getStyleClass().add("section-title");
      Label var4 = new Label("Масштаб интерфейса: окно, шрифты (включая боковое меню) и все элементы.");
      var4.getStyleClass().add("muted");
      HBox var5 = new HBox(10.0);
      double var6 = this.preferences.getDouble("uiScale", 1.0);

      for (double var11 : new double[]{0.5, 0.75, 1.0}) {
         Button var13 = new Button(Math.round(var11 * 100.0) + "%");
         boolean var14 = Math.abs(var11 - var6) < 0.001;
         var13.getStyleClass().add(var14 ? "primary-button" : "secondary-button");
         var13.setOnAction(var3x -> {
            this.preferences.putDouble("uiScale", var11);
            if (this.scaleApplier != null) {
               this.scaleApplier.accept(var11);
            }

            this.showSettings();
         });
         var5.getChildren().add(var13);
      }

      var2.setMaxWidth(Double.MAX_VALUE);
      var2.getChildren().addAll(new Node[]{var3, var4, var5});
      double var64 = this.preferences.getDouble("windowOpacity", 0.9);
      Label var65 = new Label(Math.round(var64 * 100.0) + "%");
      var65.getStyleClass().add("value");
      Slider var66 = new Slider(0.55, 1.0, var64);
      var66.setShowTickLabels(false);
      var66.setShowTickMarks(false);
      HBox var12 = new HBox(14.0, new Node[]{var65, var66});
      var12.setAlignment(Pos.CENTER_LEFT);
      HBox.setHgrow(var66, Priority.ALWAYS);
      var66.valueProperty().addListener((var2x, var3x, var4x) -> {
         this.setWindowOpacity(var4x.doubleValue());
         var65.setText(Math.round(var4x.doubleValue() * 100.0) + "%");
      });
      VBox var67 = this.settingsSection(
         "Прозрачность окна",
         "От 55% до 100%. Сквозь окно и сквозь карточки всегда видно обои — панели останутся полупрозрачными на любой прозрачности.",
         var12
      );
      double var68 = this.preferences.getDouble("panelDensity", 1.0);
      Label var16 = new Label(Math.round(var68 * 100.0) + "%");
      var16.getStyleClass().add("value");
      Slider var17 = new Slider(0.4, 1.0, var68);
      var17.setShowTickLabels(false);
      var17.setShowTickMarks(false);
      HBox var18 = new HBox(14.0, new Node[]{var16, var17});
      var18.setAlignment(Pos.CENTER_LEFT);
      HBox.setHgrow(var17, Priority.ALWAYS);
      var17.valueProperty().addListener((var2x, var3x, var4x) -> {
         this.preferences.putDouble("panelDensity", var4x.doubleValue());
         var16.setText(Math.round(var4x.doubleValue() * 100.0) + "%");
         this.applyAccentTheme();
      });
      VBox var19 = this.settingsSection(
         "Плотность панелей и карточек", "100% — как в стиле лаунчера. Меньше — секции, карточки и кнопки прозрачнее, сквозь них сильнее видно обои.", var18
      );
      HBox var20 = new HBox(10.0);
      var20.setAlignment(Pos.CENTER_LEFT);

      for (Option var22 : AccentTheme.OPTIONS) {
         StackPane var23 = new StackPane();
         var23.getStyleClass().add("accent-swatch");
         var23.setStyle("-fx-background-color: " + var22.hex() + ";");
         if (var22.id().equalsIgnoreCase(this.accentId)) {
            var23.getStyleClass().add("selected");
         }

         Tooltip.install(var23, new Tooltip(var22.name()));
         var23.setOnMouseClicked(var2x -> {
            this.accentId = var22.id();
            this.preferences.put("accent", this.accentId);
            this.applyAccentTheme();
            this.showSettings();
         });
         var20.getChildren().add(var23);
      }

      Label var69 = new Label("Текущая тема: " + AccentTheme.byId(this.accentId).name());
      var69.getStyleClass().add("setting-label");
      VBox var70 = this.settingsSection("Тема", "Акцентный цвет: кнопки, слайдеры, чекбоксы, подсветка карточек и меню.", var20, var69);
      CheckBox var71 = new CheckBox("Аниме-пак — милые иконки в меню вместо штриховых");
      var71.getStyleClass().add("settings-check");
      var71.setSelected(this.animeIcons);
      var71.selectedProperty().addListener((var1x, var2x, var3x) -> {
         this.animeIcons = var3x;
         this.preferences.putBoolean("animeIcons", var3x);
         this.refreshNavIcons();
      });
      CheckBox var24 = new CheckBox("Цвет из фона — акцент берётся из текущих обоев");
      var24.getStyleClass().add("settings-check");
      var24.setSelected(this.accentFromWallpaper);
      var24.selectedProperty().addListener((var1x, var2x, var3x) -> {
         this.accentFromWallpaper = var3x;
         this.preferences.putBoolean("accentFromWallpaper", var3x);
         this.accentFromWallpaperHex = null;
         this.applyAccentTheme();
      });
      VBox var25 = this.settingsSection(
         "Оформление",
         "Аниме-пак меняет иконки бокового меню. «Цвет из фона» перекрашивает лаунчер под главный цвет обоев — при смене фона цвет обновится сам.",
         var71,
         var24
      );
      VBox var26 = new VBox(12.0);
      var26.getStyleClass().add("settings-section");
      Label var27 = new Label("Фон лаунчера");
      var27.getStyleClass().add("section-title");
      Label var28 = new Label("Выберите фон, затемнение и живую анимацию.");
      var28.getStyleClass().add("muted");
      FlowPane var29 = new FlowPane(10.0, 10.0);

      for (int var30 = 1; var30 <= 6; var30++) {
         var29.getChildren().add(this.wallpaperChoice(var30));
      }

      Button var72 = new Button("Выбрать изображение…");
      var72.getStyleClass().add("secondary-button");
      var72.setOnAction(var1x -> this.chooseWallpaper());
      Slider var31 = new Slider(0.0, 0.65, this.preferences.getDouble("wallpaperDim", 0.28));
      var31.setShowTickLabels(false);
      var31.setShowTickMarks(false);
      var31.valueProperty().addListener((var1x, var2x, var3x) -> this.setWallpaperDim(var3x.doubleValue()));
      Label var32 = new Label("Затемнение фона");
      var32.getStyleClass().add("setting-label");
      CheckBox var33 = new CheckBox("Живой фон — плавное «дыхание» обоев");
      var33.getStyleClass().add("settings-check");
      var33.setSelected(this.liveWallpaper);
      var33.selectedProperty().addListener((var1x, var2x, var3x) -> {
         this.liveWallpaper = var3x;
         this.preferences.putBoolean("liveWallpaper", var3x);
         this.startWallpaperPulse();
      });
      double var34 = this.preferences.getDouble("pulseAmount", 0.06);
      Label var36 = new Label("Сила «дыхания» — " + Math.round(var34 * 100.0) + "%");
      var36.getStyleClass().add("setting-label");
      Slider var37 = new Slider(0.0, 0.12, var34);
      var37.setShowTickLabels(false);
      var37.setShowTickMarks(false);
      var37.valueProperty().addListener((var2x, var3x, var4x) -> {
         this.preferences.putDouble("pulseAmount", var4x.doubleValue());
         var36.setText("Сила «дыхания» — " + Math.round(var4x.doubleValue() * 100.0) + "%");
         this.startWallpaperPulse();
      });
      double var38 = this.preferences.getDouble("pulseSeconds", 10.0);
      Label var40 = new Label("Скорость «дыхания» — " + Math.round(var38) + " с на цикл");
      var40.getStyleClass().add("setting-label");
      Slider var41 = new Slider(4.0, 24.0, var38);
      var41.setShowTickLabels(false);
      var41.setShowTickMarks(false);
      var41.valueProperty().addListener((var2x, var3x, var4x) -> {
         this.preferences.putDouble("pulseSeconds", var4x.doubleValue());
         var40.setText("Скорость «дыхания» — " + Math.round(var4x.doubleValue()) + " с на цикл");
         this.startWallpaperPulse();
      });
      var26.setMaxWidth(Double.MAX_VALUE);
      var26.getChildren().addAll(new Node[]{var27, var28, var29, var72, var32, var31, var33, var36, var37, var40, var41});
      CheckBox var42 = new CheckBox("Анимации интерфейса (смена страниц и появление карточек)");
      var42.getStyleClass().add("settings-check");
      var42.setSelected(this.animationsEnabled);
      var42.selectedProperty().addListener((var1x, var2x, var3x) -> {
         this.animationsEnabled = var3x;
         this.preferences.putBoolean("uiAnimations", var3x);
      });
      double var43 = this.preferences.getDouble("animSpeed", 1.0);
      HBox var45 = new HBox(10.0);
      LinkedHashMap<String, Double> var46 = new LinkedHashMap<>();
      var46.put("Быстро", Double.valueOf(0.6));
      var46.put("Обычно", Double.valueOf(1.0));
      var46.put("Медленно", Double.valueOf(1.6));

      for (Entry<String, Double> var48 : var46.entrySet()) {
         Button var49 = new Button((String)var48.getKey());
         boolean var50 = Math.abs((Double)var48.getValue() - var43) < 0.001;
         var49.getStyleClass().add(var50 ? "primary-button" : "secondary-button");
         var49.setOnAction(var2x -> {
            this.preferences.putDouble("animSpeed", (Double)var48.getValue());
            this.animSpeed = (Double)var48.getValue();
            this.showSettings();
         });
         var45.getChildren().add(var49);
      }

      VBox var73 = this.settingsSection(
         "Интерфейс", "Отключите анимации или задайте их скорость — переходы между страницами и появление карточек.", var42, var45
      );
      VBox var74 = new VBox(10.0);
      var74.getStyleClass().add("settings-section");
      Label var75 = new Label("Discord Rich Presence");
      var75.getStyleClass().add("section-title");
      Label var76 = new Label("Показывает, что вы делаете: смотрите игру, настраиваете лаунчер или во что-то играете.");
      var76.getStyleClass().add("muted");
      CheckBox var51 = new CheckBox("Включить Rich Presence");
      var51.getStyleClass().add("settings-check");
      var51.setSelected(this.discordRpcEnabled);
      var51.selectedProperty().addListener((var1x, var2x, var3x) -> {
         this.discordRpcEnabled = var3x;
         this.preferences.putBoolean("discordRpc", var3x);
         if (var3x) {
            this.refreshRpcIdentity();
         } else {
            this.discordUpdate(null, null);
         }
      });
      Label var52 = new Label(this.runningGame != null ? "Сейчас в системе: " + this.runningGame : "Запущенных игр в системе нет");
      var52.getStyleClass().add("status");
      this.rpcStatusLabel = var52;
      var74.setMaxWidth(Double.MAX_VALUE);
      var74.getChildren().addAll(new Node[]{var75, var76, var51, var52});
      List<String> var53 = this.installDrives();
      String var54 = this.installDrive();
      if (!var53.contains(var54)) {
         var53.add(0, var54);
      }

      HBox var55 = new HBox(6.0);
      var55.setAlignment(Pos.CENTER_LEFT);

      for (String var57 : var53) {
         Button var58 = new Button(var57);
         var58.getStyleClass().add("duration-pill");
         var58.setPrefSize(50.0, 28.0);
         var58.setOnAction(var2x -> {
            this.preferences.put("installDrive", var57);
            this.showSettings();
         });
         if (var57.equals(var54)) {
            var58.getStyleClass().add("duration-pill-selected");
         }

         var55.getChildren().add(var58);
      }

      Label var77 = new Label(this.driveSpace(var54));
      var77.getStyleClass().add("value");
      HBox var78 = new HBox(12.0, new Node[]{var55, var77});
      var78.setAlignment(Pos.CENTER_LEFT);
      VBox var79 = this.settingsSection("Диск установки", null, var78);
      VBox var59 = this.settingsSection(
         "Кнопки окна",
         "Те же два варианта, что были при первоначальной настройке: точки как в macOS или обычные кнопки под дизайн лаунчера. Шапка окна меняется сразу.",
         this.windowControlsChoice()
      );
      Button var60 = new Button("Проверить обновления");
      var60.getStyleClass().add("secondary-button");
      Label var61 = new Label("Текущая версия: " + UpdateChecker.currentVersion());
      var61.getStyleClass().add("status");
      HBox var62 = new HBox(12.0, new Node[]{var60, var61});
      var62.setAlignment(Pos.CENTER_LEFT);
      var62.getStyleClass().add("updates-row");
      var60.setOnAction(var3x -> {
         String var4x = UpdateChecker.currentVersion();
         if ("dev".equals(var4x)) {
            var61.setText("Dev-сборка без версии — обновляться нечем.");
         } else {
            var60.setDisable(true);
            this.statusSafe(var61, "Проверяем обновления Launch…");
            Thread var5x = new Thread(() -> {
               try {
                  Info var4xx = UpdateChecker.fetch(UpdateChecker.versionUrl());
                  if (UpdateChecker.isNewer(var4x, var4xx.version())) {
                     this.statusSafe(var61, "Доступна версия " + var4xx.version() + " — качаем в фоне");
                     this.logUpdate("доступна версия " + var4xx.version() + ": " + (var4xx.notes() == null ? "" : var4xx.notes()));
                     Platform.runLater(() -> this.startBackgroundUpdate(var4xx));
                  } else {
                     this.statusSafe(var61, "У вас последняя версия (" + var4x + ")");
                  }
               } catch (Throwable var8) {
                  this.statusSafe(var61, "Ошибка: " + this.safeError(var8));
               } finally {
                  Platform.runLater(() -> var60.setDisable(false));
               }
            }, "update-check");
            var5x.setDaemon(true);
            var5x.start();
         }
      });
      VBox var63 = this.settingsSection("Обновления", null, var62);
      var1.getChildren().addAll(new Node[]{var2, var67, var19, var70, var25, var26, var73, var59, var79, var63, var74});
      this.swap(var1);
   }

   private VBox settingsSection(String var1, String var2, Node... var3) {
      VBox var4 = new VBox(12.0);
      var4.getStyleClass().add("settings-section");
      var4.setMaxWidth(Double.MAX_VALUE);
      Label var5 = new Label(var1);
      var5.getStyleClass().add("section-title");
      var4.getChildren().add(var5);
      if (var2 != null && !var2.isBlank()) {
         Label var6 = new Label(var2);
         var6.getStyleClass().add("muted");
         var6.setWrapText(true);
         var4.getChildren().add(var6);
      }

      var4.getChildren().addAll(var3);
      return var4;
   }

   private StackPane wallpaperChoice(int var1) {
      StackPane var2 = new StackPane();
      var2.getStyleClass().add("wallpaper-choice");
      var2.setPrefSize(150.0, 84.0);
      var2.setClip(this.roundedClip(150.0, 84.0, 10.0));
      ImageView var3 = new ImageView(new Image(this.getClass().getResourceAsStream("/assets/wallpapers/wallpaper" + var1 + ".jpg")));
      var3.setFitWidth(150.0);
      var3.setFitHeight(84.0);
      var3.setPreserveRatio(false);
      var3.setSmooth(true);
      Rectangle var4 = new Rectangle(150.0, 84.0);
      var4.setArcWidth(18.0);
      var4.setArcHeight(18.0);
      var3.setClip(var4);
      var3.getStyleClass().add("wallpaper-thumb");
      var2.getChildren().add(var3);
      var2.setOnMouseClicked(var2x -> this.loadWallpaper("/assets/wallpapers/wallpaper" + var1 + ".jpg"));
      return var2;
   }

   private void chooseWallpaper() {
      FileChooser var1 = new FileChooser();
      var1.setTitle("Выберите фон");
      var1.getExtensionFilters().add(new ExtensionFilter("Изображения", new String[]{"*.png", "*.jpg", "*.jpeg", "*.webp"}));
      File var2 = var1.showOpenDialog(this.stage);
      if (var2 != null) {
         this.loadWallpaper(var2.getAbsolutePath());
      }
   }

   private void checkClientUpdatesOnStart() {
      long var1 = System.currentTimeMillis();
      if (var1 - this.lastClientCheck >= 300000L) {
         this.lastClientCheck = var1;
         Thread var3 = new Thread(
            () -> {
               try {
                  Thread.sleep(1200L);
               } catch (InterruptedException var12) {
                  Thread.currentThread().interrupt();
               }

               ArrayList<MinecraftClient> var1x = new ArrayList<>();

               for (usa.kitikat.MainShell.MinecraftClient var3x : this.minecraftClients()) {
                  if (this.githubClient.isInstalled(var3x.installDir())) {
                     var1x.add(var3x);
                  }
               }

               StringBuilder var14 = new StringBuilder();

               for (usa.kitikat.MainShell.MinecraftClient var4 : var1x) {
                  if (var14.length() > 0) {
                     var14.append(", ");
                  }

                  var14.append(var4.name());
               }

               this.logUpdate("проверка клиентов: установлено " + var1x.size() + (var14.length() == 0 ? "" : " (" + var14 + ")"));
               KeySetView<String, Boolean> var16 = ConcurrentHashMap.newKeySet();
               ConcurrentHashMap<String, ModsDiff> var17 = new ConcurrentHashMap<>();

               try (ExecutorService var5 = Executors.newVirtualThreadPerTaskExecutor()) {
                  ArrayList<Future<?>> var6 = new ArrayList<>();

                  for (usa.kitikat.MainShell.MinecraftClient var8 : var1x) {
                     var6.add(var5.submit(() -> {
                        try {
                           ModsDiff var4x = this.githubClient.checkMods(var8.installDir(), var8.repository(), this.clientAddonFiles());
                           var16.add(var8.name());
                           if (!var4x.isEmpty()) {
                              var17.put(var8.name(), var4x);
                           }

                           this.logUpdate("сверка " + var8.name() + ": докачать " + var4x.download().size() + ", удалить " + var4x.remove().size());
                        } catch (Throwable var5x) {
                           this.logUpdate("сверка " + var8.name() + " не удалась", var5x);
                        }

                        return null;
                     }));
                  }

                  for (Future<?> var22 : var6) {
                     try {
                        var22.get();
                     } catch (Exception var11) {
                     }
                  }
               }

               ArrayList<MinecraftClient> var18 = new ArrayList<>();

               for (usa.kitikat.MainShell.MinecraftClient var21 : var1x) {
                  if (var16.contains(var21.name())) {
                     if (var17.containsKey(var21.name())) {
                        this.clientUpdates.put(var21.name(), (ModsDiff)var17.get(var21.name()));
                        var18.add(var21);
                     } else {
                        this.clientUpdates.remove(var21.name());
                     }

                     this.setMarker(var21.name(), var17.containsKey(var21.name()));
                  }
               }

               this.logUpdate(
                  "проверка завершена: проверено " + var16.size() + " из " + var1x.size() + ", с обновлениями " + var17.keySet() + ", к показу " + var18.size()
               );
               Platform.runLater(() -> {
                  this.refreshClientCards();
                  if (!var18.isEmpty()) {
                     this.showClientUpdatesDialog(var18);
                  }
               });
            },
            "client-update-check"
         );
         var3.setDaemon(true);
         var3.start();
      }
   }

   private void setMarker(String var1, boolean var2) {
      boolean var3 = var2 ? this.updateMarkers.add(var1) : this.updateMarkers.remove(var1);
      if (var3) {
         this.preferences.put("clientUpdateMarkers", String.join(",", this.updateMarkers));
      }
   }

   private void refreshClientCards() {
      if (this.clientsGrid != null && this.clientsGrid.getScene() != null) {
         this.showMinecraftClients();
      }
   }

   private void logUpdate(String var1) {
      try {
         String var2 = System.getenv("LOCALAPPDATA");
         Path var3 = Path.of(var2 != null && !var2.isBlank() ? var2 : System.getProperty("java.io.tmpdir", "."), "AttackDLC");
         Files.createDirectories(var3);
         String var4 = LocalDateTime.now().withNano(0) + "  " + var1 + System.lineSeparator();
         Files.write(var3.resolve("update-check.log"), var4.getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
      } catch (Exception var5) {
      }
   }

   private void logUpdate(String var1, Throwable var2) {
      if (var2 == null) {
         this.logUpdate(var1);
      } else {
         StringBuilder var3 = new StringBuilder(var1).append(": ").append(var2.getClass().getName());
         if (var2.getMessage() != null) {
            var3.append(" — ").append(var2.getMessage());
         }

         Throwable var4 = var2.getCause();
         if (var4 != null) {
            var3.append(" (причина: ").append(var4.getClass().getName());
            if (var4.getMessage() != null) {
               var3.append(" — ").append(var4.getMessage());
            }

            var3.append(')');
         }

         this.logUpdate(var3.toString());
      }
   }

   private void showClientUpdatesDialog(List<usa.kitikat.MainShell.MinecraftClient> var1) {
      if (this.clientUpdateOverlay == null) {
         StackPane var2 = new StackPane();
         var2.getStyleClass().add("onboarding-overlay");
         var2.setManaged(false);
         var2.setLayoutX(0.0);
         var2.setLayoutY(0.0);
         this.clientUpdateOverlay = var2;
         VBox var3 = new VBox(14.0);
         var3.getStyleClass().add("onboarding-card");
         var3.setPadding(new Insets(26.0, 28.0, 28.0, 28.0));
         var3.setMaxWidth(560.0);
         var3.setMaxHeight(560.0);
         var3.setAlignment(Pos.CENTER_LEFT);
         Label var4 = new Label("ОБНОВЛЕНИЕ КЛИЕНТОВ");
         var4.getStyleClass().add("eyebrow");
         Label var5 = new Label("Найдены новые версии");
         var5.getStyleClass().add("title-large");
         Label var6 = new Label(
            "Лаунчер сверил папку mods/ с сервером: изменённые файлы заменятся, пропавшие оттуда удалятся. Остальные файлы сборки не трогаются."
         );
         var6.getStyleClass().add("muted");
         var6.setWrapText(true);
         ArrayList<MinecraftClient> var7 = new ArrayList<>();
         ArrayList<CheckBox> var8 = new ArrayList<>();
         VBox var9 = new VBox(6.0);

         for (usa.kitikat.MainShell.MinecraftClient var11 : var1) {
            ModsDiff var12 = this.clientUpdates.get(var11.name());
            if (var12 != null) {
               StringBuilder var13 = new StringBuilder();
               var13.append(var12.download().size()).append(" файл(ов) заменится");
               if (!var12.remove().isEmpty()) {
                  var13.append(", ").append(var12.remove().size()).append(" удалится");
               }

               CheckBox var14 = new CheckBox(var11.name() + " — " + var13);
               var14.getStyleClass().add("settings-check");
               var14.setSelected(true);
               var7.add(var11);
               var8.add(var14);
               var9.getChildren().add(var14);
            }
         }

         ProgressBar var19 = new ProgressBar(0.0);
         var19.setMaxWidth(Double.MAX_VALUE);
         var19.setPrefHeight(8.0);
         var19.setVisible(false);
         var19.setManaged(false);
         Label var20 = new Label("Отметьте клиенты и нажмите «Обновить».");
         var20.getStyleClass().add("status");
         var20.setWrapText(true);
         Button var21 = new Button("Обновить выбранное");
         var21.getStyleClass().add("primary-button");
         Button var22 = new Button("Обновить всё");
         var22.getStyleClass().add("secondary-button");
         Button var23 = new Button("Позже");
         var23.getStyleClass().add("ghost-button");
         var23.setOnAction(var1x -> this.closeClientUpdates());
         Button[] var15 = new Button[]{var21, var22, var23};
         var21.setOnAction(var6x -> {
            ArrayList<MinecraftClient> var7x = new ArrayList<>();

            for (int var8x = 0; var8x < var8.size(); var8x++) {
               if (((CheckBox)var8.get(var8x)).isSelected()) {
                  var7x.add((usa.kitikat.MainShell.MinecraftClient)var7.get(var8x));
               }
            }

            if (var7x.isEmpty()) {
               var20.setText("Отметьте хотя бы одного клиента.");
            } else {
               this.runClientUpdate(var7x, var19, var20, var15);
            }
         });
         var22.setOnAction(var5x -> this.runClientUpdate(var7, var19, var20, var15));
         HBox var16 = new HBox(9.0, new Node[]{var21, var22, var23});
         var16.setAlignment(Pos.CENTER_LEFT);
         var3.getChildren().addAll(new Node[]{var4, var5, var6, var9, var19, var20, var16});
         var2.getChildren().add(var3);
         this.getChildren().add(var2);
         Runnable var17 = () -> var2.resize(this.getWidth(), this.getHeight());
         this.widthProperty().addListener((var1x, var2x, var3x) -> var17.run());
         this.heightProperty().addListener((var1x, var2x, var3x) -> var17.run());
         var17.run();
         if (this.animationsEnabled) {
            var2.setOpacity(0.0);
            FadeTransition var18 = new FadeTransition(Duration.millis(280.0 * this.animSpeed), var2);
            var18.setToValue(1.0);
            var18.play();
         }
      }
   }

   private void runClientUpdate(List<usa.kitikat.MainShell.MinecraftClient> var1, ProgressBar var2, Label var3, Button[] var4) {
      for (Button var8 : var4) {
         var8.setDisable(true);
      }

      var2.setVisible(true);
      var2.setManaged(true);
      var2.setProgress(0.0);
      Thread var9 = new Thread(
         () -> {
            int var5 = 0;
            int var6 = 0;

            for (int var7 = 0; var7 < var1.size(); var7++) {
               usa.kitikat.MainShell.MinecraftClient var8x = (usa.kitikat.MainShell.MinecraftClient)var1.get(var7);
               int var9x = var7;
               this.statusSafe(var3, var8x.name() + ": подготовка…");

               try {
                  this.githubClient
                     .updateMods(
                        var8x.installDir(),
                        var8x.repository(),
                        var3xx -> Platform.runLater(() -> var2.setProgress(((double)var9x + var3xx) / (double)var1.size())),
                        var3xx -> this.statusSafe(var3, var8x.name() + " · " + var3xx),
                        this.clientAddonFiles()
                     );
                  this.clientUpdates.remove(var8x.name());
                  this.setMarker(var8x.name(), false);
                  var5++;
               } catch (Throwable var11) {
                  var6++;
                  this.statusSafe(var3, var8x.name() + " — ошибка: " + this.safeError(var11));
               }
            }

            int var12 = var5;
            int var13 = var6;
            Platform.runLater(() -> {
               var2.setVisible(false);
               var2.setManaged(false);
               if (var13 <= 0) {
                  var3.setText("Обновлено клиентов: " + var12 + ".");
                  this.closeClientUpdates();
               } else {
                  var3.setText("Обновлено: " + var12 + ", с ошибкой: " + var13 + ". Попробуйте ещё раз.");

                  for (Button var9xx : var4) {
                     var9xx.setDisable(false);
                  }
               }
            });
         },
         "client-updater"
      );
      var9.setDaemon(true);
      var9.start();
   }

   private void checkClientUpdate(usa.kitikat.MainShell.MinecraftClient var1, Button var2, ProgressBar var3, Label var4) {
      if (!this.githubClient.isInstalled(var1.installDir())) {
         this.checkManifest(var1.archiveUrl(), var2, var3, var4);
      } else {
         var2.setDisable(true);
         var3.setVisible(true);
         var3.setProgress(-1.0);
         var4.setText("Сверяем папку mods/ с сервером…");
         Thread.startVirtualThread(
            () -> {
               try {
                  ModsDiff var5 = this.githubClient.checkMods(var1.installDir(), var1.repository(), this.clientAddonFiles());
                  if (var5.isEmpty()) {
                     this.clientUpdates.remove(var1.name());
                     this.setMarker(var1.name(), false);
                     Platform.runLater(() -> var4.setText("Обновлений нет — установлена последняя версия."));
                  } else {
                     this.clientUpdates.put(var1.name(), var5);
                     this.setMarker(var1.name(), true);
                     ArrayList<MinecraftClient> var6 = new ArrayList<>();
                     var6.add(var1);
                     Platform.runLater(
                        () -> {
                           var4.setText(
                              "Доступно обновление: "
                                 + var5.download().size()
                                 + " файл(ов)"
                                 + (var5.remove().isEmpty() ? "" : ", удалится " + var5.remove().size())
                           );
                           this.showClientUpdatesDialog(var6);
                        }
                     );
                  }
               } catch (Throwable var10) {
                  Platform.runLater(() -> var4.setText("Не удалось проверить: " + this.safeError(var10)));
               } finally {
                  Platform.runLater(() -> {
                     var3.setVisible(false);
                     var2.setDisable(false);
                  });
               }
            }
         );
      }
   }

   private void closeClientUpdates() {
      if (this.clientUpdateOverlay != null) {
         this.getChildren().remove(this.clientUpdateOverlay);
         this.clientUpdateOverlay = null;
      }
   }

   private static Path updateDir() {
      String var0 = System.getenv("LOCALAPPDATA");
      if (var0 == null || var0.isBlank()) {
         var0 = System.getProperty("user.home", ".");
      }

      return Path.of(var0, "AttackDLC", "launcher-update");
   }

   private static void migrateFromOldLauncher() {
      try {
         String var0 = System.getenv("LOCALAPPDATA");
         if (var0 == null || var0.isBlank()) {
            return;
         }

         Path var1 = Path.of(var0, "NemesisLauncher");
         Path var2 = Path.of(var0, "AttackDLC");
         if (!Files.isDirectory(var1)) {
            return;
         }

         if (Files.isDirectory(var2)) {
            return;
         }

         Files.createDirectories(var2);

         for (Path var4 : Files.list(var1).toList()) {
            String var5 = var4.getFileName().toString();
            if (!var5.endsWith(".log") && !var5.endsWith(".logs") && !"update".equals(var5) && !"plugins".equals(var5)) {
               try {
                  Files.move(var4, var2.resolve(var5), StandardCopyOption.REPLACE_EXISTING);
               } catch (Exception var7) {
               }
            }
         }

         System.out.println("[AttackLauncher] migrated data from NemesisLauncher → AttackDLC");
      } catch (Throwable var8) {
         System.err.println("[AttackLauncher] migrate failed: " + var8.getMessage());
      }
   }

   private static Path restartFlag() {
      return updateDir().resolve("restart.flag");
   }

   private void startBackgroundUpdate(Info var1) {
      if (!this.backgroundUpdateRunning) {
         Path var2 = UpdateChecker.appDirectory();
         if (var2 != null) {
            this.backgroundUpdateRunning = true;
            this.pendingUpdateVersion = var1.version();
            this.showUpdateDock("Обновление " + var1.version(), "Скачивание…", 0.0);
            Thread var3 = new Thread(() -> {
               try {
                  Path var2x = Files.createDirectories(updateDir());
                  Path var3x = var2x.resolve("pending.zip");
                  UpdateChecker.download(var1.url(), var3x, var1xx -> Platform.runLater(() -> {
                        double var3xx = Math.max(0.0, Math.min(1.0, var1xx));
                        this.updateDockBar.setProgress(var3xx);
                        if (var1xx >= 0.0) {
                           this.updateDockLabel.setText(String.format(Locale.ROOT, "Скачивание %d%%", Math.round(var3xx * 100.0)));
                        }
                     }), var1xx -> Platform.runLater(() -> this.updateDockLabel.setText(var1xx)));
                  this.pendingUpdateZip = var3x;
                  Platform.runLater(() -> {
                     this.updateDockBar.setProgress(1.0);
                     this.updateDockLabel.setText("Обновление " + var1.version() + " готово — перезапустите лаунчер");
                     this.updateDockAction.setText("Перезапустить");
                     this.updateDockAction.setVisible(true);
                     this.updateDockAction.setDisable(false);
                  });
               } catch (Throwable var4) {
                  Platform.runLater(() -> {
                     this.backgroundUpdateRunning = false;
                     this.updateDockLabel.setText("Не удалось скачать обновление: " + this.safeError(var4));
                     this.updateDockAction.setVisible(false);
                  });
               }
            }, "update-download");
            var3.setDaemon(true);
            var3.start();
         }
      }
   }

   private void showUpdateDock(String var1, String var2, double var3) {
      if (this.updateDock == null) {
         this.updateDock = new HBox(12.0);
         this.updateDock.getStyleClass().add("update-dock");
         this.updateDock.setAlignment(Pos.CENTER_LEFT);
         this.updateDock.setVisible(false);
         Label var5 = new Label();
         var5.getStyleClass().add("update-dock-title");
         this.updateDockTitle = var5;
         this.updateDockBar = new ProgressBar(0.0);
         this.updateDockBar.setPrefWidth(180.0);
         this.updateDockBar.setPrefHeight(6.0);
         this.updateDockBar.setMaxHeight(6.0);
         this.updateDockLabel = new Label();
         this.updateDockLabel.getStyleClass().add("update-dock-status");
         this.updateDockAction = new Button("Перезапустить");
         this.updateDockAction.getStyleClass().add("primary-button");
         this.updateDockAction.setVisible(false);
         this.updateDockAction.setOnAction(var1x -> this.applyPendingUpdate());
         Button var6 = new Button("×");
         var6.getStyleClass().add("ghost-button");
         var6.setOnAction(var1x -> this.updateDock.setVisible(false));
         this.updateDock.getChildren().addAll(new Node[]{var5, this.updateDockBar, this.updateDockLabel, this.updateDockAction, var6});
         this.content.getChildren().add(this.updateDock);
         StackPane.setAlignment(this.updateDock, Pos.BOTTOM_CENTER);
         StackPane.setMargin(this.updateDock, new Insets(0.0, 0.0, 14.0, 0.0));
      }

      this.updateDockTitle.setText(var1);
      this.updateDockLabel.setText(var2);
      this.updateDockBar.setProgress(var3);
      this.updateDock.setVisible(true);
   }

   private void applyPendingUpdate() {
      Path var1 = UpdateChecker.appDirectory();
      if (var1 != null && this.pendingUpdateZip != null) {
         try {
            Path var2 = restartFlag();
            Files.createDirectories(var2.getParent());
            Files.writeString(var2, "restart");
            UpdateChecker.spawnUpdate(var1, this.pendingUpdateZip, var2);
            this.stage.close();
         } catch (Throwable var3) {
            this.showError("Не удалось обновить лаунчер", var3);
         }
      }
   }

   private void checkUpdatesOnStart() {
      if (!"dev".equals(UpdateChecker.currentVersion())) {
         this.applyPendingOnExit();
         Thread var1 = new Thread(() -> {
            try {
               Info var1x = UpdateChecker.fetch(UpdateChecker.versionUrl());
               if (UpdateChecker.isNewer(UpdateChecker.currentVersion(), var1x.version())) {
                  Platform.runLater(() -> this.startBackgroundUpdate(var1x));
               }
            } catch (Throwable var2) {
            }
         }, "update-check");
         var1.setDaemon(true);
         var1.start();
      }
   }

   private void applyPendingOnExit() {
      try {
         Path var1 = updateDir().resolve("pending.zip");
         Path var2 = UpdateChecker.appDirectory();
         if (var2 == null || !Files.isRegularFile(var1) || Files.size(var1) <= 0L) {
            return;
         }

         UpdateChecker.spawnUpdate(var2, var1, null);
         this.logUpdate("скачанное обновление будет установлено при выходе");
      } catch (Throwable var3) {
      }
   }

   private void closeUpdate() {
      if (this.updateOverlay != null) {
         this.getChildren().remove(this.updateOverlay);
         this.updateOverlay = null;
      }
   }

   private void showOnboarding() {
      StackPane var1 = new StackPane();
      var1.getStyleClass().add("onboarding-overlay");
      var1.setManaged(false);
      var1.setLayoutX(0.0);
      var1.setLayoutY(0.0);
      VBox var2 = new VBox(14.0);
      var2.getStyleClass().add("onboarding-card");
      var2.setPadding(new Insets(26.0, 28.0, 28.0, 28.0));
      var2.setMaxWidth(600.0);
      var2.setMaxHeight(480.0);
      var2.setAlignment(Pos.CENTER_LEFT);
      Label var3 = new Label("ПЕРВОНАЧАЛЬНАЯ НАСТРОЙКА");
      var3.getStyleClass().add("eyebrow");
      Label var4 = new Label("Настройка окна");
      var4.getStyleClass().add("title-large");
      Label var5 = new Label("Один шаг: выберите, как выглядят кнопки окна. Всё остальное — в настройках, потом.");
      var5.getStyleClass().add("muted");
      var5.setWrapText(true);
      Label var6 = new Label("Стиль кнопок окна");
      var6.getStyleClass().add("section-title");
      HBox var7 = this.windowControlsChoice();
      Button var8 = new Button("Продолжить");
      var8.getStyleClass().add("primary-button");
      var8.setMaxWidth(Double.MAX_VALUE);
      var8.setOnAction(var2x -> {
         this.preferences.putBoolean("onboardingDone", true);
         this.getChildren().remove(var1);
      });
      var2.getChildren().addAll(new Node[]{var3, var4, var5, var6, var7, var8});
      var1.getChildren().add(var2);
      this.getChildren().add(var1);
      Runnable var9 = () -> var1.resize(this.getWidth(), this.getHeight());
      this.widthProperty().addListener((var1x, var2x, var3x) -> var9.run());
      this.heightProperty().addListener((var1x, var2x, var3x) -> var9.run());
      var9.run();
      if (this.animationsEnabled) {
         var1.setOpacity(0.0);
         FadeTransition var10 = new FadeTransition(Duration.millis(280.0 * this.animSpeed), var1);
         var10.setToValue(1.0);
         var10.play();
      }
   }

   private HBox windowControlsChoice() {
      String[] var1 = new String[]{this.preferences.get("windowControls", "dots")};
      VBox[] var2 = new VBox[2];
      Runnable var3 = () -> {
         boolean var3x = "dots".equals(var1[0]);
         this.markSelected(var2[0], var3x);
         this.markSelected(var2[1], !var3x);
      };
      Consumer<String> var4 = var3x -> {
         var1[0] = var3x;
         this.preferences.put("windowControls", var3x);
         this.buildTitlebar();
         var3.run();
      };
      var2[0] = this.choiceOption(
         "Точки как в macOS", "Три кружка слева: закрыть, свернуть, развернуть.", this.previewControls(true), () -> var4.accept("dots")
      );
      var2[1] = this.choiceOption(
         "Обычные кнопки", "Знаки справа: свернуть, развернуть, закрыть — в стиле лаунчера.", this.previewControls(false), () -> var4.accept("classic")
      );
      var3.run();
      HBox var5 = new HBox(12.0, new Node[]{var2[0], var2[1]});
      var5.setAlignment(Pos.CENTER_LEFT);
      return var5;
   }

   private VBox choiceOption(String var1, String var2, Node var3, Runnable var4) {
      VBox var5 = new VBox(9.0);
      var5.getStyleClass().addAll(new String[]{"client-card", "choice-option"});
      var5.setPadding(new Insets(15.0));
      var5.setPrefWidth(252.0);
      var5.setMaxWidth(252.0);
      var5.setAlignment(Pos.CENTER_LEFT);
      var5.setOnMouseClicked(var1x -> var4.run());
      Label var6 = new Label(var1);
      var6.getStyleClass().add("card-title");
      Label var7 = new Label(var2);
      var7.getStyleClass().add("card-version");
      var7.setWrapText(true);
      var5.getChildren().addAll(new Node[]{var3, var6, var7});
      return var5;
   }

   private void markSelected(VBox var1, boolean var2) {
      String var3 = "selected";
      if (var2) {
         if (!var1.getStyleClass().contains(var3)) {
            var1.getStyleClass().add(var3);
         }
      } else {
         var1.getStyleClass().remove(var3);
      }
   }

   private HBox previewControls(boolean var1) {
      HBox var2 = new HBox(7.0);
      var2.setAlignment(Pos.CENTER_LEFT);
      String[] var3 = var1 ? new String[]{"close", "min", "max"} : new String[]{"min", "max", "close"};

      for (String var7 : var3) {
         Button var8 = var1 ? this.dot(var7) : this.winButton(var7);
         var8.setMouseTransparent(true);
         var2.getChildren().add(var8);
      }

      return var2;
   }

   private VBox page(String var1, String var2, String var3) {
      VBox var4 = new VBox(12.0);
      var4.setPadding(new Insets(24.0, 28.0, 28.0, 28.0));
      var4.setMaxWidth(Double.MAX_VALUE);
      var4.setFillWidth(true);
      Label var5 = new Label(var1);
      var5.getStyleClass().add("eyebrow");
      Label var6 = new Label(var2);
      var6.getStyleClass().add("title-large");
      Label var7 = new Label(var3);
      var7.getStyleClass().add("muted");
      var4.getChildren().addAll(new Node[]{var5, var6, var7});
      return var4;
   }

   private void statusSafe(Label var1, String var2) {
      Platform.runLater(() -> var1.setText(var2));
   }

   private String safeError(Throwable var1) {
      String var2 = var1.getMessage();
      return var2 != null && !var2.isBlank() ? var2 : var1.getClass().getSimpleName();
   }

   private void showError(String var1, Throwable var2) {
      if (this.errorOverlay != null) {
         this.getChildren().remove(this.errorOverlay);
         this.errorOverlay = null;
      }

      StackPane var3 = new StackPane();
      var3.getStyleClass().add("onboarding-overlay");
      var3.setManaged(false);
      var3.setLayoutX(0.0);
      var3.setLayoutY(0.0);
      this.errorOverlay = var3;
      VBox var4 = new VBox(14.0);
      var4.getStyleClass().add("onboarding-card");
      var4.setPadding(new Insets(26.0, 28.0, 28.0, 28.0));
      var4.setMaxWidth(540.0);
      var4.setMaxHeight(Double.NEGATIVE_INFINITY);
      var4.setAlignment(Pos.CENTER_LEFT);
      Label var5 = new Label("ОШИБКА");
      var5.getStyleClass().add("error-eyebrow");
      Label var6 = new Label(var1);
      var6.getStyleClass().add("title-large");
      var6.setWrapText(true);
      Label var7 = new Label(this.safeError(var2));
      var7.getStyleClass().add("error-message");
      var7.setWrapText(true);
      Object var8 = var7;
      if (this.safeError(var2).length() > 300 || this.safeError(var2).contains("\n")) {
         ScrollPane var9 = new ScrollPane(var7);
         var9.getStyleClass().add("error-scroll");
         var9.setFitToWidth(true);
         var9.setPannable(true);
         var9.setVbarPolicy(ScrollBarPolicy.AS_NEEDED);
         var9.setMaxHeight(220.0);
         var8 = var9;
      }

      Button var14 = new Button("Копировать текст");
      var14.getStyleClass().add("ghost-button");
      var14.setOnAction(var4x -> {
         ClipboardContent var5x = new ClipboardContent();
         var5x.putString(var1 + "\n\n" + this.safeError(var2));
         Clipboard.getSystemClipboard().setContent(var5x);
         var14.setText("Скопировано");
      });
      Button var10 = new Button("Закрыть");
      var10.getStyleClass().add("primary-button");
      var10.setOnAction(var2x -> {
         this.getChildren().remove(var3);
         this.errorOverlay = null;
      });
      HBox var11 = new HBox(10.0, new Node[]{var14, var10});
      var11.setAlignment(Pos.CENTER_RIGHT);
      var4.getChildren().addAll(new Node[]{var5, var6, (Node)var8, var11});
      var3.getChildren().add(var4);
      this.getChildren().add(var3);
      Runnable var12 = () -> var3.resize(this.getWidth(), this.getHeight());
      this.widthProperty().addListener((var1x, var2x, var3x) -> var12.run());
      this.heightProperty().addListener((var1x, var2x, var3x) -> var12.run());
      var12.run();
      if (this.animationsEnabled) {
         var3.setOpacity(0.0);
         FadeTransition var13 = new FadeTransition(Duration.millis(240.0 * this.animSpeed), var3);
         var13.setToValue(1.0);
         var13.play();
      }
   }

   private ImageView image(String var1, double var2, double var4) {
      return new ImageView(new Image(this.getClass().getResourceAsStream(var1), var2, var4, true, true));
   }

   void applyDiscordConfig(DiscordRPC var1) {
      if (var1 != null) {
         var1.configure("attackdlc", "Attack DLC", "Покупка ключа", this.accountConfig.funpayUrl(), "Сайт", this.accountConfig.siteUrl());
      }
   }

   private void refreshProfileAsync() {
      Session var1 = this.accountStore.session();
      if (var1 != null && this.authService.configured()) {
         Thread.startVirtualThread(() -> {
            try {
               AccountProfile var2 = this.authService.fetchProfile(var1);
               if (var2 == null) {
                  return;
               }

               KeySubscription var3 = this.fetchSubscription(var1, var2);
               Platform.runLater(() -> {
                  this.applyFreshProfile(var2);
                  if (var3 != null) {
                     this.accountStore.saveSubscription(var3);
                  }
               });
            } catch (Exception var4) {
            }
         });
      }
   }

   private KeySubscription fetchSubscription(Session var1, AccountProfile var2) {
      if (var2 != null && var2.hasKey()) {
         try {
            KeySubscription var3 = this.authService.fetchKeyInfo(var1, var2.activatedKey());
            return var3 != null && var3.ownedBy(var1.uid()) ? var3 : null;
         } catch (Exception var4) {
            return null;
         }
      } else {
         return null;
      }
   }

   private void applyFreshProfile(AccountProfile var1) {
      if (!var1.sessionMatches(this.accountStore.sessionId())) {
         this.accountStore.clear();
         this.deleteClientSession();
         this.updateBarUser();
         this.accountError = "Сессия закрыта: вход выполнен с другого устройства";
         this.refreshRpcIdentity();
      } else {
         this.accountStore.saveProfile(var1);
         this.writeClientSession();
         this.updateBarUser();
         this.refreshRpcIdentity();
      }
   }

   private boolean isProfilePage() {
      return this.pageLayer.getChildren().stream().anyMatch(var0 -> "profile-page".equals(var0.getId()));
   }

   private void startSessionWatcher() {
      Thread var1 = new Thread(() -> {
         while (!Thread.currentThread().isInterrupted()) {
            try {
               Thread.sleep(60000L);
            } catch (InterruptedException var4) {
               return;
            }

            Session var1x = this.accountStore.session();
            if (var1x != null && this.authService.configured()) {
               try {
                  AccountProfile var2 = this.authService.fetchProfile(var1x);
                  if (var2 == null) {
                     continue;
                  }

                  Platform.runLater(() -> {
                     if (this.accountStore.session() != null) {
                        boolean var2x = this.isProfilePage();
                        this.applyFreshProfile(var2);
                        if (var2x) {
                           this.showProfile();
                        }
                     }
                  });
               } catch (Exception var3) {
               }

               this.writeClientSession();
            }
         }
      }, "attack-session-watcher");
      var1.setDaemon(true);
      this.sessionWatcher = var1;
      var1.start();
   }

   private void rotateSession() {
      Session var1 = this.accountStore.session();
      if (var1 != null && !this.sessionBusy) {
         this.sessionBusy = true;
         this.sessionStatus = "Закрываем другие входы…";
         this.sessionError = null;
         this.showProfile();
         Thread.startVirtualThread(() -> {
            try {
               String var2 = this.authService.startSession(var1, this.accountStore.profile());
               Platform.runLater(() -> {
                  this.sessionBusy = false;
                  this.sessionStatus = null;
                  this.sessionError = null;
                  this.accountStore.saveSessionId(var2);
                  this.showProfile();
               });
            } catch (Exception var3) {
               Platform.runLater(() -> {
                  this.sessionBusy = false;
                  this.sessionStatus = null;
                  this.sessionError = var3 instanceof AuthException ? var3.getMessage() : "Не удалось закрыть другие входы";
                  this.showProfile();
               });
            }
         });
      }
   }

   private Node sessionBlock() {
      VBox var1 = new VBox(8.0);
      Label var2 = new Label("Устройства");
      var2.getStyleClass().add("section-title");
      Label var3 = new Label("Вход выполняется только на одном ПК: как только ты зайдёшь с другого, прошлый компьютер закроется сам.");
      var3.getStyleClass().add("muted");
      var3.setWrapText(true);
      Button var4 = new Button("Выйти со всех устройок");
      var4.getStyleClass().add("ghost-button");
      var4.setDisable(this.sessionBusy);
      var4.setOnAction(var1x -> this.rotateSession());
      Label var5 = new Label(this.sessionError == null ? "" : this.sessionError);
      var5.setStyle("-fx-text-fill:#ff8b8b; -fx-font-size:12px;");
      if (this.sessionError == null || this.sessionError.isBlank()) {
         var5.setManaged(false);
         var5.setVisible(false);
      }

      Label var6 = new Label(this.sessionStatus == null ? "" : this.sessionStatus);
      var6.getStyleClass().add("muted");
      if (this.sessionStatus == null || this.sessionStatus.isBlank()) {
         var6.setManaged(false);
         var6.setVisible(false);
      }

      var1.getChildren().addAll(new Node[]{var2, var3, var4, var5, var6});
      return var1;
   }

   private void refreshRpcIdentity() {
      if (this.discordRpcEnabled) {
         String var1 = this.lastModification.isBlank() ? "Attack Launcher" : this.lastModification;
         AccountProfile var2 = this.accountStore.profile();
         String var3 = this.accountStore.nick();
         String var4;
         if (var3 != null && !var3.isBlank()) {
            String var5 = var2 == null ? "free" : var2.displayRole();
            var4 = "Role: " + var5 + " · " + var3;
         } else {
            var4 = "Не в аккаунте";
         }

         this.discordUpdate(var1, var4);
      }
   }

   private void rememberModification(String var1) {
      if (var1 != null && !var1.isBlank()) {
         this.lastModification = var1;
         this.preferences.put("lastModification", var1);
         if (this.runningGame == null) {
            this.refreshRpcIdentity();
         }
      }
   }

   private void discordUpdate(String var1, String var2) {
      try {
         if (this.discordUpdateCallback == null) {
            return;
         }

         if (var1 == null && var2 == null) {
            this.discordUpdateCallback.accept(null, null);
            return;
         }

         if (!this.discordRpcEnabled) {
            return;
         }

         this.lastDetails = var1;
         this.lastState = var2;
         if (this.runningGame != null) {
            this.discordUpdateCallback.accept("Играет: " + this.runningGame, var2);
            return;
         }

         this.discordUpdateCallback.accept(var1, var2);
      } catch (Exception var4) {
      }
   }

   private void setWindowOpacity(double var1) {
      double var3 = Math.max(0.5, Math.min(1.0, var1));
      this.preferences.putDouble("windowOpacity", var3);

      try {
         this.stage.setOpacity(var3);
      } catch (Exception var6) {
      }
   }

   private void applyAccentTheme() {
      try {
         String var1 = null;
         if (this.accentFromWallpaper) {
            if (this.accentFromWallpaperHex == null) {
               this.accentFromWallpaperHex = this.pickWallpaperAccent();
            }

            var1 = this.accentFromWallpaperHex;
         }

         if (var1 == null || !var1.matches("#[0-9a-fA-F]{6}")) {
            var1 = AccentTheme.byId(this.accentId).hex();
         }

         String var2 = AccentTheme.write(var1, this.preferences.getDouble("panelDensity", 1.0));
         Platform.runLater(() -> this.attachAccentCss(var2));
      } catch (Exception var3) {
      }
   }

   private Set<String> clientAddonFiles() {
      String var1 = this.preferences.get("clientAddonFiles", "");
      LinkedHashSet var2 = new LinkedHashSet();

      for (String var6 : var1.split(",")) {
         if (!var6.isBlank()) {
            var2.add(var6.trim());
         }
      }

      return var2;
   }

   private void attachAccentCss(String var1) {
      Scene var2 = this.getScene();
      if (var2 != null) {
         if (this.accentCssUrl != null) {
            var2.getStylesheets().remove(this.accentCssUrl);
         }

         if (!var2.getStylesheets().contains(var1)) {
            var2.getStylesheets().add(var1);
         }

         var2.getRoot().applyCss();
      }

      this.accentCssUrl = var1;
   }

   private void startWallpaperPulse() {
      this.stopWallpaperPulse();
      if (!this.liveWallpaper) {
         this.wallpaperView.setScaleX(1.0);
         this.wallpaperView.setScaleY(1.0);
      } else {
         double var1 = Math.max(0.0, Math.min(0.15, this.preferences.getDouble("pulseAmount", 0.06)));
         double var3 = Math.max(3.0, Math.min(30.0, this.preferences.getDouble("pulseSeconds", 10.0)));
         double var5 = 1.0 + var1;
         this.wallpaperPulse = new Timeline(
            new KeyFrame[]{
               new KeyFrame(
                  Duration.seconds(var3),
                  new KeyValue[]{
                     new KeyValue(this.wallpaperView.scaleXProperty(), var5, Interpolator.EASE_BOTH),
                     new KeyValue(this.wallpaperView.scaleYProperty(), var5, Interpolator.EASE_BOTH)
                  }
               )
            }
         );
         this.wallpaperPulse.setCycleCount(-1);
         this.wallpaperPulse.setAutoReverse(true);
         this.wallpaperPulse.play();
      }
   }

   private void stopWallpaperPulse() {
      if (this.wallpaperPulse != null) {
         this.wallpaperPulse.stop();
         this.wallpaperPulse = null;
      }
   }

   private void startGameWatcher() {
      try {
         this.gameWatcher = new GameActivityWatcher(var1 -> Platform.runLater(() -> this.onRunningGame(var1)));
      } catch (Throwable var2) {
         this.gameWatcher = null;
      }
   }

   private void onRunningGame(String var1) {
      this.runningGame = var1;
      if (var1 != null) {
         this.discordUpdateNow("Играет: " + var1, this.lastState);
      } else if (this.lastDetails != null) {
         this.discordUpdateNow(this.lastDetails, this.lastState);
      }

      if (this.rpcStatusLabel != null) {
         this.rpcStatusLabel.setText(var1 != null ? "Сейчас в системе: " + var1 : "Запущенных игр в системе нет");
      }
   }

   private void discordUpdateNow(String var1, String var2) {
      try {
         if (this.discordUpdateCallback == null || !this.discordRpcEnabled) {
            return;
         }

         this.discordUpdateCallback.accept(var1, var2);
      } catch (Exception var4) {
      }
   }

   public void shutdown() {
      this.stopSidebarAnims();
      this.stopWallpaperPulse();
      if (this.sessionWatcher != null) {
         this.sessionWatcher.interrupt();
         this.sessionWatcher = null;
      }

      if (this.gameWatcher != null) {
         this.gameWatcher.close();
         this.gameWatcher = null;
      }
   }

   @FunctionalInterface
   private interface KeyAction {
      void run() throws Exception;
   }

   private static record MinecraftClient(
      String name, String version, String description, String picturePath, String iconPath, String repository, String archiveUrl, Path installDir
   ) {
   }

   private static record NavIconSlot(String kind, StackPane slot, List<Node> extras) {
   }
}
