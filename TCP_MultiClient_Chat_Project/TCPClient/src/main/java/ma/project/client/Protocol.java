package ma.project.client;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

final class Protocol {
    private Protocol() {}
    static String enc(String value) {
        return Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }
    static String dec(String value) {
        return new String(Base64.getDecoder().decode(value), StandardCharsets.UTF_8);
    }
}
