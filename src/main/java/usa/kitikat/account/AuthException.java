package usa.kitikat.account;

/**
 * Ошибка входа с готовым текстом для пользователя: без адресов,
 * без названий серверной части, без технических подробностей.
 */
public final class AuthException extends Exception {

    public AuthException(String message) {
        super(message);
    }

    public AuthException(String message, Throwable cause) {
        super(message, cause);
    }
}
