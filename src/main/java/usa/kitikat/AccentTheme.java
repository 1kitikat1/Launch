package usa.kitikat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

/**
 * Акцентная тема лаунчера.
 *
 * <p>JavaFX CSS не умеет переменных, поэтому акцентный цвет применяется так:
 * генерируется небольшой override-файл стилей, который подключается ПОСЛЕ
 * {@code styles.css} (поздние таблицы перекрывают ранние) и переопределяет
 * все места, где используется акцент: кнопки, слайдеры, чекбоксы,
 * подсветку карточек, «глазки» и т.д.</p>
 */
public final class AccentTheme {

    /** Варианты тем (по акцентному цвету). */
    public record Option(String id, String name, String hex) {
    }

    public static final List<Option> OPTIONS = List.of(
            new Option("blue", "Синий", "#1688e8"),
            new Option("cyan", "Голубой", "#06b6d4"),
            new Option("violet", "Фиолетовый", "#8b5cf6"),
            new Option("pink", "Розовый", "#ec4899"),
            new Option("red", "Красный", "#ef4444"),
            new Option("green", "Зелёный", "#22c55e"),
            new Option("amber", "Янтарный", "#f59e0b")
    );

    private AccentTheme() {
    }

    public static Option byId(String id) {
        for (Option option : OPTIONS) {
            if (option.id().equalsIgnoreCase(id)) {
                return option;
            }
        }
        return OPTIONS.get(0);
    }

    /** Файл, куда пишется сгенерированный CSS (переживает перезапуск). */
    public static Path cssFile() {
        return Path.of(System.getProperty("user.home"), ".nemesis", "accent.css");
    }

    /**
     * Записать CSS для цвета и вернуть URL файла для scene.getStylesheets().
     */
    public static String write(String hex) throws IOException {
        return write(hex, 1.0);
    }

    /**
     * Записать CSS (акцент + плотность панелей) и вернуть URL файла.
     *
     * @param panelDensity 1.0 — панели как в styles.css, меньше — прозрачнее.
     */
    public static String write(String hex, double panelDensity) throws IOException {
        Path file = cssFile();
        Files.createDirectories(file.getParent());
        Files.writeString(file, css(hex, panelDensity), StandardCharsets.UTF_8);
        return file.toUri().toString();
    }

    /** Сгенерированные override-правила для одного акцентного цвета. */
    public static String css(String hex) {
        return css(hex, 1.0);
    }

    /** Сгенерированные override-правила: акцент + плотность панелей. */
    public static String css(String hex, double panelDensity) {
        String base = normalize(hex);
        String hover = lighten(base, 0.16);
        String soft = rgba(base, 0.16);
        String glow = rgba(base, 0.32);

        return """
                /* сгенерировано Attack Launcher — акцентная тема %s */
                .primary-button { -fx-background-color: %s; }
                .primary-button:hover { -fx-background-color: %s; }

                .eyebrow { -fx-text-fill: %s; }

                .slider .thumb { -fx-background-color: %s; }
                .settings-check:selected .box { -fx-background-color: %s; -fx-border-color: %s; }
                .progress-bar > .bar { -fx-background-color: %s; }

                .nav-button:hover { -fx-background-color: %s; }
                .nav-button:hover .nav-label { -fx-text-fill: %s; }
                .nav-button:hover .nav-icon { -fx-stroke: %s; }

                .game-card-small:hover {
                    -fx-border-color: %s;
                    -fx-effect: dropshadow(gaussian, %s, 18, .25, 0, 5);
                }
                .client-card:hover, .wallpaper-choice:hover { -fx-border-color: %s; }
                .wallpaper-choice:hover { -fx-effect: dropshadow(gaussian, %s, 12, .2, 0, 2); }

                .plan-pill {
                    -fx-background-color: %s;
                    -fx-background-radius: 99;
                    -fx-padding: 4 11;
                    -fx-text-fill: %s;
                    -fx-font-size: 11px;
                }
                .duration-pill {
                    -fx-background-color: %s;
                    -fx-background-radius: 99;
                    -fx-padding: 4 12;
                    -fx-text-fill: %s;
                    -fx-font-size: 11px;
                    -fx-cursor: hand;
                    -fx-border-width: 0;
                    -fx-min-height: 28px;
                }
                .duration-pill:hover {
                    -fx-background-color: %s;
                }
                .duration-pill-selected {
                    -fx-background-color: %s !important;
                    -fx-text-fill: #ffffff !important;
                    -fx-font-weight: bold;
                }
                """.formatted(base, base, hover, hover, base, base, base, base,
                soft, hover, hover, base, glow, base, glow, soft, hover)
                + panelRules(panelDensity);
    }

    /**
     * Плотность панелей и карточек (1.0 — базовый вид из styles.css).
     *
     * <p>Значение меньше единицы уменьшает альфу фонов, поэтому сквозь
     * панели и карточки сильнее видно обои. Правила идут тем же файлом
     * после styles.css, поэтому перекрывают базовые.</p>
     */
    private static String panelRules(double density) {
        double k = Math.max(0.3, Math.min(1.0, density));
        return """
                /* сгенерировано Attack Launcher — плотность панелей %.0f%% */
                .page-layer > VBox { -fx-background-color: rgba(6,10,17,%s); }
                .game-card-small { -fx-background-color: rgba(17,23,34,%s); }
                .client-card, .settings-section, .profile-card, .empty-state-card {
                    -fx-background-color: rgba(9,15,24,%s);
                }
                .secondary-button, .card-button { -fx-background-color: rgba(13,20,31,%s); }
                .secondary-button:hover, .card-button:hover { -fx-background-color: rgba(28,41,61,%s); }
                .wallpaper-choice { -fx-background-color: rgba(10,15,23,%s); }
                .progress-bar { -fx-background-color: rgba(15,22,34,%s); }
                """.formatted(
                k * 100,
                alpha(0.24, k), alpha(0.72, k), alpha(0.58, k),
                alpha(0.74, k), alpha(0.88, k), alpha(0.68, k), alpha(0.72, k));
    }

    /** alpha из стиля, умноженный на плотность, в формате 0.24. */
    private static String alpha(double base, double k) {
        double value = Math.max(0.0, Math.min(1.0, base * k));
        return String.format(Locale.ROOT, "%.2f", value);
    }

    /* ---------------- цветовые помощники ---------------- */

    private static String normalize(String hex) {
        String value = hex == null ? "#1688e8" : hex.trim();
        if (!value.startsWith("#")) {
            value = "#" + value;
        }
        if (value.length() == 4) {
            // #abc -> #aabbcc
            value = "#" + value.charAt(1) + value.charAt(1)
                    + value.charAt(2) + value.charAt(2)
                    + value.charAt(3) + value.charAt(3);
        }
        if (value.length() != 7) {
            return "#1688e8";
        }
        return value.toLowerCase(Locale.ROOT);
    }

    private static int[] rgb(String hex) {
        String value = normalize(hex).substring(1);
        return new int[]{
                Integer.parseInt(value.substring(0, 2), 16),
                Integer.parseInt(value.substring(2, 4), 16),
                Integer.parseInt(value.substring(4, 6), 16)
        };
    }

    private static String toHex(int channel) {
        return String.format("%02x", Math.max(0, Math.min(255, channel)));
    }

    /** Осветлить цвет (смесь с белым). */
    private static String lighten(String hex, double factor) {
        int[] c = rgb(hex);
        return "#" + toHex((int) (c[0] + (255 - c[0]) * factor))
                + toHex((int) (c[1] + (255 - c[1]) * factor))
                + toHex((int) (c[2] + (255 - c[2]) * factor));
    }

    private static String rgba(String hex, double alpha) {
        int[] c = rgb(hex);
        return String.format(Locale.ROOT, "rgba(%d,%d,%d,%.2f)", c[0], c[1], c[2], alpha);
    }
}
