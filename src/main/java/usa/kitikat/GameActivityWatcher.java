package usa.kitikat;

import java.nio.file.Path;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * Ищет запущенные на компьютере игры и сообщает об этом колбэку.
 *
 * <p>Нужен для Rich Presence: «Играет: Counter-Strike 2», «Играет: Valorant»…
 * Пока игра запущена — активность показывает игру, как только она закрыта,
 * колбэк получает {@code null} и лаунчер возвращает своё состояние.</p>
 *
 * <p>Minecraft (java.exe / javaw.exe) намеренно не отслеживается: сам лаунчер
 * работает на Java, иначе был бы постоянный ложный срабатывания.</p>
 */
public final class GameActivityWatcher implements AutoCloseable {

    /** Имя процесса (lower-case) -> название игры для Discord. */
    private static final Map<String, String> GAMES = new LinkedHashMap<>();

    static {
        GAMES.put("cs2.exe", "Counter-Strike 2");
        GAMES.put("csgo.exe", "Counter-Strike: Global Offensive");
        GAMES.put("dota2.exe", "Dota 2");
        GAMES.put("dota2launcher.exe", "Dota 2");
        GAMES.put("valorant-win64-shipping.exe", "Valorant");
        GAMES.put("gta5.exe", "Grand Theft Auto V");
        GAMES.put("gta5_be.exe", "Grand Theft Auto V");
        GAMES.put("league of legends.exe", "League of Legends");
        GAMES.put("rocketleague.exe", "Rocket League");
        GAMES.put("fortniteclient-win64-shipping.exe", "Fortnite");
        GAMES.put("rustclient.exe", "Rust");
        GAMES.put("deadlock.exe", "Deadlock");
        GAMES.put("eldenring.exe", "Elden Ring");
        GAMES.put("osu.exe", "Osu!");
        GAMES.put("sof2.exe", "Soldier of Fortune 2");
        GAMES.put("robloxplayerbeta.exe", "Roblox");
        GAMES.put("windows10universal.exe", "Roblox");
    }

    private final ScheduledExecutorService executor =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread thread = new Thread(r, "nemesis-game-watch");
                thread.setDaemon(true);
                return thread;
            });

    /** Вызывается из фонового потока: имя игры или null. */
    private final Consumer<String> callback;

    private volatile String current;
    private volatile boolean running = true;

    public GameActivityWatcher(Consumer<String> callback) {
        this.callback = callback;
        executor.scheduleWithFixedDelay(this::scan, 2, 5, TimeUnit.SECONDS);
    }

    /** Текущая найденная игра (или null). */
    public String currentGame() {
        return current;
    }

    private void scan() {
        if (!running) {
            return;
        }

        String found = null;

        try (Stream<ProcessHandle> processes = ProcessHandle.allProcesses()) {
            Iterator<ProcessHandle> iterator = processes.iterator();
            while (iterator.hasNext()) {
                ProcessHandle handle = iterator.next();
                Optional<String> command = handle.info().command();
                if (command.isEmpty()) {
                    continue;
                }
                String name = Path.of(command.get()).getFileName().toString().toLowerCase(Locale.ROOT);
                String game = GAMES.get(name);
                if (game != null) {
                    found = game;
                    break;
                }
            }
        } catch (Throwable ignored) {
            return;
        }

        if (Objects.equals(found, current)) {
            return;
        }

        current = found;
        try {
            callback.accept(found);
        } catch (Throwable ignored) {
        }
    }

    @Override
    public void close() {
        running = false;
        executor.shutdownNow();
        current = null;
    }

    /** Названия игр для подсказок/настроек. */
    public static Set<String> knownGames() {
        return Set.copyOf(GAMES.values());
    }
}
