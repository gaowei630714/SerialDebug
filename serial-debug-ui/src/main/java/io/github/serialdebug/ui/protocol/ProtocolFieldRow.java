package io.github.serialdebug.ui.protocol;

import io.github.serialdebug.protocol.ProtocolField;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;

/** Mutable row for the protocol field table (UI view of immutable ProtocolField). */
public class ProtocolFieldRow {

    private final SimpleStringProperty name = new SimpleStringProperty();
    private final SimpleStringProperty label = new SimpleStringProperty();
    private final SimpleIntegerProperty offset = new SimpleIntegerProperty();
    private final SimpleIntegerProperty size = new SimpleIntegerProperty(1);
    private final SimpleStringProperty type = new SimpleStringProperty("uint8");
    private final SimpleStringProperty typeDisplay = new SimpleStringProperty("uint8");
    private final SimpleStringProperty scale = new SimpleStringProperty("1.0");
    private final SimpleStringProperty bias = new SimpleStringProperty("0.0");
    private final SimpleStringProperty bits = new SimpleStringProperty("");
    private final SimpleBooleanProperty enabled = new SimpleBooleanProperty(true);

    public ProtocolFieldRow(ProtocolField field) {
        setName(field.name());
        setLabel(field.label());
        setOffset(field.offset());
        setSize(field.size());
        setType(field.type());
        setScale(String.valueOf(field.scale()));
        setBias(String.valueOf(field.bias()));
        if (field.bits() != null && !field.bits().isEmpty()) {
            setBits(field.bits().toString());
        }
        setEnabled(field.enabled());
    }

    private static double toDouble(String s, double defaultValue) {
        try { return Double.parseDouble(s); } catch (NumberFormatException e) { return defaultValue; }
    }

    public ProtocolFieldRow() {
        // no-arg constructor for new-field buttons; defaults are set at field level
    }

    public ProtocolField toProtocolField() {
        List<Integer> bitsList = parseBits(getBits());
        return new ProtocolField(getName(), getLabel(), getOffset(), getSize(),
                getType(), toDouble(getScale(), 1.0), toDouble(getBias(), 0.0),
                bitsList, isEnabled());
    }

    private List<Integer> parseBits(String bitsText) {
        List<Integer> list = new ArrayList<>();
        if (bitsText == null || bitsText.isBlank()) return list;
        String cleaned = bitsText.trim().replace("[", "").replace("]", "");
        for (String part : cleaned.split(",")) {
            String p = part.trim();
            if (p.isBlank()) continue;
            try {
                list.add(Integer.parseInt(p));
            } catch (NumberFormatException ignored) {
                // Silently skip malformed tokens so one bad bit doesn't block save
            }
        }
        return list;
    }

    // Properties
    public SimpleStringProperty nameProperty() { return name; }
    public SimpleStringProperty labelProperty() { return label; }
    public SimpleIntegerProperty offsetProperty() { return offset; }
    public SimpleIntegerProperty sizeProperty() { return size; }
    public SimpleStringProperty typeProperty() { return type; }
    public SimpleStringProperty typeDisplayProperty() { return typeDisplay; }
    public SimpleStringProperty scaleProperty() { return scale; }
    public SimpleStringProperty biasProperty() { return bias; }
    public SimpleStringProperty bitsProperty() { return bits; }
    public SimpleBooleanProperty enabledProperty() { return enabled; }

    public String getName() { return name.get(); }
    public void setName(String n) { name.set(n); }
    public String getLabel() { return label.get(); }
    public void setLabel(String l) { label.set(l); }
    public int getOffset() { return offset.get(); }
    public void setOffset(int o) { offset.set(o); }
    public int getSize() { return size.get(); }
    public void setSize(int s) { size.set(s); }
    public String getType() { return type.get(); }
    public void setType(String t) { type.set(t); typeDisplay.set(t); }
    public String getScale() { return scale.get(); }
    public void setScale(String s) { scale.set(s); }
    public String getBias() { return bias.get(); }
    public void setBias(String b) { bias.set(b); }
    public String getBits() { return bits.get(); }
    public void setBits(String b) { bits.set(b); }
    public boolean isEnabled() { return enabled.get(); }
    public void setEnabled(boolean e) { enabled.set(e); }
}
