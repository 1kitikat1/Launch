package usa.kitikat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.DoubleConsumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** Downloads the complete portable Nemesis game repository. */
public final class GitHubClient {
    public static final String REPOSITORY = "https://github.com/1kitikat1/Nema";
    public static final String ARCHIVE_URL = REPOSITORY + "/archive/refs/heads/main.zip";
    public static final String MANIFEST_URL = "https://raw.githubusercontent.com/1kitikat1/Nema/main/manifest.json";

    private final HttpClient http = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(java.time.Duration.ofSeconds(20))
            .build();
    private final ObjectMapper mapper = new ObjectMapper();

    public record Manifest(String version, String minecraft, String loader,
                           String displayName, String icon, List<FileEntry> files) {}
    public record FileEntry(String path, String url, String sha256) {}

    public boolean isInstalled(Path installDir) {
        try {
            Path versions = installDir.resolve("versions");
            if (!Files.isDirectory(versions)) return false;
            try (var stream = Files.walk(versions)) {
                return stream.anyMatch(p -> p.getFileName().toString().endsWith(".json")
                        && Files.isRegularFile(p)
                        && Files.isRegularFile(p.resolveSibling(p.getFileName().toString().replace(".json", ".jar"))));
            }
        } catch (Exception ignored) {
            return false;
        }
    }

    public void checkRepository() throws Exception {
        checkArchive(ARCHIVE_URL);
    }

    /** Проверяет, что GitHub отдаёт архив репозитория (zip ветки). */
    public void checkArchive(String archiveUrl) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(archiveUrl))
                .header("User-Agent", "NemesisLauncher")
                .header("Range", "bytes=0-0")
                .GET().build();
        HttpResponse<Void> response = http.send(request, HttpResponse.BodyHandlers.discarding());
        if (response.statusCode() / 100 != 2) {
            throw new IOException("GitHub недоступен: HTTP " + response.statusCode());
        }
    }

    public Manifest getManifest(String manifestUrl) throws Exception {
        HttpResponse<String> response = http.send(
                HttpRequest.newBuilder(URI.create(manifestUrl))
                        .header("User-Agent", "NemesisLauncher")
                        .GET().build(),
                HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() / 100 != 2) {
            throw new IOException("Не удалось получить manifest.json: HTTP " + response.statusCode());
        }
        return mapper.readValue(response.body(), Manifest.class);
    }

    /**
     * Downloads the whole Nema repository as one archive and extracts it into C:\\Nemesis.
     * This is considerably faster than requesting every repository file separately.
     */
    public void downloadRepository(Path installDir, DoubleConsumer progress,
                                   java.util.function.Consumer<String> status) throws Exception {
        downloadArchive(ARCHIVE_URL, installDir, progress, status);
    }

    /**
     * Скачивает любой публичный репозиторий GitHub как zip-архив ветки
     * и распаковывает его в installDir (корневая папка архива отрезается).
     * Так же ставится и Delta: &lt;диск&gt;:\\Delta.
     */
    public void downloadArchive(String archiveUrl, Path installDir, DoubleConsumer progress,
                                java.util.function.Consumer<String> status) throws Exception {
        Files.createDirectories(installDir);
        Path temp = Files.createTempFile("nemesis-repo-", ".zip");

        try {
            if (status != null) status.accept("Скачивание сборки...");

            HttpRequest request = HttpRequest.newBuilder(URI.create(archiveUrl))
                    .header("User-Agent", "NemesisLauncher")
                    .GET().build();

            HttpResponse<InputStream> response = http.send(
                    request, HttpResponse.BodyHandlers.ofInputStream());

            if (response.statusCode() / 100 != 2) {
                throw new IOException(" HTTP " + response.statusCode());
            }

            long total = response.headers().firstValueAsLong("Content-Length").orElse(-1L);
            long done = 0;

            /*
             * GitHub часто отдаёт архив чанками без Content-Length — тогда
             * шкала неопределённая, а в статусе показываем, сколько уже
             * скачано (иначе пользователь видит «залипшую» на 0 полосу).
             */
            if (progress != null && total <= 0) {
                progress.accept(-1);
            }

            try (InputStream in = new BufferedInputStream(response.body());
                 var out = Files.newOutputStream(temp)) {
                byte[] buffer = new byte[1024 * 1024];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    out.write(buffer, 0, read);
                    done += read;
                    if (progress != null && total > 0) {
                        progress.accept((double) done / total * 0.55);
                    } else if (status != null && done % (4 * 1024 * 1024) < read) {
                        status.accept(String.format(
                                "Скачивание архива: %.0f МБ...", done / 1024.0 / 1024.0));
                    }
                }
            }

            if (status != null) status.accept("Распаковка файлов...");
            extractArchive(temp, installDir, progress, status);
            if (progress != null) progress.accept(1.0);
            if (status != null) status.accept("Скачивание завершено.");
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    private void extractArchive(Path zip, Path destination, DoubleConsumer progress,
                                java.util.function.Consumer<String> status) throws IOException {
        int total = 0;
        try (ZipInputStream zin = new ZipInputStream(Files.newInputStream(zip))) {
            while (zin.getNextEntry() != null) total++;
        }

        int current = 0;
        try (ZipInputStream zin = new ZipInputStream(Files.newInputStream(zip))) {
            ZipEntry entry;
            while ((entry = zin.getNextEntry()) != null) {
                current++;
                String name = stripArchiveRoot(entry.getName());
                if (name.isBlank()) continue;

                Path target = destination.resolve(name).normalize();
                if (!target.startsWith(destination.toAbsolutePath().normalize())) {
                    throw new IOException("Небезопасный путь в архиве: " + name);
                }

                if (entry.isDirectory()) {
                    Files.createDirectories(target);
                    continue;
                }

                Path parent = target.getParent();
                if (parent != null) Files.createDirectories(parent);
                Files.copy(zin, target, StandardCopyOption.REPLACE_EXISTING);

                if (status != null && current % 25 == 0) status.accept("Файл: " + name);
                if (progress != null && total > 0) progress.accept(0.55 + ((double) current / total) * 0.45);
            }
        }
    }

    private static String stripArchiveRoot(String name) {
        int slash = name.indexOf('/');
        return slash < 0 ? "" : name.substring(slash + 1);
    }

    /** Kept for compatibility with older manifest-based UI code. */
    public void download(Manifest manifest, Path installDir, DoubleConsumer progress) throws Exception {
        if (manifest.files() == null || manifest.files().isEmpty()) return;
        Files.createDirectories(installDir);
        for (int i = 0; i < manifest.files().size(); i++) {
            FileEntry f = manifest.files().get(i);
            Path target = installDir.resolve(f.path()).normalize();
            if (!target.startsWith(installDir.toAbsolutePath().normalize())) {
                throw new IOException("Небезопасный путь: " + f.path());
            }
            Files.createDirectories(target.getParent());
            HttpResponse<byte[]> r = http.send(
                    HttpRequest.newBuilder(URI.create(f.url()))
                            .header("User-Agent", "NemesisLauncher").GET().build(),
                    HttpResponse.BodyHandlers.ofByteArray());
            if (r.statusCode() / 100 != 2) {
                throw new IOException("Ошибка скачивания " + f.path() + ": HTTP " + r.statusCode());
            }
            Files.write(target, r.body());
            if (progress != null) progress.accept((i + 1d) / manifest.files().size());
        }
    }

    /** Downloads the Lineage 2M launcher EXE only when it is missing. */
    public void downloadLineage2M(Path installDir, DoubleConsumer progress,
                                   java.util.function.Consumer<String> status) throws Exception {
        final String url = "https://github.com/1kitikat1/NemL2m/releases/download/Beta/NemL2m.exe";
        Files.createDirectories(installDir);
        Path target = installDir.resolve("NemL2m.exe").toAbsolutePath().normalize();

        if (Files.isRegularFile(target) && Files.size(target) > 0) {
            if (status != null) status.accept("Lineage 2M уже установлена.");
            if (progress != null) progress.accept(1.0);
            return;
        }

        Path temp = Files.createTempFile(installDir, "NemL2m-", ".download");
        try {
            if (status != null) status.accept("Скачивание Lineage 2M...");
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .header("User-Agent", "NemesisLauncher")
                    .GET().build();
            HttpResponse<InputStream> response = http.send(
                    request, HttpResponse.BodyHandlers.ofInputStream());

            if (response.statusCode() / 100 != 2) {
                throw new IOException("Lineage 2M: GitHub вернул HTTP " + response.statusCode());
            }

            long total = response.headers().firstValueAsLong("Content-Length").orElse(-1L);
            long done = 0;
            try (InputStream in = new BufferedInputStream(response.body());
                 var out = Files.newOutputStream(temp)) {
                byte[] buffer = new byte[1024 * 1024];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    out.write(buffer, 0, read);
                    done += read;
                    if (progress != null && total > 0) {
                        progress.accept((double) done / total);
                    }
                }
            }
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            if (progress != null) progress.accept(1.0);
            if (status != null) status.accept("Lineage 2M готова к запуску.");
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    /* ==================================================================
     *             Обновление установленного клиента (только mods/)
     * ================================================================== */

    /** Ветка, из которой ставятся и обновляются все клиенты. */
    public static final String BRANCH = "main";

    /** Файл в репозитории: путь от корня ветки, git blob sha, размер. */
    public record RemoteFile(String path, String sha, long size) {}

    /** Результат сверки локальной папки mods/ с тем, что лежит в репозитории. */
    public record ModsDiff(List<String> download, List<String> remove, int remoteCount) {
        public boolean isEmpty() {
            return download.isEmpty() && remove.isEmpty();
        }
    }

    /** «1kitikat1/Nema» из ссылки вида https://github.com/1kitikat1/Nema */
    public static String slug(String repositoryUrl) {
        String s = repositoryUrl == null ? "" : repositoryUrl.trim();
        while (s.endsWith("/")) s = s.substring(0, s.length() - 1);
        if (s.endsWith(".git")) s = s.substring(0, s.length() - 4);
        int i = s.lastIndexOf("github.com/");
        if (i >= 0) s = s.substring(i + "github.com/".length());
        return s;
    }

    /**
     * git blob sha-1 файла: SHA1("blob " + размер + "\0" + содержимое).
     * Ровно такое значение GitHub хранит в дереве репозитория, поэтому
     * одного сравнения достаточно, чтобы понять — файл изменился или нет,
     * не качая архивы целиком.
     */
    public static String blobSha(Path file) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            digest.update(("blob " + Files.size(file) + "\0")
                    .getBytes(StandardCharsets.US_ASCII));
            try (InputStream in = new BufferedInputStream(Files.newInputStream(file))) {
                byte[] buffer = new byte[1024 * 1024];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    digest.update(buffer, 0, read);
                }
            }
            StringBuilder out = new StringBuilder(40);
            for (byte b : digest.digest()) {
                out.append(Character.forDigit((b >> 4) & 0xF, 16));
                out.append(Character.forDigit(b & 0xF, 16));
            }
            return out.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IOException("SHA-1 недоступен", ex);
        }
    }

    /** Все файлы внутри mods/ ветки: путь относительно mods/ → описание файла. */
    public Map<String, RemoteFile> remoteMods(String repositoryUrl, String branch) throws Exception {
        String url = "https://api.github.com/repos/" + slug(repositoryUrl)
                + "/git/trees/" + branch + "?recursive=1";

        HttpResponse<String> response = http.send(
                HttpRequest.newBuilder(URI.create(url))
                        .header("User-Agent", "NemesisLauncher")
                        .header("Accept", "application/vnd.github+json")
                        // без таймаута подвешенный запрос держал бы всю проверку
                        .timeout(java.time.Duration.ofSeconds(30))
                        .GET().build(),
                HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 403 || response.statusCode() == 429) {
            throw new IOException("GitHub временно ограничил запросы (HTTP "
                    + response.statusCode() + ") — повторите попытку чуть позже");
        }
        if (response.statusCode() / 100 != 2) {
            throw new IOException("Не удалось получить список файлов клиента: HTTP "
                    + response.statusCode());
        }

        Map<String, RemoteFile> files = new LinkedHashMap<>();
        for (JsonNode node : mapper.readTree(response.body()).path("tree")) {
            if (!"blob".equals(node.path("type").asText())) continue;
            String path = node.path("path").asText();
            if (!path.startsWith("mods/") || path.contains("..")) continue;
            files.put(path.substring("mods/".length()),
                    new RemoteFile(path, node.path("sha").asText(), node.path("size").asLong()));
        }
        if (files.isEmpty()) {
            throw new IOException("В репозитории " + slug(repositoryUrl) + " нет папки mods/");
        }
        return files;
    }

    /**
     * Список файлов клиента в репозитории (относительно mods/).
     *
     * <p>Нужен, чтобы при переносе в хранилище унести только файлы
     * клиента, а старые аддоны и мусор удалить.</p>
     *
     * <p>Пустой список, если сеть недоступна — тогда переносим всё.</p>
     */
    public java.util.List<String> remoteModFiles(String repositoryUrl) {
        try {
            Map<String, RemoteFile> files = remoteMods(repositoryUrl, BRANCH);
            return new java.util.ArrayList<>(files.keySet());
        } catch (Exception ex) {
            return java.util.List.of();
        }
    }

    /** Сверка установленной сборки с репозиторием: что докачать и что удалить. */
    public ModsDiff checkMods(Path installDir, String repositoryUrl) throws Exception {
        return checkMods(installDir, repositoryUrl, BRANCH, java.util.List.of());
    }

    public ModsDiff checkMods(Path installDir, String repositoryUrl, String branch)
            throws Exception {
        return checkMods(installDir, repositoryUrl, branch, java.util.List.of());
    }

    /**
     * Сверка с игнором: файлы из ignoreLocal (аддоны клиента, поставленные
     * из магазина лаунчера) не считаются пропавшими — обновление их не удалит
     * и не показывает из-за них диалог обновления.
     */
    public ModsDiff checkMods(Path installDir, String repositoryUrl,
                              java.util.Collection<String> ignoreLocal) throws Exception {
        return checkMods(installDir, repositoryUrl, BRANCH, ignoreLocal);
    }

    public ModsDiff checkMods(Path installDir, String repositoryUrl, String branch,
                              java.util.Collection<String> ignoreLocal) throws Exception {
        Map<String, RemoteFile> remote = remoteMods(repositoryUrl, branch);
        // клиент живёт в хранилище вне папки mods — там он и сверяется
        Path mods = ClientStore.modsDir(installDir);

        Set<String> local = new LinkedHashSet<>();
        if (Files.isDirectory(mods)) {
            try (var stream = Files.walk(mods)) {
                for (Path file : (Iterable<Path>) stream::iterator) {
                    if (!Files.isRegularFile(file)) continue;
                    local.add(mods.relativize(file).toString().replace('\\', '/'));
                }
            }
        }

        List<String> download = new ArrayList<>();
        for (Map.Entry<String, RemoteFile> entry : remote.entrySet()) {
            Path file = mods.resolve(entry.getKey());
            if (Files.isRegularFile(file) && blobSha(file).equals(entry.getValue().sha())) {
                continue;
            }
            download.add(entry.getKey());
        }

        List<String> remove = new ArrayList<>();
        for (String path : local) {
            if (remote.containsKey(path)) continue;
            if (ignoreLocal != null && ignoreLocal.contains(path)) continue;
            remove.add(path);
        }
        return new ModsDiff(download, remove, remote.size());
    }

    /**
     * Применяет обновление: сначала удаляет файлы, пропавшие из репозитория,
     * затем докачивает новые и изменившиеся. Трогается только mods/.
     */
    public void updateMods(Path installDir, String repositoryUrl, DoubleConsumer progress,
                           java.util.function.Consumer<String> status) throws Exception {
        updateMods(installDir, repositoryUrl, BRANCH, progress, status, java.util.List.of());
    }

    public void updateMods(Path installDir, String repositoryUrl, String branch,
                           DoubleConsumer progress, java.util.function.Consumer<String> status)
            throws Exception {
        updateMods(installDir, repositoryUrl, branch, progress, status, java.util.List.of());
    }

    /** Как updateMods, но не удаляет файлы из ignoreLocal (аддоны магазина). */
    public void updateMods(Path installDir, String repositoryUrl, DoubleConsumer progress,
                           java.util.function.Consumer<String> status,
                           java.util.Collection<String> ignoreLocal) throws Exception {
        updateMods(installDir, repositoryUrl, BRANCH, progress, status, ignoreLocal);
    }

    public void updateMods(Path installDir, String repositoryUrl, String branch,
                           DoubleConsumer progress, java.util.function.Consumer<String> status,
                           java.util.Collection<String> ignoreLocal) throws Exception {
        ModsDiff diff = checkMods(installDir, repositoryUrl, branch, ignoreLocal);
        // файлы клиента хранятся вне mods — в отдельной папке
        Path mods = Files.createDirectories(ClientStore.modsDir(installDir));
        String owner = slug(repositoryUrl);

        for (String path : diff.remove()) {
            if (status != null) status.accept("Удаляем пропавший файл: " + path);
            Files.deleteIfExists(mods.resolve(path));
        }

        int total = diff.download().size();
        int done = 0;
        for (String path : diff.download()) {
            done++;
            if (status != null) {
                status.accept("Скачиваем " + done + "/" + total + ": " + path);
            }
            if (progress != null && total > 0) {
                progress.accept((double) (done - 1) / total);
            }
            String url = "https://raw.githubusercontent.com/" + owner + "/" + branch
                    + "/mods/" + encodePath(path);
            downloadFile(url, mods.resolve(path), status);
        }

        if (progress != null) progress.accept(1.0);
        if (status != null) {
            status.accept(diff.isEmpty()
                    ? "Обновления не нужны — версия актуальна."
                    : "Готово: обновлено " + total + ", удалено " + diff.remove().size() + ".");
        }
    }

    /** Кодирует каждый сегмент пути, оставляя «/» разделителем. */
    private static String encodePath(String path) {
        StringBuilder out = new StringBuilder();
        for (String part : path.split("/", -1)) {
            if (out.length() > 0) out.append('/');
            out.append(URLEncoder.encode(part, StandardCharsets.UTF_8).replace("+", "%20"));
        }
        return out.toString();
    }

    /** Скачивание одного файла: во временный .part, затем замена с повторами. */
    private void downloadFile(String url, Path target,
                              java.util.function.Consumer<String> status) throws Exception {
        Files.createDirectories(target.getParent());
        Path temp = Files.createTempFile(target.getParent(), "update-", ".part");
        try {
            IOException failure = null;
            for (int attempt = 1; attempt <= 3; attempt++) {
                try {
                    HttpResponse<InputStream> response = http.send(
                            HttpRequest.newBuilder(URI.create(url))
                                    .header("User-Agent", "NemesisLauncher")
                                    .GET().build(),
                            HttpResponse.BodyHandlers.ofInputStream());
                    if (response.statusCode() / 100 != 2) {
                        throw new IOException("HTTP " + response.statusCode());
                    }
                    try (InputStream in = new BufferedInputStream(response.body());
                         OutputStream out = Files.newOutputStream(temp)) {
                        in.transferTo(out);
                    }
                    failure = null;
                    break;
                } catch (IOException ex) {
                    failure = ex;
                    if (status != null) {
                        status.accept("Сбой сети, повторяем (" + attempt + "/3)...");
                    }
                    Thread.sleep(500L * attempt);
                }
            }
            if (failure != null) {
                throw new IOException("не удалось скачать " + target.getFileName()
                        + " (" + failure.getMessage() + ")");
            }

            try {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException ex) {
                // файл может быть занят игрой или антивирусом — даём ещё пару попыток
                IOException last = ex;
                for (int attempt = 0; attempt < 3; attempt++) {
                    Thread.sleep(400L);
                    try {
                        Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
                        last = null;
                        break;
                    } catch (IOException retry) {
                        last = retry;
                    }
                }
                if (last != null) {
                    throw new IOException("не удалось заменить файл " + target.getFileName()
                            + " — закройте игру и повторите (" + last.getMessage() + ")");
                }
            }
        } finally {
            Files.deleteIfExists(temp);
        }
    }

}
