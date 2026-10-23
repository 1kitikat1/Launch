package usa.kitikat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.WinBase;
import com.sun.jna.platform.win32.WinDef;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;

/**
 * Проверка и установка обновлений самого лаунчера.
 *
 * <p>В репозитории {@code https://github.com/1kitikat1/Launch} в ветке {@code main}
 * лежит {@code version.json}:</p>
 *
 * <pre>{@code
 * { "version": "1.1.0",
 *   "url": "https://github.com/1kitikat1/Launch/releases/download/v1.1.0/NemesisLauncher-1.1.0.zip",
 *   "notes": "Что изменилось" } }
 * </pre>
 *
 * <p>Если версия там новее текущей, лаунчер скачивает zip во временную папку,
 * отбрасывает рядом скрипт PowerShell и закрывается. Скрипт ждёт полного
 * завершения процесса, распаковывает архив поверх папки приложения
 * и снова запускает {@code NemesisLauncher.exe} — пользователь просто
 * видит перезапуск.</p>
 */
public final class UpdateChecker {

    /** JSON со свежей версией (репозиторий Launch, ветка main). */
    public static final String VERSION_URL =
            "https://raw.githubusercontent.com/1kitikat1/Launch/main/version.json";

    /**
     * Откуда читать version.json. Подмена одним флагом — для проверки
     * обновления на локальном сервере: {@code -Dattack.updateUrl=http://...}.
     */
    public static String versionUrl() {
        String override = System.getProperty("attack.updateUrl");
        return override == null || override.isBlank() ? VERSION_URL : override;
    }

    /** Имя exe в папке приложения — совпадает с --name задачи packageExe. */
    public static final String EXE_NAME = "AttackLauncher.exe";

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private UpdateChecker() {
    }

    /** Содержимое version.json. */
    public record Info(String version, String url, String notes) {
    }

    /**
     * Версия этой сборки — из манифеста jar ({@code Implementation-Version}).
     * В dev-режиме (запуск из классов) версии нет → «dev».
     */
    public static String currentVersion() {
        try {
            String value = UpdateChecker.class.getPackage().getImplementationVersion();
            return value == null || value.isBlank() ? "dev" : value.trim();
        } catch (Throwable ignored) {
            return "dev";
        }
    }

    /** Забрать version.json с сервера. */
    public static Info fetch(String versionUrl) throws Exception {
        HttpResponse<String> response = HTTP.send(
                HttpRequest.newBuilder(URI.create(versionUrl))
                        .header("User-Agent", "NemesisLauncher")
                        .GET().build(),
                HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() / 100 != 2) {
            throw new IOException("Не удалось получить version.json: HTTP "
                    + response.statusCode());
        }

        Info info = MAPPER.readValue(response.body(), Info.class);
        if (info == null || info.version() == null || info.version().isBlank()) {
            throw new IOException("В version.json нет поля version");
        }
        if (info.url() == null || info.url().isBlank()) {
            throw new IOException("В version.json нет поля url");
        }
        return info;
    }

    /**
     * Новая ли версия: сравниваем по числовым частям (1.1.0 &gt; 1.0.0).
     * Нестандартные значения (например «dev») считаются старейшими.
     */
    public static boolean isNewer(String current, String remote) {
        if (remote == null || remote.isBlank()) {
            return false;
        }
        String[] left = split(current);
        String[] right = split(remote);
        int max = Math.max(left.length, right.length);
        for (int i = 0; i < max; i++) {
            String a = i < left.length ? left[i] : "0";
            String b = i < right.length ? right[i] : "0";
            int cmp = comparePart(a, b);
            if (cmp != 0) {
                return cmp < 0;
            }
        }
        return false;
    }

    private static String[] split(String version) {
        if (version == null || version.isBlank()) {
            return new String[]{"0"};
        }
        return version.trim().split("[.\\-+]");
    }

    private static int comparePart(String a, String b) {
        try {
            return Integer.compare(Integer.parseInt(a), Integer.parseInt(b));
        } catch (NumberFormatException ignored) {
            // «dev», «beta» и прочее — младше любой цифры
            boolean numA = isNumber(a);
            boolean numB = isNumber(b);
            if (numA != numB) {
                return numA ? -1 : 1;
            }
            return a.compareToIgnoreCase(b);
        }
    }

    private static boolean isNumber(String value) {
        for (int i = 0; i < value.length(); i++) {
            if (!Character.isDigit(value.charAt(i))) {
                return false;
            }
        }
        return !value.isEmpty();
    }

    /**
     * Скачивание файла с прогрессом.
     *
     * @param progress 0..1, либо −1, если сервер не отдал Content-Length
     *                 (тогда в статусе показываем, сколько уже скачано)
     */
    public static void download(String url, Path target,
                                DoubleConsumer progress,
                                Consumer<String> status) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .header("User-Agent", "NemesisLauncher")
                .GET().build();
        HttpResponse<InputStream> response =
                HTTP.send(request, HttpResponse.BodyHandlers.ofInputStream());

        if (response.statusCode() / 100 != 2) {
            throw new IOException("Скачивание не удалось: HTTP " + response.statusCode());
        }

        long total = response.headers().firstValueAsLong("Content-Length").orElse(-1L);
        long done = 0;

        if (progress != null && total <= 0) {
            progress.accept(-1);
        }

        try (InputStream in = new BufferedInputStream(response.body());
             var out = Files.newOutputStream(target)) {
            byte[] buffer = new byte[1024 * 1024];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
                done += read;
                if (progress != null && total > 0) {
                    progress.accept((double) done / total);
                } else if (status != null && done % (4 * 1024 * 1024) < read) {
                    status.accept(String.format(java.util.Locale.ROOT,
                            "Скачивание обновления: %.0f МБ...",
                            done / 1024.0 / 1024.0));
                }
            }
        }

        if (done <= 0) {
            throw new IOException("Сервер отдал пустой файл");
        }
        if (progress != null) {
            progress.accept(1.0);
        }
        if (status != null) {
            status.accept("Архив скачан.");
        }
    }

    /**
     * Папка приложения (та, где лежит AttackLauncher.exe), либо null,
     * если лаунчер запущен не из exe — тогда самообновляться нечем.
     */
    public static Path appDirectory() {
        try {
            var source = UpdateChecker.class.getProtectionDomain().getCodeSource();
            if (source == null || source.getLocation() == null) {
                return null;
            }
            Path code = Path.of(source.getLocation().toURI());
            if (!Files.isRegularFile(code)) {
                return null;      // dev-режим: classes-каталог, а не jar
            }
            Path app = code.getParent();        // .../AttackLauncher/app
            Path root = app == null ? null : app.getParent();   // .../AttackLauncher
            if (root == null) {
                return null;
            }
            return Files.isRegularFile(root.resolve(EXE_NAME)) ? root : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    /**
     * Пишет скрипт обновления и запускает его отвязанным скрытым процессом.
     * Скрипт ждёт, пока лаунчер закроется, распаковывает zip поверх папки
     * приложения и — если существует файл-флаг — снова запускает exe.
     *
     * @param appDir      папка приложения
     * @param zip         скачанный архив
     * @param restartFlag файл-флаг: если он есть, лаунчер перезапустится
     * @return путь к скрипту (во временной папке)
     */
    public static Path spawnUpdate(Path appDir, Path zip, Path restartFlag)
            throws IOException {
        Path script = Files.createTempFile("nemesis-update-", ".ps1");
        Files.writeString(script, scriptBody(appDir, zip, restartFlag),
                StandardCharsets.UTF_8);

        if (!startHidden(script)) {
            /*
             * Запасной путь: отвязанный cmd /c start, окно скрывает
             * сам PowerShell (-WindowStyle Hidden).
             */
            new ProcessBuilder(
                    "cmd.exe", "/c", "start", "",
                    "powershell.exe", "-NoProfile", "-WindowStyle", "Hidden",
                    "-ExecutionPolicy", "Bypass",
                    "-File", script.toAbsolutePath().toString()
            ).redirectErrorStream(true).redirectOutput(
                    ProcessBuilder.Redirect.DISCARD).start();
        }

        return script;
    }

    /**
     * Запускает скрипт так, чтобы пользователь ничего не увидел:
     * {@code CreateProcess} с флагом {@code CREATE_NO_WINDOW} — консоль
     * Windows не создаётся вовсе (без окна и мигания), вывод в консоль
     * скрипту не нужен.
     *
     * <p>Процесс не входит в job object JVM, поэтому переживает наше
     * завершение — как и раньше с {@code cmd /c start}.</p>
     *
     * @return true — процесс создан скрытым
     */
    private static boolean startHidden(Path script) {
        String command = "powershell.exe -NoProfile"
                + " -ExecutionPolicy Bypass -File \""
                + script.toAbsolutePath() + "\"";

        WinBase.STARTUPINFO si = new WinBase.STARTUPINFO();
        WinBase.PROCESS_INFORMATION pi = new WinBase.PROCESS_INFORMATION();

        try {
            boolean created = Kernel32.INSTANCE.CreateProcess(
                    null,
                    command,
                    null,
                    null,
                    false,
                    new WinDef.DWORD(WinBase.CREATE_NO_WINDOW),
                    null,
                    null,
                    si,
                    pi
            );

            if (pi.hThread != null) {
                Kernel32.INSTANCE.CloseHandle(pi.hThread);
            }
            if (pi.hProcess != null) {
                Kernel32.INSTANCE.CloseHandle(pi.hProcess);
            }
            return created;
        } catch (Throwable ignored) {
            return false;   // не Windows или JNA не поднялся — см. запасной путь
        }
    }

    /** Тело PowerShell-скрипта (для отладки — можно прочитать и проверить). */
    public static String scriptBody(Path appDir, Path zip, Path restartFlag) {
        String dir = ps(appDir.toAbsolutePath().toString());
        String archive = ps(zip.toAbsolutePath().toString());
        String exe = ps(EXE_NAME);
        String flag = ps(restartFlag == null ? "" : restartFlag.toAbsolutePath().toString());

        return """
                $ErrorActionPreference = 'Stop'
                $appDir = '%s'
                $zip = '%s'
                $restartFlag = '%s'

                # ждём, пока лаунчер полностью завершится
                do {
                    Start-Sleep -Milliseconds 700
                    $busy = @()
                    $busy += Get-Process -Name 'AttackLauncher' -ErrorAction SilentlyContinue
                    $busy += Get-CimInstance Win32_Process -Filter "Name='java.exe'" -ErrorAction SilentlyContinue |
                             Where-Object { $_.CommandLine -like ('*' + $appDir + '*') }
                } while ($busy.Count -gt 0)

                $tmp = Join-Path $env:TEMP 'nemesis-update-unpack'
                if (Test-Path $tmp) { Remove-Item $tmp -Recurse -Force }
                Expand-Archive -Path $zip -DestinationPath $tmp -Force

                $src = $tmp
                if (Test-Path (Join-Path $tmp 'AttackLauncher')) {
                    $src = Join-Path $tmp 'AttackLauncher'
                }
                robocopy $src $appDir /MIR /NFL /NDL /NJH /NJS /R:2 /W:2 | Out-Null

                # перезапуск — только если пользователь его запросил
                if ($restartFlag -ne '' -and (Test-Path $restartFlag)) {
                    Remove-Item $restartFlag -Force -ErrorAction SilentlyContinue
                    if (Test-Path (Join-Path $appDir '%s')) {
                        Start-Process -FilePath (Join-Path $appDir '%s')
                    }
                }

                Remove-Item $tmp -Recurse -Force -ErrorAction SilentlyContinue
                Remove-Item $zip -Force -ErrorAction SilentlyContinue
                Remove-Item -LiteralPath $MyInvocation.MyCommand.Path -Force -ErrorAction SilentlyContinue
                """
                .formatted(dir, archive, flag, exe, exe);
    }

    /** Экранирование пути для одинарных кавычек PowerShell. */
    private static String ps(String value) {
        return value.replace("'", "''");
    }
}
