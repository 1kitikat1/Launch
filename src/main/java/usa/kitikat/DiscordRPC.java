package usa.kitikat;

import com.google.gson.JsonObject;
import com.jagrosh.discordipc.IPCClient;
import com.jagrosh.discordipc.IPCListener;
import com.jagrosh.discordipc.entities.RichPresence;
import com.jagrosh.discordipc.entities.User;
import com.jagrosh.discordipc.entities.pipe.PipeStatus;
import com.jagrosh.discordipc.entities.Packet;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public final class DiscordRPC implements AutoCloseable, IPCListener {

    private static final long APPLICATION_ID =
            1550164242844487850L;

    private final ScheduledExecutorService executor =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread thread = new Thread(
                        r,
                        "attack-discord-rpc"
                );

                thread.setDaemon(true);
                return thread;
            });

    private volatile IPCClient client;

    private volatile String details =
            "Attack Launcher";

    private volatile String state =
            "Browsing games";

    /*
     * Аватарка клиента и кнопки-ссылки в RPC.
     * Задаются один раз из настроек; дальше подставляются в каждый presence.
     */
    private volatile String largeImageKey = "attackdlc";

    private volatile String largeImageText = "Attack DLC";

    private volatile String button1Label = "";

    private volatile String button1Url = "";

    private volatile String button2Label = "";

    private volatile String button2Url = "";

    /*
     * Timestamp текущей активности.
     *
     * Он НЕ меняется при обычном update().
     * Меняется только когда мы реально начинаем
     * новую активность.
     */
    private volatile long activityStart =
            System.currentTimeMillis();

    private volatile boolean running;

    private volatile boolean schedulerStarted;

    /*
     * =========================================================
     * START
     * =========================================================
     */

    public synchronized void start(
            String details,
            String state
    ) {

        this.details = normalizeDetails(details);
        this.state = normalizeState(state);

        /*
         * Первый запуск лаунчера.
         */
        if (!running) {
            running = true;

            /*
             * Timestamp лаунчера начинается именно сейчас.
             */
            activityStart = System.currentTimeMillis();
        }

        /*
         * Если Discord уже подключён —
         * просто обновляем Activity.
         */
        if (isConnected()) {
            updatePresence();
            return;
        }

        /*
         * Подключаемся.
         */
        executor.execute(this::connectNow);

        /*
         * Запускаем reconnect loop только один раз.
         */
        if (!schedulerStarted) {

            schedulerStarted = true;

            executor.scheduleWithFixedDelay(
                    () -> {

                        if (!running) {
                            return;
                        }

                        if (!isConnected()) {
                            connectNow();
                        }

                    },
                    10,
                    10,
                    TimeUnit.SECONDS
            );
        }
    }

    /*
     * =========================================================
     * CONNECT
     * =========================================================
     */

    private synchronized void connectNow() {

        if (!running || isConnected()) {
            return;
        }

        try {

            IPCClient next =
                    new IPCClient(APPLICATION_ID);

            next.setListener(this);

            client = next;

            next.connect();

        } catch (Throwable ignored) {

            IPCClient failed = client;

            client = null;

            try {

                if (failed != null) {
                    failed.close();
                }

            } catch (Throwable ignoredClose) {
            }
        }
    }

    /*
     * =========================================================
     * NORMAL UPDATE
     * =========================================================
     *
     * Используется при переходе:
     *
     * Games
     * Minecraft
     * Lineage
     * Settings
     *
     * Timestamp НЕ сбрасывается.
     */

    public void update(
            String details,
            String state
    ) {

        this.details = normalizeDetails(details);
        this.state = normalizeState(state);

        if (isConnected()) {
            updatePresence();
        }
    }

    /*
     * =========================================================
     * NEW ACTIVITY
     * =========================================================
     *
     * Используется именно при запуске игры.
     *
     * Например:
     *
     * Playing Minecraft
     *
     * После этого Discord начинает новый таймер.
     */

    public void startActivity(
            String details,
            String state
    ) {

        String next = normalizeDetails(details);

        /*
         * Таймер сбрасываем ТОЛЬКО когда активность реально новая:
         *
         * - первая игра после запуска лаунчера
         * - другая игра
         * - та же игра после того, как её закрыли
         *   (между ними в details стояло состояние лаунчера)
         *
         * Пока игра та же, обычные действия в лаунчере —
         * переходы по страницам, настройки, профиль —
         * шлют тот же details и НЕ трогают таймер.
         */
        boolean newActivity =
                !next.equals(this.details) || !running;

        this.details = next;
        this.state = normalizeState(state);

        if (newActivity) {
            activityStart =
                    System.currentTimeMillis();
        }

        if (!running) {
            running = true;
        }

        if (isConnected()) {

            updatePresence();

        } else {

            executor.execute(this::connectNow);
        }
    }

    /*
     * =========================================================
     * RETURN TO LAUNCHER
     * =========================================================
     *
     * После закрытия игры возвращаемся
     * к состоянию лаунчера.
     */

    public void returnToLauncher() {

        this.details =
                "Attack Launcher";

        this.state =
                "Browsing games";

        /*
         * Начинаем новый launcher activity.
         */
        activityStart =
                System.currentTimeMillis();

        if (isConnected()) {
            updatePresence();
        }
    }

    /*
     * Аватарка клиента и ссылки-кнопки для Discord RPC.
     *
     * Кнопки необязательны: пустые подписи просто не добавляются.
     */
    public void configure(
            String largeImageKey,
            String largeImageText,
            String button1Label,
            String button1Url,
            String button2Label,
            String button2Url
    ) {
        this.largeImageKey = largeImageKey == null ? "" : largeImageKey.trim();
        this.largeImageText = largeImageText == null ? "" : largeImageText.trim();
        this.button1Label = button1Label == null ? "" : button1Label.trim();
        this.button1Url = button1Url == null ? "" : button1Url.trim();
        this.button2Label = button2Label == null ? "" : button2Label.trim();
        this.button2Url = button2Url == null ? "" : button2Url.trim();
        if (isConnected()) {
            updatePresence();
        }
    }

    /*
     * =========================================================
     * PRESENCE
     * =========================================================
     */

    private void updatePresence() {

        IPCClient current =
                client;

        if (current == null) {
            return;
        }

        try {

            RichPresence.Builder builder =
                    new RichPresence.Builder()

                            .setDetails(details)

                            .setState(state)

                            .setStartTimestamp(
                                    activityStart
                            );

            if (!largeImageKey.isBlank()) {
                builder.setLargeImageWithTooltip(
                        largeImageKey,
                        largeImageText.isBlank()
                                ? "Attack DLC" : largeImageText
                );
            }

            com.google.gson.JsonArray buttons =
                    buildButtons();

            if (buttons.size() > 0) {
                builder.setButtons(buttons);
            }

            current.sendRichPresence(
                    builder.build()
            );

        } catch (Throwable ignored) {
        }
    }

    /** Кнопки-ссылки (FunPay/сайт); пустые подписи пропускаем. */
    private com.google.gson.JsonArray buildButtons() {

        com.google.gson.JsonArray array =
                new com.google.gson.JsonArray();

        addButton(array, button1Label, button1Url);
        addButton(array, button2Label, button2Url);

        return array;
    }

    private static void addButton(
            com.google.gson.JsonArray array,
            String label,
            String url
    ) {

        if (label == null || label.isBlank()
                || url == null || url.isBlank()) {
            return;
        }

        com.google.gson.JsonObject button =
                new com.google.gson.JsonObject();

        button.addProperty("label", label);
        button.addProperty("url", url);

        array.add(button);
    }

    /*
     * =========================================================
     * CONNECTION
     * =========================================================
     */

    private boolean isConnected() {

        IPCClient current =
                client;

        if (current == null) {
            return false;
        }

        try {

            return current.getStatus()
                    == PipeStatus.CONNECTED;

        } catch (Throwable ignored) {

            return false;
        }
    }

    /*
     * =========================================================
     * LISTENER
     * =========================================================
     */

    @Override
    public void onReady(
            IPCClient client
    ) {

        updatePresence();
    }

    @Override
    public void onClose(
            IPCClient client,
            JsonObject json
    ) {

        if (this.client == client) {
            this.client = null;
        }
    }

    @Override
    public void onDisconnect(
            IPCClient client,
            Throwable throwable
    ) {

        if (this.client == client) {
            this.client = null;
        }
    }

    @Override
    public void onPacketSent(
            IPCClient client,
            Packet packet
    ) {
    }

    @Override
    public void onPacketReceived(
            IPCClient client,
            Packet packet
    ) {
    }

    @Override
    public void onActivityJoin(
            IPCClient client,
            String secret
    ) {
    }

    @Override
    public void onActivitySpectate(
            IPCClient client,
            String secret
    ) {
    }

    @Override
    public void onActivityJoinRequest(
            IPCClient client,
            String secret,
            User user
    ) {
    }

    /*
     * Оставляем для совместимости с твоей версией библиотеки.
     */
    public void onActivityJoinRequest(
            IPCClient client,
            String user
    ) {
    }

    /*
     * =========================================================
     * CLOSE
     * =========================================================
     */

    @Override
    public synchronized void close() {

        running = false;

        IPCClient current =
                client;

        client = null;

        if (current != null) {

            try {
                current.close();
            } catch (Throwable ignored) {
            }
        }

        /*
         * ВАЖНО:
         *
         * executor НЕ shutdownNow().
         *
         * Иначе после logout -> login
         * этот же DiscordRPC больше нельзя использовать.
         */

        activityStart =
                System.currentTimeMillis();
    }

    /*
     * =========================================================
     * HELPERS
     * =========================================================
     */

    private static String normalizeDetails(
            String value
    ) {

        if (value == null ||
                value.isBlank()) {

            return "Attack Launcher";
        }

        return value;
    }

    private static String normalizeState(
            String value
    ) {

        if (value == null ||
                value.isBlank()) {

            return "Browsing games";
        }

        return value;
    }
}