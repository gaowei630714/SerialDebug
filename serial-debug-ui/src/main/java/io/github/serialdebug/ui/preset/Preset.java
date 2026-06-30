package io.github.serialdebug.ui.preset;

/**
 * A user-defined command preset that fills the send text field.
 */
public class Preset {

    private String name;
    private String data;

    /** Default constructor required by Jackson. */
    public Preset() {
        this("", "");
    }

    public Preset(String name, String data) {
        this.name = name;
        this.data = data;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getData() {
        return data;
    }

    public void setData(String data) {
        this.data = data;
    }
}
