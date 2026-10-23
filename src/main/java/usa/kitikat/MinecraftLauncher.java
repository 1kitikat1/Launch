package usa.kitikat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.jar.JarFile;
import java.util.stream.Stream;

public final class MinecraftLauncher {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public MinecraftLauncher() {
    }

    public static Process launch(
            Path gameDirectory,
            String username
    ) throws Exception {

        gameDirectory = gameDirectory.toAbsolutePath().normalize();

        Path versions = gameDirectory.resolve("versions");
        Path libraries = gameDirectory.resolve("libraries");
        Path assets = gameDirectory.resolve("assets");

        if (!Files.isDirectory(versions)) {
            throw new IOException(
                    "Не найдена папка versions:\n" + versions
            );
        }

        /*
         * Ищем:
         *
         * fabric-loader-0.19.5-1.21.11.json
         */
        Path fabricJson = findFabricJson(versions);

        if (fabricJson == null) {
            throw new IOException(
                    "Не найден Fabric JSON в:\n" + versions
            );
        }

        JsonNode fabric = MAPPER.readTree(
                Files.readString(
                        fabricJson,
                        StandardCharsets.UTF_8
                )
        );

        /*
         * Fabric JSON говорит:
         *
         * "inheritsFrom": "1.21.11"
         */
        String minecraftVersion =
                fabric.path("inheritsFrom").asText(null);

        if (minecraftVersion == null ||
                minecraftVersion.isBlank()) {

            throw new IOException(
                    "В Fabric JSON отсутствует inheritsFrom:\n" +
                            fabricJson
            );
        }

        /*
         * Minecraft JSON:
         *
         * versions/1.21.11/1.21.11.json
         */
        Path minecraftJson =
                versions
                        .resolve(minecraftVersion)
                        .resolve(minecraftVersion + ".json");

        if (!Files.isRegularFile(minecraftJson)) {

            /*
             * На случай другой структуры.
             */
            minecraftJson =
                    findMinecraftJson(
                            versions,
                            minecraftVersion
                    );
        }

        if (minecraftJson == null ||
                !Files.isRegularFile(minecraftJson)) {

            throw new IOException(
                    "Не найден Minecraft JSON для " +
                            minecraftVersion
            );
        }

        JsonNode minecraft =
                MAPPER.readTree(
                        Files.readString(
                                minecraftJson,
                                StandardCharsets.UTF_8
                        )
                );

        /*
         * Minecraft JAR:
         *
         * versions/1.21.11/1.21.11.jar
         */
        Path minecraftJar =
                versions
                        .resolve(minecraftVersion)
                        .resolve(minecraftVersion + ".jar");

        if (!Files.isRegularFile(minecraftJar)) {

            minecraftJar =
                    findMinecraftJar(
                            versions,
                            minecraftVersion
                    );
        }

        if (minecraftJar == null ||
                !Files.isRegularFile(minecraftJar)) {

            throw new IOException(
                    "Не найден Minecraft JAR:\n" +
                            versions
                                    .resolve(minecraftVersion)
                                    .resolve(
                                            minecraftVersion +
                                                    ".jar"
                                    )
            );
        }

        /*
         * Java. Порядок:
         *
         * 1) runtime\bin внутри самой сборки (Minecraft 26.2 требует
         *    Java 25+, а лаунчер собран на Java 21 — поэтому у Dimasik26
         *    в репозитории лежит свой портативный JRE);
         * 2) runtime\bin внутри приложения (портативная сборка лаунчера);
         * 3) системная Java из PATH.
         */
        Path javaPath = findJava(gameDirectory);

        if (javaPath == null) {
            throw new IOException(
                    "Не найден Java:\n" +
                            Paths.get(
                                    System.getProperty("java.home"),
                                    "bin",
                                    isWindows()
                                            ? "java.exe"
                                            : "java"
                            ) +
                            "\n\nЛаунчер запущен из неполной папки. " +
                            "Распакуйте архив целиком и запускайте " +
                            "NemesisLauncher.exe из распакованной папки."
            );
        }

        /*
         * ---------------------------------------------------------
         * CLASS PATH
         * ---------------------------------------------------------
         *
         * ВАЖНО:
         *
         * Мы больше НЕ добавляем все JAR из libraries.
         *
         * Сначала Minecraft libraries из 1.21.11.json,
         * затем Fabric libraries.
         */
        /*
         * Ключ — maven-координата без версии (group:artifact:classifier):
         * Fabric подставляет свои версии библиотек (asm 9.10.1 вместо
         * asm 9.6 из json Minecraft), иначе игра падает с «duplicate ASM
         * classes found on classpath». Обычный set здесь не помогает —
         * у разных версий разные пути.
         */
        LinkedHashMap<String, Path> classpath =
                new LinkedHashMap<>();

        addLibrariesFromJson(
                minecraft,
                libraries,
                classpath
        );

        addLibrariesFromJson(
                fabric,
                libraries,
                classpath
        );

        /*
         * Сам Minecraft JAR.
         */
        classpath.put(
                "minecraft:client:",
                minecraftJar.toAbsolutePath().normalize()
        );

        if (classpath.isEmpty()) {
            throw new IOException(
                    "Classpath пуст."
            );
        }

        /*
         * Проверяем, что все библиотеки реально существуют.
         */
        List<Path> missing = new ArrayList<>();

        for (Path path : classpath.values()) {

            if (!Files.isRegularFile(path)) {
                missing.add(path);
            }
        }

        if (!missing.isEmpty()) {

            StringBuilder error =
                    new StringBuilder(
                            "Не найдены библиотеки Minecraft/Fabric:\n"
                    );

            for (Path path : missing) {
                error.append("\n")
                        .append(path);
            }

            throw new IOException(
                    error.toString()
            );
        }

        /*
         * Classpath string.
         */
        String separator =
                isWindows()
                        ? ";"
                        : ":";

        String classpathString =
                joinClasspath(
                        classpath.values(),
                        separator
                );

        /*
         * ---------------------------------------------------------
         * NATIVES
         * ---------------------------------------------------------
         */
        Path natives =
                gameDirectory.resolve("natives");

        Files.createDirectories(natives);

        extractNativesFromJson(
                minecraft,
                libraries,
                natives
        );

        extractNativesFromJson(
                fabric,
                libraries,
                natives
        );

        /*
         * ---------------------------------------------------------
         * PLAYER
         * ---------------------------------------------------------
         */
        String safeUsername =
                username == null ||
                        username.isBlank()
                        ? "Player"
                        : username;

        String uuid =
                UUID.nameUUIDFromBytes(
                                (
                                        "OfflinePlayer:" +
                                                safeUsername
                                ).getBytes(
                                        StandardCharsets.UTF_8
                                )
                        )
                        .toString()
                        .replace("-", "");

        /*
         * ---------------------------------------------------------
         * ASSET INDEX
         * ---------------------------------------------------------
         */
        String assetIndex =
                minecraft
                        .path("assetIndex")
                        .path("id")
                        .asText(minecraftVersion);

        /*
         * ---------------------------------------------------------
         * FABRIC MAIN CLASS
         * ---------------------------------------------------------
         */
        String mainClass =
                fabric
                        .path("mainClass")
                        .asText(
                                "net.fabricmc.loader.impl.launch.knot.KnotClient"
                        );

        /*
         * ---------------------------------------------------------
         * COMMAND
         * ---------------------------------------------------------
         */
        List<String> command =
                new ArrayList<>();

        command.add(
                javaPath.toString()
        );

        command.add(
                "-Xmx2G"
        );

        command.add(
                "-Djava.library.path=" +
                        natives.toAbsolutePath()
        );

        /*
         * Fabric рекомендует использовать эмуляцию
         * Minecraft main class.
         */
        command.add(
                "-DFabricMcEmu=net.minecraft.client.main.Main"
        );

        /*
         * Клиент лежит вне папки mods (в отдельном хранилище) —
         * подсказываем загрузчику, откуда взять моды. Папка mods
         * инстанса остаётся пустой.
         */
        String addMods = ClientStore.addModsProperty(gameDirectory);
        if (!addMods.isBlank()) {
            command.add("-Dfabric.addMods=" + addMods);
        }

        command.add("-cp");

        command.add(classpathString);

        command.add(mainClass);

        /*
         * ---------------------------------------------------------
         * GAME ARGUMENTS
         * ---------------------------------------------------------
         */
        command.add("--username");
        command.add(safeUsername);

        command.add("--version");
        command.add(
                fabric
                        .path("id")
                        .asText(
                                fabricJson
                                        .getFileName()
                                        .toString()
                                        .replace(
                                                ".json",
                                                ""
                                        )
                        )
        );

        command.add("--gameDir");
        command.add(
                gameDirectory.toString()
        );

        command.add("--assetsDir");
        command.add(
                assets.toString()
        );

        command.add("--assetIndex");
        command.add(assetIndex);

        command.add("--uuid");
        command.add(uuid);

        command.add("--accessToken");
        command.add("0");

        command.add("--userType");
        command.add("legacy");

        command.add("--versionType");
        command.add("Nemesis");

        /*
         * ---------------------------------------------------------
         * PROCESS
         * ---------------------------------------------------------
         */
        ProcessBuilder builder =
                new ProcessBuilder(command);

        builder.directory(
                gameDirectory.toFile()
        );

        builder.redirectErrorStream(true);

        Path log =
                gameDirectory.resolve(
                        "nemesis-launch.log"
                );

        builder.redirectOutput(
                ProcessBuilder.Redirect.appendTo(
                        log.toFile()
                )
        );

        /*
         * Записываем команду в лог.
         * Пароли/токены здесь отсутствуют.
         */
        try {
            Files.writeString(
                    log,
                    "\n\n========== NEMESIS LAUNCH ==========\n" +
                            "Minecraft: " +
                            minecraftVersion +
                            "\n" +
                            "Fabric: " +
                            fabric.path("id").asText() +
                            "\n" +
                            "Java: " +
                            javaPath +
                            "\n" +
                            "Classpath entries: " +
                            classpath.size() +
                            "\n" +
                            "Main: " +
                            mainClass +
                            "\n" +
                            "====================================\n",
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND
            );
        } catch (Exception ignored) {
        }

        return builder.start();
    }

    /*
     * =============================================================
     * LIBRARIES
     * =============================================================
     */

    private static void addLibrariesFromJson(
            JsonNode json,
            Path librariesRoot,
            Map<String, Path> classpath
    ) throws IOException {

        JsonNode libraries =
                json.path("libraries");

        if (!libraries.isArray()) {
            return;
        }

        for (JsonNode library : libraries) {

            if (!isAllowedByRules(library)) {
                continue;
            }

            /*
             * Современный Minecraft JSON:
             *
             * downloads:
             *   artifact:
             *      path: ...
             */
            JsonNode artifact =
                    library
                            .path("downloads")
                            .path("artifact");

            if (artifact.isObject()) {

                String relativePath =
                        artifact
                                .path("path")
                                .asText(null);

                if (relativePath != null &&
                        !relativePath.isBlank()) {

                    Path jar =
                            librariesRoot.resolve(
                                    relativePath
                            ).normalize();

                    classpath.put(
                            pathLibraryKey(relativePath),
                            jar
                    );

                    continue;
                }
            }

            /*
             * Fabric JSON часто содержит:
             *
             * name: group:artifact:version
             *
             * Без downloads.artifact.path.
             */
            String name =
                    library
                            .path("name")
                            .asText(null);

            if (name != null &&
                    !name.isBlank()) {

                Path jar =
                        mavenCoordinateToPath(
                                librariesRoot,
                                name
                        );

                if (jar != null) {
                    classpath.put(
                            coordinateLibraryKey(name),
                            jar
                    );
                }
            }
        }
    }

    /**
     * Ключ библиотеки из maven-пути вида
     * {@code org/ow2/asm/asm/9.6/asm-9.6.jar}:
     * {@code org.ow2.asm:asm:} (версия отбрасывается, ключ одинаков
     * с {@link #coordinateLibraryKey}).
     */
    private static String pathLibraryKey(String relativePath) {

        String normalized =
                relativePath.replace('\\', '/');

        String[] seg =
                normalized.split("/");

        if (seg.length < 4) {
            // не maven-вид — сравниваем путь как есть
            return normalized;
        }

        String file =
                seg[seg.length - 1];

        String versionDir =
                seg[seg.length - 2];

        String artifact =
                seg[seg.length - 3];

        StringBuilder group =
                new StringBuilder();

        for (int i = 0; i < seg.length - 3; i++) {
            if (i > 0) group.append('.');
            group.append(seg[i]);
        }

        String base =
                file.endsWith(".jar")
                        ? file.substring(0, file.length() - 4)
                        : file;

        base = base.replace("-" + versionDir, "");

        String classifier =
                base.startsWith(artifact + "-")
                        ? base.substring(artifact.length() + 1)
                        : "";

        return group + ":" + artifact + ":" + classifier;
    }

    /**
     * Ключ библиотеки из координаты {@code group:artifact:version[:classifier]}:
     * версия отбрасывается, классификатор сохраняется — иначе нативные
     * jar (natives-windows) склеились бы с обычными jar одной библиотеки.
     */
    private static String coordinateLibraryKey(String coordinate) {

        String[] parts =
                coordinate.split(":");

        if (parts.length < 3) {
            return coordinate;
        }

        String classifier =
                parts.length > 3
                        ? parts[3]
                        : "";

        return parts[0] + ":" + parts[1] + ":" + classifier;
    }

    private static Path mavenCoordinateToPath(
            Path librariesRoot,
            String coordinate
    ) {

        String[] parts =
                coordinate.split(":");

        if (parts.length < 3) {
            return null;
        }

        String group = parts[0];
        String artifact = parts[1];
        String version = parts[2];

        /*
         * classifier.
         */
        String classifier =
                parts.length >= 4
                        ? parts[3]
                        : null;

        Path path =
                librariesRoot;

        for (String groupPart :
                group.split("\\.")) {

            path = path.resolve(groupPart);
        }

        path = path.resolve(artifact)
                .resolve(version);

        String filename =
                artifact +
                        "-" +
                        version;

        if (classifier != null &&
                !classifier.isBlank()) {

            filename +=
                    "-" +
                            classifier;
        }

        filename += ".jar";

        return path.resolve(filename);
    }

    /*
     * =============================================================
     * RULES
     * =============================================================
     */

    private static boolean isAllowedByRules(
            JsonNode library
    ) {

        JsonNode rules =
                library.path("rules");

        if (!rules.isArray() ||
                rules.isEmpty()) {

            return true;
        }

        boolean allowed = false;

        for (JsonNode rule : rules) {

            String action =
                    rule
                            .path("action")
                            .asText("allow");

            boolean matches =
                    ruleMatchesCurrentPlatform(rule);

            if (matches) {

                if ("allow".equalsIgnoreCase(action)) {
                    allowed = true;
                }

                if ("disallow".equalsIgnoreCase(action)) {
                    allowed = false;
                }
            }
        }

        return allowed;
    }

    private static boolean ruleMatchesCurrentPlatform(
            JsonNode rule
    ) {

        JsonNode os =
                rule.path("os");

        if (!os.isObject()) {
            return true;
        }

        /*
         * OS.
         */
        String requiredOs =
                os.path("name").asText(null);

        if (requiredOs != null &&
                !requiredOs.isBlank()) {

            String currentOs;

            if (isWindows()) {
                currentOs = "windows";
            } else if (isMac()) {
                currentOs = "osx";
            } else {
                currentOs = "linux";
            }

            if (!requiredOs.equalsIgnoreCase(currentOs)) {
                return false;
            }
        }

        /*
         * Architecture.
         */
        String requiredArch =
                os.path("arch").asText(null);

        if (requiredArch != null &&
                !requiredArch.isBlank()) {

            String currentArch =
                    System.getProperty(
                            "os.arch",
                            ""
                    ).toLowerCase(Locale.ROOT);

            if (!currentArch.contains(
                    requiredArch.toLowerCase(Locale.ROOT)
            )) {
                return false;
            }
        }

        return true;
    }

    /*
     * =============================================================
     * NATIVES
     * =============================================================
     */

    private static void extractNativesFromJson(
            JsonNode json,
            Path librariesRoot,
            Path natives
    ) {

        JsonNode libraries =
                json.path("libraries");

        if (!libraries.isArray()) {
            return;
        }

        for (JsonNode library : libraries) {

            if (!isAllowedByRules(library)) {
                continue;
            }

            JsonNode classifiers =
                    library
                            .path("downloads")
                            .path("classifiers");

            if (!classifiers.isObject()) {
                continue;
            }

            /*
             * Minecraft обычно использует:
             *
             * natives-windows
             */
            String classifierKey =
                    nativeClassifierKey();

            JsonNode nativeArtifact =
                    classifiers.path(
                            classifierKey
                    );

            if (!nativeArtifact.isObject()) {
                continue;
            }

            String relativePath =
                    nativeArtifact
                            .path("path")
                            .asText(null);

            if (relativePath == null ||
                    relativePath.isBlank()) {
                continue;
            }

            Path jar =
                    librariesRoot
                            .resolve(relativePath)
                            .normalize();

            if (!Files.isRegularFile(jar)) {
                continue;
            }

            extractNativeJar(
                    jar,
                    natives
            );
        }
    }

    private static String nativeClassifierKey() {

        if (isWindows()) {
            return "natives-windows";
        }

        if (isMac()) {
            return "natives-osx";
        }

        return "natives-linux";
    }

    private static void extractNativeJar(
            Path jar,
            Path natives
    ) {

        try (
                JarFile jarFile =
                        new JarFile(
                                jar.toFile()
                        )
        ) {

            Enumeration<java.util.jar.JarEntry> entries =
                    jarFile.entries();

            while (entries.hasMoreElements()) {

                java.util.jar.JarEntry entry =
                        entries.nextElement();

                if (entry.isDirectory()) {
                    continue;
                }

                String name =
                        entry.getName();

                String lower =
                        name.toLowerCase(
                                Locale.ROOT
                        );

                if (!lower.endsWith(".dll") &&
                        !lower.endsWith(".so") &&
                        !lower.endsWith(".dylib")) {

                    continue;
                }

                String filename =
                        Paths.get(name)
                                .getFileName()
                                .toString();

                Path target =
                        natives.resolve(filename);

                try (
                        var input =
                                jarFile.getInputStream(entry)
                ) {

                    Files.copy(
                            input,
                            target,
                            StandardCopyOption.REPLACE_EXISTING
                    );
                }

            }

        } catch (Exception ignored) {
        }
    }

    /*
     * =============================================================
     * FINDERS
     * =============================================================
     */

    private static Path findFabricJson(
            Path versions
    ) throws IOException {

        try (Stream<Path> stream =
                     Files.walk(versions)) {

            return stream
                    .filter(Files::isRegularFile)
                    .filter(path -> {

                        String name =
                                path.getFileName()
                                        .toString()
                                        .toLowerCase(
                                                Locale.ROOT
                                        );

                        return name.startsWith(
                                "fabric-loader-"
                        ) &&
                                name.endsWith(
                                        ".json"
                                );
                    })
                    .findFirst()
                    .orElse(null);
        }
    }

    private static Path findMinecraftJson(
            Path versions,
            String version
    ) throws IOException {

        try (Stream<Path> stream =
                     Files.walk(versions)) {

            return stream
                    .filter(Files::isRegularFile)
                    .filter(path ->
                            path.getFileName()
                                    .toString()
                                    .equalsIgnoreCase(
                                            version + ".json"
                                    )
                    )
                    .findFirst()
                    .orElse(null);
        }
    }

    private static Path findMinecraftJar(
            Path versions,
            String version
    ) throws IOException {

        Path expected =
                versions
                        .resolve(version)
                        .resolve(version + ".jar");

        if (Files.isRegularFile(expected)) {
            return expected;
        }

        try (Stream<Path> stream =
                     Files.walk(versions)) {

            return stream
                    .filter(Files::isRegularFile)
                    .filter(path ->
                            path.getFileName()
                                    .toString()
                                    .equalsIgnoreCase(
                                            version + ".jar"
                                    )
                    )
                    .findFirst()
                    .orElse(null);
        }
    }

    /*
     * =============================================================
     * HELPERS
     * =============================================================
     */

    private static String joinClasspath(
            Collection<Path> paths,
            String separator
    ) {

        StringBuilder result =
                new StringBuilder();

        for (Path path : paths) {

            if (result.length() > 0) {
                result.append(separator);
            }

            result.append(
                    path.toAbsolutePath()
                            .normalize()
            );
        }

        return result.toString();
    }

    /**
     * Java для запуска игры: сначала вшитая в приложение
     * ({@code runtime\bin}), затем системная из PATH.
     *
     * <p>Портативная сборка живёт в {@code runtime\bin}, но в dev-режиме
     * {@code java.home} указывает на установленный JDK — поэтому кандидатов
     * несколько, а не один жёстко заданный.</p>
     */
    private static Path findJava() {
        return findJava(null);
    }

    /**
     * Java для запуска игры.
     *
     * <p>Если у инстанса есть своя папка {@code runtime} (например Dimasik26
     * требует Java 25+, а лаунчер собран на 21), используется она — иначе
     * старые клиенты начнут грузиться на неподходящей JVM.</p>
     *
     * @param gameDirectory папка инстанса, может быть {@code null}
     */
    private static Path findJava(Path gameDirectory) {
        String exe = isWindows() ? "java.exe" : "java";
        String home = System.getProperty("java.home");

        List<Path> candidates = new ArrayList<>();

        // 1) собственная Java инстанса
        if (gameDirectory != null) {
            candidates.add(
                    gameDirectory.resolve("runtime")
                            .resolve("bin")
                            .resolve(exe)
            );
            candidates.add(
                    gameDirectory.resolve("runtime")
                            .resolve("bin")
                            .resolve(isWindows() ? "javaw.exe" : "java")
            );
        }

        if (home != null && !home.isBlank()) {
            candidates.add(Paths.get(home, "bin", exe));
            // в минимальном образе бывает только javaw.exe
            candidates.add(Paths.get(home, "bin",
                    isWindows() ? "javaw.exe" : "java"));
        }

        String path = System.getenv("PATH");
        if (path != null) {
            for (String dir : path.split(java.io.File.pathSeparator)) {
                try {
                    if (!dir.isBlank()) {
                        candidates.add(Paths.get(dir.trim(), exe));
                    }
                } catch (Exception ignored) {
                    // мусорный путь в PATH — пропускаем
                }
            }
        }

        for (Path candidate : candidates) {
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    private static boolean isWindows() {

        return System
                .getProperty(
                        "os.name",
                        ""
                )
                .toLowerCase(
                        Locale.ROOT
                )
                .contains("win");
    }

    private static boolean isMac() {

        return System
                .getProperty(
                        "os.name",
                        ""
                )
                .toLowerCase(
                        Locale.ROOT
                )
                .contains("mac");
    }

    public String getLastOutput() {
        return null;
    }
}