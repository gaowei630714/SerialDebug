package io.github.serialdebug.ui.at;

/**
 * An AT command template for the AT companion panel.
 * Jackson-compatible POJO (default constructor + getters/setters).
 */
public class AtCommand {

    private String name;
    private String command;
    private String description;

    /** Default constructor required by Jackson. */
    public AtCommand() {
        this("", "", "");
    }

    public AtCommand(String name, String command, String description) {
        this.name = name;
        this.command = command;
        this.description = description;
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getCommand() { return command; }
    public void setCommand(String command) { this.command = command; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    @Override
    public String toString() {
        return name + "  " + command;
    }
}
