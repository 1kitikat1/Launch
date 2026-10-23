package usa.kitikat.account;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.Instant;
import java.util.regex.Pattern;

/**
 * Вход по нику и паролю.
 *
 * <p>Уникальность ника обеспечивает сервер: ник — это логин аккаунта,
 * повторное создание с тем же ником отклоняется. Пароль хранится на
 * сервере в зашифрованном виде — в лаунчер он не попадает.</p>
 *
 * <p>Все тексты ошибок нейтральные — никаких названий серверной части.</p>
 */
public final class AuthService {

    /** Сессия пользователя после входа. */
    public record Session(String uid, String nick, String email, String photoUrl,
                          String idToken, String refreshToken, long expiresAt) {
    }

    private static final Pattern NICK = Pattern.compile("[A-Za-z0-9_]{3,16}");
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    private final AccountConfig config;

    public AuthService(AccountConfig config) {
        this.config = config;
    }

    /** Хватает ли настроек для входа. */
    public boolean configured() {
        return config.ready();
    }

    /** Регистрация: ник и пароль. Дубль ника сервер отклоняет. */
    public Session register(String nick, String password) throws AuthException {
        String clean = check(nick, password);
        return authRequest("accounts:signUp", clean, password,
                "Ник «" + clean + "» уже занят — попробуй другой",
                "Регистрация пока недоступна");
    }

    /** Вход по нику и паролю. */
    public Session login(String nick, String password) throws AuthException {
        String clean = check(nick, password);
        return authRequest("accounts:signInWithPassword", clean, password,
                "Неверный ник или пароль",
                "Вход пока недоступен");
    }

    /** Проверка ника и пароля до отправки на сервер. */
    private String check(String nick, String password) throws AuthException {
        String clean = nick == null ? "" : nick.trim();
        if (!NICK.matcher(clean).matches()) {
            throw new AuthException("Ник: 3–16 символов — латиница, цифры и «_»");
        }
        if (password == null || password.length() < 6) {
            throw new AuthException("Пароль: минимум 6 символов");
        }
        return clean;
    }

    /** Общий запрос входа/регистрации к серверу аккаунтов. */
    private Session authRequest(String method, String nick, String password,
                                String badCredentials, String disabled) throws AuthException {
        String email = nick.toLowerCase() + "@attack.local";
        String body = "{\"email\":\"" + jsonEscape(email)
                + "\",\"password\":\"" + jsonEscape(password)
                + "\",\"returnSecureToken\":true}";
        try {
            var response = HTTP.send(
                    HttpRequest.newBuilder(URI.create(
                                    "https://identitytoolkit.googleapis.com/v1/" + method
                                            + "?key=" + config.apiKey()))
                            .timeout(Duration.ofSeconds(30))
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString(body))
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
            var node = new com.fasterxml.jackson.databind.ObjectMapper()
                    .readTree(response.body());
            if (response.statusCode() != 200) {
                String code = node.path("error").path("message").asText("");
                log(method + ": сервер ответил " + response.statusCode() + " " + code);
                if (code.contains("EMAIL_EXISTS")) {
                    throw new AuthException(badCredentials);
                }
                if (code.contains("INVALID_PASSWORD")
                        || code.contains("INVALID_LOGIN_CREDENTIALS")
                        || code.contains("INVALID_EMAIL")
                        || code.contains("EMAIL_NOT_FOUND")) {
                    throw new AuthException(badCredentials);
                }
                if (code.contains("OPERATION_NOT_ALLOWED")) {
                    throw new AuthException(disabled);
                }
                if (code.contains("WEAK_PASSWORD")) {
                    throw new AuthException(
                            "Пароль слишком простой — минимум 6 символов");
                }
                throw new AuthException("Не удалось выполнить вход");
            }
            long seconds = parseSeconds(node.path("expiresIn").asText("3600"));
            return new Session(
                    node.path("localId").asText(""),
                    nick,
                    email,
                    "",
                    node.path("idToken").asText(""),
                    node.path("refreshToken").asText(""),
                    Instant.now().getEpochSecond() + seconds - 120);
        } catch (AuthException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new AuthException("Нет связи с сервером аккаунтов", ex);
        }
    }

    /** Продлевает сессию, если токен уже истёк. */
    public Session refreshIfNeeded(Session session) throws AuthException {
        if (session == null) {
            throw new AuthException("Вход не выполнен");
        }
        if (session.expiresAt() > Instant.now().getEpochSecond() + 120) {
            return session;
        }
        if (session.refreshToken() == null || session.refreshToken().isBlank()) {
            throw new AuthException("Сессия истекла — войди заново");
        }
        String body = "grant_type=refresh_token&refresh_token="
                + urlEncode(session.refreshToken());
        try {
            var response = HTTP.send(
                    HttpRequest.newBuilder(URI.create(
                                    "https://securetoken.googleapis.com/v1/token?key="
                                            + config.apiKey()))
                            .timeout(Duration.ofSeconds(30))
                            .header("Content-Type", "application/x-www-form-urlencoded")
                            .POST(HttpRequest.BodyPublishers.ofString(body))
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
            var node = new com.fasterxml.jackson.databind.ObjectMapper()
                    .readTree(response.body());
            if (response.statusCode() != 200
                    || node.path("id_token").asText("").isBlank()) {
                log("обновление сессии: HTTP " + response.statusCode());
                throw new AuthException("Сессия истекла — войди заново");
            }
            long seconds = parseSeconds(node.path("expires_in").asText("3600"));
            return new Session(session.uid(), session.nick(), session.email(),
                    session.photoUrl(), node.path("id_token").asText(""),
                    session.refreshToken(),
                    Instant.now().getEpochSecond() + seconds - 120);
        } catch (AuthException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new AuthException("Нет связи с сервером аккаунтов", ex);
        }
    }

    /* ---------------- профиль и ключи ---------------- */

    /** Читает профиль пользователя из базы (ник, роль, активированный ключ). */
    public AccountProfile fetchProfile(Session session) throws AuthException {
        if (session == null) {
            throw new AuthException("Вход не выполнен");
        }
        try {
            var response = HTTP.send(
                    HttpRequest.newBuilder(URI.create(config.rtdbUrl() + "users/"
                            + session.uid() + ".json?auth=" + urlEncode(session.idToken())))
                            .timeout(Duration.ofSeconds(30))
                            .GET()
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new AuthException("Не удалось загрузить профиль");
            }
            var node = new com.fasterxml.jackson.databind.ObjectMapper()
                    .readTree(response.body());
            if (node == null || node.isNull()) {
                // профиля ещё нет — его создаст ensureProfile
                return null;
            }
            return new AccountProfile(
                    session.uid(),
                    node.path("username").asText(session.nick()),
                    node.path("email").asText(session.email()),
                    node.path("role").asText("free"),
                    node.path("activatedKey").asText(""),
                    node.path("createdAt").asLong(0),
                    node.path("activeSession").asText(""),
                    node.path("hwid").asText(""));
        } catch (AuthException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new AuthException("Нет связи с сервером аккаунтов", ex);
        }
    }

    /**
     * Создаёт профиль при первом входе. Роль по умолчанию — free,
     * компьютер запоминается как привязка аккаунта.
     * Если профиль уже есть — сервер запись отклонит, это нормально.
     */
    public void ensureProfile(Session session) {
        if (session == null) {
            return;
        }
        String body = "{\"createdAt\":" + System.currentTimeMillis()
                + ",\"role\":\"free\",\"username\":\""
                + jsonEscape(session.nick())
                + "\",\"hwid\":\"" + jsonEscape(Hwid.get()) + "\"}";
        try {
            HTTP.send(
                    HttpRequest.newBuilder(URI.create(config.rtdbUrl() + "users/"
                            + session.uid() + ".json?auth=" + urlEncode(session.idToken())))
                            .timeout(Duration.ofSeconds(30))
                            .header("Content-Type", "application/json")
                            .PUT(HttpRequest.BodyPublishers.ofString(body))
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
        } catch (Exception ex) {
            log("ensureProfile: " + ex.getMessage());
        }
    }

    /**
     * Стартует новую сессию входа.
     *
     * <p>id пишется в профиль: пока в профиле один {@code activeSession},
     * вход считается сделанным именно отсюда. Вход с другого ПК
     * перезаписывает поле — прошлый компьютер при следующей проверке
     * закроется сам.</p>
     *
     * @return id новой сессии (его лаунчер хранит локально)
     */
    public String startSession(Session session, AccountProfile profile)
            throws AuthException {
        if (session == null) {
            throw new AuthException("Вход не выполнен");
        }
        if (profile == null) {
            ensureProfile(session);
        }
        String id = java.util.UUID.randomUUID().toString().replace("-", "");
        String body = "{\"activeSession\":\"" + id
                + "\",\"lastLoginAt\":" + System.currentTimeMillis()
                + ",\"lastHwid\":\"" + jsonEscape(Hwid.get()) + "\"}";
        try {
            var response = HTTP.send(
                    HttpRequest.newBuilder(URI.create(config.rtdbUrl() + "users/"
                            + session.uid() + ".json?auth="
                            + urlEncode(session.idToken())))
                            .timeout(Duration.ofSeconds(30))
                            .header("Content-Type", "application/json")
                            .method("PATCH",
                                    HttpRequest.BodyPublishers.ofString(body))
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new AuthException("Не удалось начать сессию");
            }
        } catch (AuthException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new AuthException("Нет связи с сервером аккаунтов", ex);
        }
        return id;
    }

    /**
     * Активирует ключ покупки: закрепляет его за аккаунтом и этим ПК.
     *
     * <p>У ключа поле {@code days}: 30/60/90/120 — дни подписки,
     * 0 — «навсегда». Срок и момент активации пишутся в сам ключ, а не в
     * профиль: в профиле пользователь мог бы подделать срок.</p>
     *
     * <p>Ключ активируется один раз: {@code owner} и {@code activatedAt}
     * записываются только когда ключ ещё свободен, а срок и текст ключа
     * правила базы менять не дают.</p>
     */
    public AccountProfile activateKey(Session session, String key) throws AuthException {
        if (session == null) {
            throw new AuthException("Вход не выполнен");
        }
        String clean = key == null ? "" : key.trim().toUpperCase();
        if (clean.isBlank()) {
            throw new AuthException("Введи ключ активации");
        }
        String token = urlEncode(session.idToken());

        // 1. Ключ должен существовать
        var node = readKey(clean, token);
        if (node == null) {
            throw new AuthException("Ключ не найден");
        }
        String existingValue = node.path("value").asText("");
        String owner = node.path("owner").asText("");

        if (!owner.isBlank() && !owner.equals(session.uid())) {
            throw new AuthException("Этот ключ уже активирован другим аккаунтом");
        }
        if (existingValue.isBlank() && node.path("days").asInt(0) <= 0) {
            throw new AuthException("Ключ повреждён — обратись к владельцу");
        }

        long now = System.currentTimeMillis();

        // 2. Закрепляем ключ за аккаунтом и ПК (только если ещё свободен).
        //    value и days не трогаем — правило базы не даёт их менять.
        if (owner.isBlank()) {
            String body = "{\"owner\":\"" + jsonEscape(session.uid())
                    + "\",\"activatedAt\":" + now
                    + ",\"hwid\":\"" + jsonEscape(Hwid.get()) + "\"}";
            try {
                var response = HTTP.send(
                        HttpRequest.newBuilder(URI.create(config.rtdbUrl() + "keys/"
                                + urlEncode(clean) + ".json?auth=" + token))
                                .timeout(Duration.ofSeconds(30))
                                .header("Content-Type", "application/json")
                                .method("PATCH",
                                        HttpRequest.BodyPublishers.ofString(body))
                                .build(),
                        HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() != 200) {
                    log("activateKey: HTTP " + response.statusCode()
                            + " body=" + response.body());
                    throw new AuthException("Не удалось активировать ключ (код "
                            + response.statusCode() + ") — напиши владельцу");
                }
            } catch (AuthException ex) {
                throw ex;
            } catch (Exception ex) {
                throw new AuthException("Нет связи с сервером аккаунтов", ex);
            }
        }

        // 3. Отмечаем ключ в профиле (профиля может ещё не быть)
        if (fetchProfile(session) == null) {
            ensureProfile(session);
        }
        writeActivatedKey(session, clean, token);
        return fetchProfile(session);
    }

    /**
     * Читает данные ключа: срок, момент активации и владельца.
     * null — ключа нет.
     */
    public KeySubscription fetchKeyInfo(Session session, String key)
            throws AuthException {
        if (session == null) {
            throw new AuthException("Вход не выполнен");
        }
        String clean = key == null ? "" : key.trim().toUpperCase();
        if (clean.isBlank()) {
            return null;
        }
        var node = readKey(clean, urlEncode(session.idToken()));
        if (node == null) {
            return null;
        }
        return new KeySubscription(
                clean,
                node.path("days").asInt(0),
                node.path("activatedAt").asLong(0),
                node.path("owner").asText(""),
                node.path("hwid").asText(""));
    }

    private com.fasterxml.jackson.databind.JsonNode readKey(
            String key, String token) throws AuthException {
        try {
            var response = HTTP.send(
                    HttpRequest.newBuilder(URI.create(config.rtdbUrl() + "keys/"
                            + key + ".json?auth=" + token))
                            .timeout(Duration.ofSeconds(30))
                            .GET()
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                return null;
            }
            var node = new com.fasterxml.jackson.databind.ObjectMapper()
                    .readTree(response.body());
            return node == null || node.isNull() ? null : node;
        } catch (Exception ex) {
            throw new AuthException("Нет связи с сервером аккаунтов", ex);
        }
    }

    /** Привязывает текущий ПК к аккаунту, если привязки ещё нет. */
    public void bindHwid(Session session) {
        if (session == null) {
            return;
        }
        try {
            HTTP.send(
                    HttpRequest.newBuilder(URI.create(config.rtdbUrl() + "users/"
                            + session.uid() + "/hwid.json?auth="
                            + urlEncode(session.idToken())))
                            .timeout(Duration.ofSeconds(30))
                            .header("Content-Type", "application/json")
                            .PUT(HttpRequest.BodyPublishers.ofString(
                                    "\"" + jsonEscape(Hwid.get()) + "\""))
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
        } catch (Exception ex) {
            log("bindHwid: " + ex.getMessage());
        }
    }

    /* ---------------- управление ключами (для владельца) ---------------- */

    /** Запись ключа в списке владельца. */
    public record KeyInfo(String key, int days, String owner,
                          String hwid, long createdAt, long activatedAt) {
        /** Свободен ли ключ (ещё никем не активирован). */
        public boolean free() {
            return owner == null || owner.isBlank();
        }
    }

    /**
     * Создаёт ключ с произвольным сроком.
     *
     * @param key      текст ключа (если пусто — сгенерируется)
     * @param days     7/30/60/90/120… — дни; 0 — «навсегда»
     * @param ownerUid uid аккаунта, за которым сразу закрепить ключ;
     *                 пусто — свободный ключ для продажи
     * @return созданный ключ (его текст нужно показать владельцу)
     */
    public String createKey(Session session, String key, int days, String ownerUid)
            throws AuthException {
        requireOwner(session);
        String clean = key == null ? "" : key.trim().toUpperCase();
        if (clean.isBlank()) {
            clean = generateKey();
        }
        String ownerValue = ownerUid == null ? "" : ownerUid.trim();
        // свободный ключ: owner должен быть именно null, иначе правило базы
        // не пропустит активацию (пустая строка — это не null)
        String body = "{\"value\":\"" + jsonEscape(clean)
                + "\",\"days\":" + Math.max(0, days)
                + ",\"createdAt\":" + System.currentTimeMillis()
                + ",\"owner\":" + (ownerValue.isBlank()
                        ? "null" : "\"" + jsonEscape(ownerValue) + "\"")
                + ",\"hwid\":\"\",\"activatedAt\":0}";
        try {
            var response = HTTP.send(
                    HttpRequest.newBuilder(URI.create(config.rtdbUrl() + "keys/"
                            + urlEncode(clean) + ".json?auth="
                            + urlEncode(session.idToken())))
                            .timeout(Duration.ofSeconds(30))
                            .header("Content-Type", "application/json")
                            .PUT(HttpRequest.BodyPublishers.ofString(body))
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new AuthException("Не удалось создать ключ");
            }
        } catch (AuthException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new AuthException("Нет связи с сервером аккаунтов", ex);
        }
        return clean;
    }

    /**
     * Ищет uid аккаунта по нику — для привязки ключа к человеку
     * в панели владельца. null — такого ника нет.
     */
    public String findUidByNick(Session session, String nick)
            throws AuthException {
        requireOwner(session);
        String clean = nick == null ? "" : nick.trim();
        if (clean.isBlank()) {
            throw new AuthException("Введи ник");
        }
        try {
            // RTDB отдаёт все профили; ищем совпадение по username
            var response = HTTP.send(
                    HttpRequest.newBuilder(URI.create(config.rtdbUrl()
                            + "users.json?auth="
                            + urlEncode(session.idToken())))
                            .timeout(Duration.ofSeconds(60))
                            .GET()
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new AuthException("Не удалось найти аккаунт");
            }
            var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            var root = mapper.readTree(response.body());
            if (root != null && root.isObject()) {
                var fields = root.fieldNames();
                while (fields.hasNext()) {
                    String uid = fields.next();
                    String name = root.path(uid).path("username").asText("");
                    if (name.equalsIgnoreCase(clean)) {
                        return uid;
                    }
                }
            }
            throw new AuthException("Ник «" + clean + "» не найден");
        } catch (AuthException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new AuthException("Нет связи с сервером аккаунтов", ex);
        }
    }

    /** Список всех ключей для панели владельца. */
    public java.util.List<KeyInfo> listKeys(Session session) throws AuthException {
        requireOwner(session);
        try {
            var response = HTTP.send(
                    HttpRequest.newBuilder(URI.create(config.rtdbUrl()
                            + "keys.json?auth="
                            + urlEncode(session.idToken())))
                            .timeout(Duration.ofSeconds(30))
                            .GET()
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new AuthException("Не удалось загрузить ключи");
            }
            var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            var root = mapper.readTree(response.body());
            var out = new java.util.ArrayList<KeyInfo>();
            if (root != null && root.isObject()) {
                var fields = root.fieldNames();
                while (fields.hasNext()) {
                    String name = fields.next();
                    var node = root.path(name);
                    // старые ключи могли сохранить owner как "" — правило
                    // активации требует именно null, поэтому чиним на месте
                    if (node.has("owner")
                            && node.get("owner").isTextual()
                            && node.get("owner").asText().isBlank()) {
                        clearOwner(session, name);
                    }
                    out.add(new KeyInfo(
                            node.path("value").asText(name),
                            node.path("days").asInt(0),
                            node.path("owner").asText(""),
                            node.path("hwid").asText(""),
                            node.path("createdAt").asLong(0),
                            node.path("activatedAt").asLong(0)));
                }
            }
            out.sort((a, b) -> Long.compare(b.createdAt(), a.createdAt()));
            return out;
        } catch (AuthException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new AuthException("Нет связи с сервером аккаунтов", ex);
        }
    }

    /** Ставит owner=null у ключа (чинит ключи, созданные с пустой строкой). */
    private void clearOwner(Session session, String key) {
        try {
            HTTP.send(
                    HttpRequest.newBuilder(URI.create(config.rtdbUrl() + "keys/"
                            + urlEncode(key) + "/owner.json?auth="
                            + urlEncode(session.idToken())))
                            .timeout(Duration.ofSeconds(20))
                            .header("Content-Type", "application/json")
                            .PUT(HttpRequest.BodyPublishers.ofString("null"))
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
        } catch (Exception ex) {
            log("clearOwner: " + ex.getMessage());
        }
    }

    /** Полностью удаляет ключ (даже активированный). */
    public void deleteKey(Session session, String key) throws AuthException {
        requireOwner(session);
        try {
            var response = HTTP.send(
                    HttpRequest.newBuilder(URI.create(config.rtdbUrl() + "keys/"
                            + urlEncode(key) + ".json?auth="
                            + urlEncode(session.idToken())))
                            .timeout(Duration.ofSeconds(30))
                            .DELETE()
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new AuthException("Не удалось удалить ключ");
            }
        } catch (AuthException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new AuthException("Нет связи с сервером аккаунтов", ex);
        }
    }

    /**
     * Освобождает активированный ключ: снимает привязку к аккаунту и ПК,
     * ключ снова может активировать кто угодно. Срок сохраняется.
     */
    public void releaseKey(Session session, String key) throws AuthException {
        requireOwner(session);
        try {
            var response = HTTP.send(
                    HttpRequest.newBuilder(URI.create(config.rtdbUrl() + "keys/"
                            + urlEncode(key) + ".json?auth="
                            + urlEncode(session.idToken())))
                            .timeout(Duration.ofSeconds(30))
                            .header("Content-Type", "application/json")
                            .method("PATCH",
                                    HttpRequest.BodyPublishers.ofString(
                                            "{\"owner\":null,\"hwid\":null,\"activatedAt\":0}"))
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new AuthException("Не удалось освободить ключ");
            }
        } catch (AuthException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new AuthException("Нет связи с сервером аккаунтов", ex);
        }
    }

    /**
     * Сбрасывает привязку ПК к ключу: снимает hwid, ключ можно
     * активировать на другом компьютере. Аккаунт и срок сохраняются.
     */
    public void resetKeyHwid(Session session, String key) throws AuthException {
        requireOwner(session);
        try {
            var response = HTTP.send(
                    HttpRequest.newBuilder(URI.create(config.rtdbUrl() + "keys/"
                            + urlEncode(key) + ".json?auth="
                            + urlEncode(session.idToken())))
                            .timeout(Duration.ofSeconds(30))
                            .header("Content-Type", "application/json")
                            .method("PATCH",
                                    HttpRequest.BodyPublishers.ofString(
                                            "{\"hwid\":null}"))
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new AuthException("Не удалось сбросить привязку ПК");
            }
        } catch (AuthException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new AuthException("Нет связи с сервером аккаунтов", ex);
        }
    }

    /** Меняет срок у уже существующего ключа. */
    public void setKeyDays(Session session, String key, int days)
            throws AuthException {
        requireOwner(session);
        try {
            var response = HTTP.send(
                    HttpRequest.newBuilder(URI.create(config.rtdbUrl() + "keys/"
                            + urlEncode(key) + "/days.json?auth="
                            + urlEncode(session.idToken())))
                            .timeout(Duration.ofSeconds(30))
                            .header("Content-Type", "application/json")
                            .PUT(HttpRequest.BodyPublishers.ofString(
                                    Integer.toString(Math.max(0, days))))
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new AuthException("Не удалось изменить срок ключа");
            }
        } catch (AuthException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new AuthException("Нет связи с сервером аккаунтов", ex);
        }
    }

    /** Снимает привязку ПК с аккаунта (перенос на другой компьютер). */
    public void unbindHwid(Session session, String uid) throws AuthException {
        requireOwner(session);
        try {
            var response = HTTP.send(
                    HttpRequest.newBuilder(URI.create(config.rtdbUrl() + "users/"
                            + urlEncode(uid) + "/hwid.json?auth="
                            + urlEncode(session.idToken())))
                            .timeout(Duration.ofSeconds(30))
                            .DELETE()
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new AuthException("Не удалось снять привязку ПК");
            }
        } catch (AuthException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new AuthException("Нет связи с сервером аккаунтов", ex);
        }
    }

    /** Проверка: операции управления доступны только владельцу. */
    private void requireOwner(Session session) throws AuthException {
        if (session == null) {
            throw new AuthException("Вход не выполнен");
        }
        AccountProfile profile = fetchProfile(session);
        if (profile == null || !"owner".equals(profile.roleOrFree())) {
            throw new AuthException(
                    "Управление ключами доступно только владельцу");
        }
    }

    /** Свободный ключ вида ATK-XXXX-XXXX-XXXX. */
    private static String generateKey() {
        String alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        java.util.Random random = new java.util.Random();
        StringBuilder key = new StringBuilder("ATK-");
        for (int i = 0; i < 12; i++) {
            if (i > 0 && i % 4 == 0) {
                key.append('-');
            }
            key.append(alphabet.charAt(random.nextInt(alphabet.length())));
        }
        return key.toString();
    }

    /**
     * Отмечает в профиле, какой ключ активирован.
     *
     * <p>Только имя ключа: срок и дату активации база берёт из самого ключа,
     * поэтому подделать подписку в профиле невозможно.</p>
     */
    private void writeActivatedKey(Session session, String key, String token) {
        try {
            HTTP.send(
                    HttpRequest.newBuilder(URI.create(config.rtdbUrl() + "users/"
                            + session.uid() + ".json?auth=" + token))
                            .timeout(Duration.ofSeconds(30))
                            .header("Content-Type", "application/json")
                            .method("PATCH",
                                    HttpRequest.BodyPublishers.ofString(
                                            "{\"activatedKey\":\"" + jsonEscape(key)
                                                    + "\"}"))
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
        } catch (Exception ex) {
            log("writeActivatedKey: " + ex.getMessage());
        }
    }

    /* ---------------- мелочи ---------------- */

    private static long parseSeconds(String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ex) {
            return 3600;
        }
    }

    private static String jsonEscape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String urlEncode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    /** Технический журнал входа (только для разбора сбоев). */
    private static void log(String message) {
        try {
            String local = System.getenv("LOCALAPPDATA");
            if (local == null || local.isBlank()) {
                return;
            }
            Path dir = Path.of(local, "AttackDLC");
            Files.createDirectories(dir);
            Files.writeString(dir.resolve("account.log"),
                    Instant.now() + "  " + message + System.lineSeparator(),
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (Exception ignored) {
        }
    }
}
