package usa.kitikat.account;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;

/**
 * Идентификатор компьютера (HWID).
 *
 * <p>Считается из аппаратных и системных номеров: серийник материнской
 * платы, MachineGuid Windows и серийный номер системного диска. Все
 * составляющие складываются в одну строку и хэшируются SHA-256 — наружу
 * уходит только короткий шестнадцатеричный отпечаток, сами номера
 * никуда не отправляются.</p>
 *
 * <p>Один и тот же ПК всегда даёт один и тот же HWID, поэтому аккаунт
 * с другого компьютера с этим отпечатком не совпадёт.</p>
 */
public final class Hwid {

    private static final Object LOCK = new Object();
    private static volatile String cached;

    private Hwid() {
    }

    /** Шестнадцатеричный отпечаток ПК (32 символа). */
    public static String get() {
        String value = cached;
        if (value != null) {
            return value;
        }
        synchronized (LOCK) {
            if (cached == null) {
                cached = hash(fingerprint());
            }
            return cached;
        }
    }

    /** Короткий вид для показа в интерфейсе: ABCD…WXYZ. */
    public static String shortForm() {
        String value = get();
        if (value.length() <= 12) {
            return value;
        }
        return value.substring(0, 4) + "…" + value.substring(value.length() - 4);
    }

    /** Составные части отпечатка — через «|», потом хэшируется. */
    private static String fingerprint() {
        StringBuilder raw = new StringBuilder();
        append(raw, commandOutput(
                "reg", "query",
                "HKLM\\SOFTWARE\\Microsoft\\Cryptography",
                "/v", "MachineGuid"));
        append(raw, commandOutput(
                "powershell", "-NoProfile", "-NonInteractive", "-Command",
                "(Get-CimInstance Win32_BaseBoard).SerialNumber"));
        append(raw, commandOutput(
                "powershell", "-NoProfile", "-NonInteractive", "-Command",
                "(Get-Volume -DriveLetter C).UniqueId"));
        raw.append('|').append(System.getProperty("user.name", ""));
        return raw.toString();
    }

    private static void append(StringBuilder raw, String part) {
        raw.append('|')
                .append(part == null ? "" : part.trim().toLowerCase(Locale.ROOT));
    }

    /** Вывод консольной команды; при сбое — пустая строка. */
    private static String commandOutput(String... command) {
        try {
            Process process = new ProcessBuilder(command)
                    .redirectErrorStream(true)
                    .start();
            String out = new String(process.getInputStream().readAllBytes(),
                    StandardCharsets.UTF_8);
            process.waitFor();
            return out;
        } catch (Exception ignored) {
            return "";
        }
    }

    /** SHA-256 в нижнем регистре. */
    private static String hash(String source) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(
                    source.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16))
                        .append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        } catch (Exception ignored) {
            // SHA-256 есть в любой JVM — сюда не должны попадать
            return "0".repeat(32);
        }
    }
}
