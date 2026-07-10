import java.nio.file.*;
import java.nio.charset.StandardCharsets;

public class Unescape {
    public static void main(String[] args) throws Exception {
        Path path = Path.of("serial-debug-ui/src/main/resources/io/github/serialdebug/ui/i18n/messages_zh_CN.properties");
        String content = Files.readString(path, StandardCharsets.UTF_8);
        // Replace \uXXXX with actual chars
        content = content.replaceAll("\\\\u([0-9a-fA-F]{4})",
            m -> String.valueOf((char) Integer.parseInt(m.group(1), 16)));
        Files.writeString(path, content, StandardCharsets.UTF_8);
        System.out.println("Done");
    }
}