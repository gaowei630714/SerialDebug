package io.github.serialdebug.ui.i18n;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import java.io.*;
import java.nio.file.*;
import java.util.Locale;
import java.util.Properties;

public class LocaleManager {

    private static final LocaleManager INSTANCE = new LocaleManager();
    private static final String PREFS_DIR = ".serialdebug";
    private static final String PREFS_FILE = "preferences.properties";
    private static final String KEY = "locale";

    private final ObjectProperty<Locale> currentLocale = new SimpleObjectProperty<>();
    private volatile Locale volatileLocale;

    private LocaleManager() {
        Locale saved = loadSavedLocale();
        volatileLocale = saved != null ? saved : Locale.getDefault();
        currentLocale.set(volatileLocale);
    }

    public static LocaleManager getInstance() { return INSTANCE; }
    public ObjectProperty<Locale> localeProperty() { return currentLocale; }
    public Locale get() { return volatileLocale; }

    public void set(Locale locale) {
        volatileLocale = locale;
        currentLocale.set(locale);
        saveLocale(locale);
    }

    public void toggle() {
        Locale current = get();
        if (Locale.CHINESE.getLanguage().equals(current.getLanguage())) {
            set(Locale.ENGLISH);
        } else {
            set(Locale.CHINESE);
        }
    }

    private Locale loadSavedLocale() {
        Path path = Path.of(System.getProperty("user.home"), PREFS_DIR, PREFS_FILE);
        if (!Files.exists(path)) return null;
        try (InputStream is = Files.newInputStream(path)) {
            Properties props = new Properties();
            props.load(is);
            String lang = props.getProperty(KEY);
            return lang != null ? Locale.of(lang) : null;
        } catch (IOException e) {
            return null;
        }
    }

    private void saveLocale(Locale locale) {
        try {
            Path dir = Path.of(System.getProperty("user.home"), PREFS_DIR);
            Files.createDirectories(dir);
            Path path = dir.resolve(PREFS_FILE);
            Properties props = new Properties();
            if (Files.exists(path)) {
                try (InputStream is = Files.newInputStream(path)) {
                    props.load(is);
                }
            }
            props.setProperty(KEY, locale.getLanguage());
            try (OutputStream os = Files.newOutputStream(path)) {
                props.store(os, null);
            }
        } catch (IOException e) {
            System.err.println("Failed to save locale preference: " + e.getMessage());
        }
    }
}
