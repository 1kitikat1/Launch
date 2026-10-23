package usa.kitikat.account;

/**
 * Подписка, посчитанная по ключу.
 *
 * <p>Источник истины — сам ключ в базе, а не профиль: срок задаётся при
 * создании ключа (поле {@code days}), момент активации пишется один раз
 * (поле {@code activatedAt}), а владелец (поле {@code owner}) привязывает
 * ключ к аккаунту.</p>
 *
 * <p>В профиле эти данные намеренно не хранятся: иначе пользователь мог бы
 * сам записать себе любой срок. Правила базы не дают менять срок в ключе
 * после активации.</p>
 */
public record KeySubscription(
        String key,
        int days,
        long activatedAt,
        String owner,
        String hwid) {

    /** Ключ закреплён за этим аккаунтом. */
    public boolean ownedBy(String uid) {
        return owner != null && !owner.isBlank() && owner.equals(uid);
    }

    /** Срок бессрочный (ключ создан с days = 0). */
    public boolean forever() {
        return days <= 0;
    }

    /** Момент конца подписки в мс; 0 — «навсегда». */
    public long expiresAt() {
        if (forever()) {
            return 0;
        }
        return activatedAt + days * 86_400_000L;
    }

    /** Подписка действует прямо сейчас. */
    public boolean active() {
        if (owner == null || owner.isBlank()) {
            return false;               // ключ ещё не активирован
        }
        if (forever()) {
            return true;
        }
        return expiresAt() > System.currentTimeMillis();
    }

    /** Сколько дней осталось; -1 — навсегда; 0 — не действует. */
    public int daysLeft() {
        if (!active()) {
            return 0;
        }
        if (forever()) {
            return -1;
        }
        long left = expiresAt() - System.currentTimeMillis();
        return (int) Math.max(0, (left + 86_399_999L) / 86_400_000L);
    }

    /** Подпись срока для интерфейса. */
    public String termLabel() {
        if (owner == null || owner.isBlank()) {
            return "не активирован";
        }
        if (forever()) {
            return "навсегда";
        }
        int left = daysLeft();
        return left > 0
                ? "осталось дней: " + left
                : "срок истёк";
    }
}
