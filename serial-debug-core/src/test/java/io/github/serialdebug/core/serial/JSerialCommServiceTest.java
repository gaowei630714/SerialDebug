package io.github.serialdebug.core.serial;

import com.fazecast.jSerialComm.SerialPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JSerialCommServiceTest {

    @Test
    void shouldListPorts() {
        SerialPort mockPort = mock(SerialPort.class);
        when(mockPort.getSystemPortName()).thenReturn("COM3");
        when(mockPort.getDescriptivePortName()).thenReturn("USB Serial Port");

        try (MockedStatic<SerialPort> mocked = mockStatic(SerialPort.class)) {
            mocked.when(SerialPort::getCommPorts).thenReturn(new SerialPort[]{mockPort});

            JSerialCommService service = new JSerialCommService();
            var ports = service.listPorts();

            assertEquals(1, ports.size());
            assertEquals("COM3", ports.get(0).getSystemPortName());
            assertEquals("USB Serial Port", ports.get(0).getDescription());
        }
    }

    @Test
    void shouldOpenPortSuccessfully() throws Exception {
        SerialPort mockPort = mock(SerialPort.class);
        when(mockPort.openPort()).thenReturn(true);
        when(mockPort.isOpen()).thenReturn(true);

        try (MockedStatic<SerialPort> mocked = mockStatic(SerialPort.class)) {
            mocked.when(() -> SerialPort.getCommPort(anyString())).thenReturn(mockPort);

            JSerialCommService service = new JSerialCommService();
            SerialConfig config = new SerialConfig();
            config.setPortName("COM3");

            service.open(config);
            assertTrue(service.isOpen());
        }
    }

    @Test
    void shouldThrowOnOpenFailure() {
        SerialPort mockPort = mock(SerialPort.class);
        when(mockPort.openPort()).thenReturn(false);

        try (MockedStatic<SerialPort> mocked = mockStatic(SerialPort.class)) {
            mocked.when(() -> SerialPort.getCommPort(anyString())).thenReturn(mockPort);

            JSerialCommService service = new JSerialCommService();
            SerialConfig config = new SerialConfig();
            config.setPortName("COM3");

            assertThrows(IOException.class, () -> service.open(config));
            assertFalse(service.isOpen());
        }
    }

    @Test
    void shouldThrowOnOpenWhenAlreadyOpen() throws Exception {
        SerialPort mockPort = mock(SerialPort.class);
        when(mockPort.openPort()).thenReturn(true);
        when(mockPort.isOpen()).thenReturn(true);

        try (MockedStatic<SerialPort> mocked = mockStatic(SerialPort.class)) {
            mocked.when(() -> SerialPort.getCommPort(anyString())).thenReturn(mockPort);

            JSerialCommService service = new JSerialCommService();
            SerialConfig config = new SerialConfig();
            config.setPortName("COM3");
            service.open(config);

            assertThrows(IOException.class, () -> service.open(config));
        }
    }

    @Test
    void shouldSendData() throws Exception {
        SerialPort mockPort = mock(SerialPort.class);
        when(mockPort.openPort()).thenReturn(true);
        when(mockPort.isOpen()).thenReturn(true);
        when(mockPort.writeBytes(any(), anyInt())).thenAnswer(inv -> inv.getArgument(1));

        try (MockedStatic<SerialPort> mocked = mockStatic(SerialPort.class)) {
            mocked.when(() -> SerialPort.getCommPort(anyString())).thenReturn(mockPort);

            JSerialCommService service = new JSerialCommService();
            SerialConfig config = new SerialConfig();
            config.setPortName("COM3");
            service.open(config);

            byte[] data = "hello".getBytes();
            service.sendData(data);

            verify(mockPort).writeBytes(data, data.length);
        }
    }

    @Test
    void shouldThrowOnSendWhenNotOpen() {
        JSerialCommService service = new JSerialCommService();
        assertThrows(IOException.class, () -> service.sendData(new byte[]{0x01}));
    }

    @Test
    void shouldClosePort() throws Exception {
        SerialPort mockPort = mock(SerialPort.class);
        when(mockPort.openPort()).thenReturn(true);
        when(mockPort.isOpen()).thenReturn(true);

        try (MockedStatic<SerialPort> mocked = mockStatic(SerialPort.class)) {
            mocked.when(() -> SerialPort.getCommPort(anyString())).thenReturn(mockPort);

            JSerialCommService service = new JSerialCommService();
            SerialConfig config = new SerialConfig();
            config.setPortName("COM3");
            service.open(config);
            assertTrue(service.isOpen());

            service.close();
            assertFalse(service.isOpen());
            verify(mockPort).removeDataListener();
            verify(mockPort).closePort();
        }
    }
}
