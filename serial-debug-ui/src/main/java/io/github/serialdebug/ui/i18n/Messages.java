package io.github.serialdebug.ui.i18n;

import javafx.beans.binding.Bindings;
import javafx.beans.binding.StringBinding;
import java.text.MessageFormat;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

public class Messages {

    private static final String BASE_NAME = "io.github.serialdebug.ui.i18n.messages";

    private Messages() {}

    public static String get(String key) {
        try {
            return ResourceBundle.getBundle(BASE_NAME, LocaleManager.getInstance().get()).getString(key);
        } catch (MissingResourceException e) {
            return "!" + key + "!";
        }
    }

    public static String get(String key, Object... args) {
        try {
            String pattern = ResourceBundle.getBundle(BASE_NAME, LocaleManager.getInstance().get()).getString(key);
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
