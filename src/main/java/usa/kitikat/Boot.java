package usa.kitikat;

/**
 * Точка входа, которая сама не наследует {@link javafx.application.Application}.
 *
 * <p>JavaFX отказывается стартовать, если класс с {@code main()} наследует
 * Application, а классы JavaFX лежат в classpath (без module path) — именно
 * так устроен портативный exe, и на этом он падал с сообщением
 * «JavaFX runtime components are missing». Отдельный «чистый» main
 * обходит эту проверку.</p>
 */
public final class Boot {

    private Boot() {
    }

    public static void main(String[] args) {
        AttackLauncher.main(args);
    }
}
