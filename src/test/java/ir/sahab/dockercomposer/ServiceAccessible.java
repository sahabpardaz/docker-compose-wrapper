package ir.sahab.dockercomposer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import org.assertj.core.api.Condition;

/**
 * Checks whether a container is accessible from its default port.
 */
public class ServiceAccessible extends Condition<Service> {

    public static final int CONNECT_TIMEOUT_IN_MILLIS = 500;
    private final int port;

    public ServiceAccessible(int port) {
        this.port = port;
    }

    /**
     * @return true if the default port is accessible or false otherwise
     */
    @Override
    public boolean matches(Service container) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(container.getInternalIp(), port), CONNECT_TIMEOUT_IN_MILLIS);
            return true;
        } catch (IOException e) {
            return false;
        }
    }
}