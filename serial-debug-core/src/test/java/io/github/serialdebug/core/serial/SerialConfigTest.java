package io.github.serialdebug.core.serial;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SerialConfigTest {

    @Test
    void shouldCreateDefaultConfig() {
        SerialConfig config = new SerialConfig();
        assertEquals("", config.getPortName());
        assertEquals(115200, config.getBaudRate());
        assertEquals(8, config.getDataBits());
        assertEquals(1, config.getStopBits());
        assertEquals(SerialConfig.Parity.NONE, config.getParity());
        assertEquals(SerialConfig.FlowControl.NONE, config.getFlowControl());
    }

    @Test
    void shouldSetAndGetPortName() {
        SerialConfig config = new SerialConfig();
        config.setPortName("COM3");
        assertEquals("COM3", config.getPortName());
    }

    @Test
    void shouldSetAndGetBaudRate() {
        SerialConfig config = new SerialConfig();
        config.setBaudRate(9600);
        assertEquals(9600, config.getBaudRate());
    }

    @Test
    void shouldRejectInvalidBaudRate() {
        SerialConfig config = new SerialConfig();
        assertThrows(IllegalArgumentException.class, () -> config.setBaudRate(0));
        assertThrows(IllegalArgumentException.class, () -> config.setBaudRate(-1));
    }

    @Test
    void shouldSetAndGetDataBits() {
        SerialConfig config = new SerialConfig();
        config.setDataBits(7);
        assertEquals(7, config.getDataBits());
    }

    @Test
    void shouldRejectInvalidDataBits() {
        SerialConfig config = new SerialConfig();
        assertThrows(IllegalArgumentException.class, () -> config.setDataBits(4));
        assertThrows(IllegalArgumentException.class, () -> config.setDataBits(9));
    }

    @Test
    void shouldSetAndGetStopBits() {
        SerialConfig config = new SerialConfig();
        config.setStopBits(2);
        assertEquals(2, config.getStopBits());
    }

    @Test
    void shouldRejectInvalidStopBits() {
        SerialConfig config = new SerialConfig();
        assertThrows(IllegalArgumentException.class, () -> config.setStopBits(0));
        assertThrows(IllegalArgumentException.class, () -> config.setStopBits(3));
    }

    @Test
    void shouldSetAndGetParity() {
        SerialConfig config = new SerialConfig();
        config.setParity(SerialConfig.Parity.EVEN);
        assertEquals(SerialConfig.Parity.EVEN, config.getParity());
    }

    @Test
    void shouldSetAndGetFlowControl() {
        SerialConfig config = new SerialConfig();
        config.setFlowControl(SerialConfig.FlowControl.RTS_CTS);
        assertEquals(SerialConfig.FlowControl.RTS_CTS, config.getFlowControl());
    }

    @Test
    void shouldHaveValidSupportedBaudRates() {
        int[] rates = SerialConfig.SUPPORTED_BAUD_RATES;
        assertTrue(rates.length > 0);
        assertTrue(rates[0] >= 300);
        assertTrue(rates[rates.length - 1] <= 921600);
    }

    @Test
    void shouldProduceReadableToString() {
        SerialConfig config = new SerialConfig();
        config.setPortName("COM3");
        config.setBaudRate(115200);
        String str = config.toString();
        assertTrue(str.contains("COM3"));
        assertTrue(str.contains("115200"));
    }
}
