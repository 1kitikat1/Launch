package usa.kitikat.account;

/**
 * Профиль пользователя в базе: ник, роль, активированный ключ,
 * HWID компьютера и идентификатор активной сессии.
 *
 * <p>Роль по умолчанию — {@code free} (саморегистрация). После активации
 * ключа с FunPay роль становится {@code user}. {@code owner} выдаётся
 * вручную владельцу.</p>
 *
 * <p>Срок подписки здесь намеренно не хранится: он берётся из самого ключа
 * (см. {@link KeySubscription}), иначе пользователь мог бы выписать себе
 * любой срок обычным запросом к базе.</p>
 *
 * <p>{@code hwid} — отпечаток компьютера, с которого активировали ключ.
 * Аккаунт с другого ПК с этим отпечатком не совпадает.</p>
 *
 * <p>{@code activeSession} — id последнего входа. У каждого аккаунта
 * активна только одна сессия: вход на другом ПК перезаписывает поле,
 * после чего прошлый компьютер считается вытесненным.</p>
 */
public record AccountProfile(
        String uid,
        String username,
        String email,
        String role,
        String activatedKey,
        long createdAt,
        String activeSession,
        String hwid) {

    /** Роль с учётом значения по умолчанию. */
    public String roleOrFree() {
        return role == null || role.isBlank() ? "free" : role;
    }

    /** Есть ли активированный ключ (доступ к DLC). */
    public boolean hasKey() {
        return activatedKey != null && !activatedKey.isBlank();
    }

    /**
     * Роль для показа: owner — владелец, user — купленный ключ,
     * free — саморегистрация без ключа.
     */
    public String displayRole() {
        if ("owner".equals(role)) {
            return "owner";
        }
        if ("user".equals(role) || hasKey()) {
            return "user";
        }
        return "free";
    }

    /**
     * Совпадает ли локальная сессия с активной на сервере.
     *
     * <p>Пока на сервере сессии нет (старый профиль) — считаем,
     * что вход валидный, чтобы никого не выкидывать без причины.</p>
     */
    public boolean sessionMatches(String localSessionId) {
        String current = activeSession == null ? "" : activeSession.trim();
        if (current.isEmpty()) {
            return true;
        }
        String local = localSessionId == null ? "" : localSessionId.trim();
        return current.equals(local);
    }

    /** HWID этого ПК совпадает с привязанным к аккаунту. */
    public boolean hwidMatches(String currentHwid) {
        String bound = hwid == null ? "" : hwid.trim();
        if (bound.isEmpty()) {
            return true;                 // привязки ещё нет
        }
        String current = currentHwid == null ? "" : currentHwid.trim();
        return bound.equalsIgnoreCase(current);
    }
}
