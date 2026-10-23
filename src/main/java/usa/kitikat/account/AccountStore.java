package usa.kitikat.account;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Локальное сохранение сессии и профиля аккаунта (файл в папке лаунчера). */
public final class AccountStore {

    private AuthService.Session session;
    private AccountProfile profile;
    /** id текущей сессии на этом ПК (для проверки активного входа). */
    private String sessionId;
    /** Локальный кэш подписки: срок и момент активации ключа. */
    private KeySubscription subscription;

    public AccountStore() {
        load();
    }

    /** null — если не вошёл. */
    public AuthService.Session session() {
        return session;
    }

    /** Профиль аккаунта; null — если не вошёл или ещё не загружен. */
    public AccountProfile profile() {
        return profile;
    }

    /** id сессии этого ПК; null — если входа на этом ПК ещё не было. */
    public String sessionId() {
        return sessionId;
    }

    public void saveSessionId(String value) {
        this.sessionId = value;
        write();
    }

    /** Ник для запуска игры; null — если не вошёл или ник ещё не задан. */
    public String nick() {
        return session == null || session.nick() == null || session.nick().isBlank()
                ? null
                : session.nick();
    }

    public void save(AuthService.Session value) {
        this.session = value;
        write();
    }

    public void saveProfile(AccountProfile value) {
        this.profile = value;
        write();
    }

    /** Кэш подписки (срок по ключу); null — подписки нет. */
    public KeySubscription subscription() {
        return subscription;
    }

    /** Сохраняет кэш подписки, полученный с сервера. */
    public void saveSubscription(KeySubscription value) {
        this.subscription = value;
        write();
    }

    public void clear() {
        this.session = null;
        this.profile = null;
        this.sessionId = null;
        this.subscription = null;
        write();
    }

    private static Path path() {
        String local = System.getenv("LOCALAPPDATA");
        if (local == null || local.isBlank()) {
            return null;
        }
        return Path.of(local, "AttackDLC", "account.json");
    }

    private void load() {
        Path file = path();
        if (file == null || !Files.isRegularFile(file)) {
            return;
        }
        try {
            var node = new com.fasterxml.jackson.databind.ObjectMapper()
                    .readTree(Files.readString(file, StandardCharsets.UTF_8));
            String uid = node.path("uid").asText("");
            if (!uid.isBlank()) {
                this.session = new AuthService.Session(
                        uid,
                        node.path("nick").asText(""),
                        node.path("email").asText(""),
                        node.path("photo").asText(""),
                        node.path("idToken").asText(""),
                        node.path("refreshToken").asText(""),
                        node.path("expiresAt").asLong(0));
                this.sessionId = node.path("sessionId").asText("");
                if (this.sessionId.isBlank()) {
                    this.sessionId = null;
                }
                this.profile = new AccountProfile(
                        uid,
                        node.path("username").asText(node.path("nick").asText("")),
                        node.path("email").asText(""),
                        node.path("role").asText("free"),
                        node.path("activatedKey").asText(""),
                        node.path("createdAt").asLong(0),
                        node.path("serverSession").asText(""),
                        node.path("hwid").asText(""));
                String key = node.path("activatedKey").asText("");
                if (!key.isBlank()) {
                    this.subscription = new KeySubscription(
                            key,
                            node.path("keyDays").asInt(0),
                            node.path("keyActivatedAt").asLong(0),
                            uid,
                            node.path("hwid").asText(""));
                }
            }
        } catch (Exception ignored) {
            // битый файл просто игнорируем
        }
    }

    private void write() {
        Path file = path();
        if (file == null) {
            return;
        }
        try {
            Files.createDirectories(file.getParent());
            var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            var object = mapper.createObjectNode();
            var s = session;
            object.put("uid", s == null ? "" : s.uid() == null ? "" : s.uid());
            object.put("nick", s == null ? "" : s.nick() == null ? "" : s.nick());
            object.put("email", s == null ? "" : s.email() == null ? "" : s.email());
            object.put("photo",
                    s == null ? "" : s.photoUrl() == null ? "" : s.photoUrl());
            object.put("idToken",
                    s == null ? "" : s.idToken() == null ? "" : s.idToken());
            object.put("refreshToken", s == null || s.refreshToken() == null
                    ? "" : s.refreshToken());
            object.put("expiresAt", s == null ? 0 : s.expiresAt());
            object.put("sessionId", sessionId == null ? "" : sessionId);
            var p = profile;
            object.put("username",
                    p == null ? "" : p.username() == null ? "" : p.username());
            object.put("role", p == null ? "free" : p.roleOrFree());
            object.put("activatedKey",
                    p == null || p.activatedKey() == null ? "" : p.activatedKey());
            object.put("createdAt", p == null ? 0 : p.createdAt());
            object.put("serverSession",
                    p == null || p.activeSession() == null ? "" : p.activeSession());
            object.put("hwid", p == null || p.hwid() == null ? "" : p.hwid());
            var sub = subscription;
            object.put("keyDays", sub == null ? 0 : sub.days());
            object.put("keyActivatedAt", sub == null ? 0 : sub.activatedAt());
            Files.writeString(file, mapper.writeValueAsString(object),
                    StandardCharsets.UTF_8);
        } catch (Exception ignored) {
        }
    }
}
