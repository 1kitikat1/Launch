package usa.kitikat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Хранилище клиента вне папки Minecraft.
 *
 * <p>Клиент лежит не в {@code mods} инстанса, а в отдельной папке
 * {@code %LOCALAPPDATA%\AttackDLC\store\<инстанс>}. При запуске лаунчер
 * передаёт Fabric'у путь через системное свойство
 * {@code -Dfabric.addMods=...}, и загрузчик подхватывает моды оттуда.</p>
 *
 * <p>Смысл: папка {@code mods} инстанса остаётся пустой — сканер, который
 * её открывает, не находит ничего. Настоящий клиент при этом лежит в
 * другом месте.</p>
 *
 * <p>Требование загрузчика к файлу: расширение {@code .jar}, имя не
 * начинается с точки, файл не скрытый.</p>
 */
public final class ClientStore {

    private ClientStore() {
    }

    /** Корень хранилища: {@code %LOCALAPPDATA%\AttackDLC\store}. */
    public static Path storeRoot() {
        String local = System.getenv("LOCALAPPDATA");
        if (local == null || local.isBlank()) {
            local = System.getProperty("user.home", ".");
        }
        return Path.of(local, "AttackDLC", "store");
    }

    /** Папка клиента для конкретного инстанса. */
    public static Path modsDir(Path installDir) {
        String name = installDir == null ? "default"
                : installDir.getFileName().toString();
        Path target = storeRoot().resolve(name);
        // Фоллбэк на старое имя при миграции: minecraft → AttackDLC, Nemesis → N4em и т.п.
        // Проверяем не только существование, но и наличие jar-файлов — пустая папка не считается хранилищем.
        if (!hasModJars(target)) {
            String legacy = LEGACY_MIGRATIONS.get(name);
            if (legacy != null) {
                Path alt = storeRoot().resolve(legacy);
                if (hasModJars(alt)) {
                    return alt;
                }
            }
        }
        return target;
    }

    private static boolean hasModJars(Path dir) {
        try {
            if (!Files.isDirectory(dir)) return false;
            try (var stream = Files.list(dir)) {
                return stream.anyMatch(p -> p.toString().toLowerCase().endsWith(".jar"));
            }
        } catch (IOException e) {
            return false;
        }
    }

    /** Маппинг «новое имя папки → старое имя в хранилище». */
    private static final java.util.Map<String, String> LEGACY_MIGRATIONS =
            java.util.Map.ofEntries(
                    Map.entry("minecraft", "AttackDLC")
            );

    /**
     * Переносит клиента из {@code mods} инстанса в хранилище.
     *
     * <p>Вызывается после распаковки сборки: папка {@code mods} должна
     * остаться пустой, а мод — лежать в хранилище.</p>
     *
     * <p>Если файл уже есть в хранилище (например, там лежит новая версия,
     * а в {@code mods} — старая), старая копия просто удаляется: иначе
     * загрузчик увидел бы один мод дважды.</p>
     *
     * <p>Файлы, которых нет в репозитории клиента (старые аддоны, мусор),
     * в хранилище не переносятся, а удаляются — иначе они попали бы в
     * загрузку и ломали клиент.</p>
     *
     * @param knownFiles имена файлов клиента по репозиторию; пусто —
     *                   переносим всё (на случай недоступности сети)
     * @return true — что-то перенесено или удалено
     */
    public static boolean moveModsOut(Path installDir,
                                       java.util.Collection<String> knownFiles) {
        if (installDir == null) {
            return false;
        }
        Path mods = installDir.resolve("mods");
        if (!Files.isDirectory(mods)) {
            return false;
        }
        Path target = modsDir(installDir);
        boolean moved = false;
        boolean check = knownFiles != null && !knownFiles.isEmpty();
        try {
            Files.createDirectories(target);
            try (Stream<Path> files = Files.walk(mods)) {
                for (Path file : files.filter(Files::isRegularFile)
                        .toList()) {
                    Path relative = mods.relativize(file);
                    String name = relative.toString().replace('\\', '/');
                    Path destination = target.resolve(relative);
                    if (Files.exists(destination)) {
                        Files.deleteIfExists(file);   // в хранилище уже есть
                        moved = true;
                        continue;
                    }
                    if (check && !knownFiles.contains(name)) {
                        Files.deleteIfExists(file);   // чужой/старый файл
                        moved = true;
                        continue;
                    }
                    Files.createDirectories(destination.getParent());
                    Files.move(file, destination,
                            StandardCopyOption.REPLACE_EXISTING);
                    moved = true;
                }
            }
            // чистим mods — там не должно остаться ничего
            try (Stream<Path> files = Files.walk(mods)) {
                for (Path file : files.sorted((a, b) -> b.getNameCount()
                        - a.getNameCount()).toList()) {
                    Files.deleteIfExists(file);
                }
            }
        } catch (IOException ex) {
            return moved;
        }
        return moved;
    }

    /** Переносит всё, что есть в mods (когда список клиента неизвестен). */
    public static boolean moveModsOut(Path installDir) {
        return moveModsOut(installDir, java.util.List.of());
    }

    /** Все jar-файлы клиента в хранилище. */
    public static List<Path> modJars(Path installDir) {
        Path dir = modsDir(installDir);
        List<Path> jars = new ArrayList<>();
        if (!Files.isDirectory(dir)) {
            return jars;
        }
        try (Stream<Path> files = Files.walk(dir)) {
            files.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(".jar"))
                    .forEach(jars::add);
        } catch (IOException ignored) {
        }
        jars.sort(Path::compareTo);
        return jars;
    }

    /**
     * Значение системного свойства {@code fabric.addMods}:
     * пути к модам через разделитель пути ОС.
     */
    public static String addModsProperty(Path installDir) {
        List<Path> jars = modJars(installDir);
        if (jars.isEmpty()) {
            return "";
        }
        StringBuilder out = new StringBuilder();
        for (Path jar : jars) {
            if (out.length() > 0) {
                out.append(java.io.File.pathSeparator);
            }
            out.append(jar.toAbsolutePath());
        }
        return out.toString();
    }
}
