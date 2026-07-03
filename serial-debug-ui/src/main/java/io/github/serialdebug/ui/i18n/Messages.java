package io.github.serialdebug.ui.i18n;

import javafx.beans.binding.Bindings;
import javafx.beans.binding.StringBinding;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.MessageFormat;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.PropertyResourceBundle;
import java.util.ResourceBundle;

public class Messages {

    private static final String BASE_NAME = "io.github.serialdebug.ui.i18n.messages";

    private static final ResourceBundle.Control UTF8_CONTROL = new ResourceBundle.Control() {
        @Override
        public ResourceBundle newBundle(String baseName, Locale locale, String format,
                                         ClassLoader loader, boolean reload)
                throws IllegalAccessException, InstantiationException, IOException {
            String bundleName = toBundleName(baseName, locale);
            String resourceName = toResourceName(bundleName, "properties");
            var stream = loader.getResourceAsStream(resourceName);
            if (stream == null) return null;
            try (var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                return new PropertyResourceBundle(reader);
            }
        }
    };

    private Messages() {}

    private static ResourceBundle bundle() {
        return ResourceBundle.getBundle(BASE_NAME, LocaleManager.getInstance().get(), UTF8_CONTROL);
    }

    public static String get(String key) {
        try {
            return bundle().getString(key);
        } catch (MissingResourceException e) {
            return "!" + key + "!";
        }
    }

    public static String get(String key, Object... args) {
        try {
            String pattern = bundle().getString(key);
            return MessageFormat.format(pattern, args);
        } catch (MissingResourceException e) {
            return "!" + key + "!";
        }
    }

    public static StringBinding createStringBinding(String key) {
        return Bindings.createStringBinding(
                () -> get(key),
                LocaleManager.getInstance().localeProperty());
    }

    public static StringBinding createStringBinding(String key, Object... args) {
        return Bindings.createStringBinding(
                () -> get(key, args),
                LocaleManager.getInstance().localeProperty());
    }
}
