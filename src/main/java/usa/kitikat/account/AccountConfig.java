package usa.kitikat.account;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Конфигурация аккаунта Attack DLC. Все ключи нейтральные: ни в коде,
 * ни в сообщениях для пользователя нет названий серверной части.
 *
 * <p>Поля:
 * <ul>
 *   <li>{@code apiKey}/{@code projectId} — вход по нику и паролю;</li>
 *   <li>{@code rtdbUrl} — адрес базы с профилями и ключами;</li>
 *   <li>{@code clientVersion} — версия клиента для Discord RPC;</li>
 *   <li>{@code funpayUrl}/{@code siteUrl} — ссылки-кнопки в Discord RPC.</li>
 * </ul>
 */
public record AccountConfig(
        String apiKey,
        String projectId,
        String rtdbUrl,
        String clientVersion,
        String funpayUrl,
        String siteUrl) {

    private static final String RESOURCE = "/account-config.json";

    /** Встроенная конфигурация; внешний файл может переопределить поля. */
    public static AccountConfig load() {
        String apiKey = "";
        String projectId = "";
        String rtdbUrl = "";
        String clientVersion = "1.0";
        String funpayUrl = "";
        String siteUrl = "";
        try (InputStream in = AccountConfig.class.getResourceAsStream(RESOURCE)) {
            if (in != null) {
                var node = new com.fasterxml.jackson.databind.ObjectMapper().readTree(in);
                apiKey = node.path("apiKey").asText("");
                projectId = node.path("projectId").asText("");
                rtdbUrl = node.path("rtdbUrl").asText("");
                clientVersion = node.path("clientVersion").asText("1.0");
                funpayUrl = node.path("funpayUrl").asText("");
                siteUrl = node.path("siteUrl").asText("");
            }
        } catch (Exception ignored) {
            // без конфигурации вход просто будет недоступен
        }
        Path external = externalPath();
        if (external != null && Files.isRegularFile(external)) {
            try {
                var node = new com.fasterxml.jackson.databind.ObjectMapper()
                        .readTree(Files.readString(external));
                apiKey = firstNonBlank(node.path("apiKey").asText(""), apiKey);
                projectId = firstNonBlank(node.path("projectId").asText(""), projectId);
                rtdbUrl = firstNonBlank(node.path("rtdbUrl").asText(""), rtdbUrl);
                clientVersion = firstNonBlank(
                        node.path("clientVersion").asText(""), clientVersion);
                funpayUrl = firstNonBlank(node.path("funpayUrl").asText(""), funpayUrl);
                siteUrl = firstNonBlank(node.path("siteUrl").asText(""), siteUrl);
            } catch (Exception ignored) {
                // битый внешний файл просто игнорируем
            }
        }
        return new AccountConfig(
                apiKey, projectId, rtdbUrl, clientVersion, funpayUrl, siteUrl);
    }

    /** Путь внешнего файла настроек входа (правится без пересборки). */
    public static Path externalPath() {
        String local = System.getenv("LOCALAPPDATA");
        if (local == null || local.isBlank()) {
            return null;
        }
        return Path.of(local, "AttackDLC", "account-config.json");
    }

    private static String firstNonBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    /** Хватает ли данных для входа. */
    public boolean ready() {
        return !apiKey.isBlank() && !rtdbUrl.isBlank();
    }
}
