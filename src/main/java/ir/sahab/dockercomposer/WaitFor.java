package ir.sahab.dockercomposer;

import org.apache.commons.lang3.Validate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.Map;

/**
 * Contains helper methods to create startup callbacks which wait for some conditions to become
 * true.
 */
public class WaitFor {

    /**
     * Busy waits for a service. It tries to connect to the given port until timeout reaches.
     */
    public static class PortOpenChecker implements StartupCallback {
        private static Logger logger = LoggerFactory.getLogger(PortOpenChecker.class);
        private final String serviceName;
        private final Integer internalPort;
        private final int timeout;
        private final int retryWaitTime;

        /**
         * @param serviceName the name of service to be checked
         * @param internalPort the port to check. In case of null, the default port will be used
         * @param timeout timeout in milliseconds before check is failed.
         * @param retryWaitTime time to wait in milliseconds between two tries
         */
        public PortOpenChecker(String serviceName, int internalPort, int timeout,
                               int retryWaitTime) {
            Validate.notEmpty(serviceName, "Service name is required");

            this.serviceName = serviceName;
            this.internalPort = internalPort;
            this.timeout = timeout;
            this.retryWaitTime = retryWaitTime;
        }

        @Override
        public void process(Map<String, Service> servicesByName) throws Exception {
            Service service = servicesByName.get(serviceName);
            Validate.notNull(service, "No service with name %s is found", serviceName);

            logger.info("Waiting for opening port {} of {} service (Address={}).", internalPort,
                        serviceName, service.getInternalIp());
            long startTime = System.currentTimeMillis();
            while (System.currentTimeMillis() - startTime <= timeout) {
                try (Socket socket = new Socket()) {
                    socket.connect(new InetSocketAddress(service.getInternalIp(), internalPort),
                            timeout);
                    logger.info("Port {} of {} service opened.", internalPort, serviceName);
                    return; // Successfully connected
                } catch (Exception e) {
                    Thread.sleep(retryWaitTime);
                }
            }
            throw new IOException(String.format("Can not connect to service %s at %s:%d",
                    serviceName, service.getInternalIp(), internalPort));
        }
    }

    /**
     * Waits for the given port of the given service to be available at most 60 seconds. Check
     * will be performed in periods of 100 milliseconds.
     */
    public static StartupCallback portOpen(String serviceName, int internalPort) {
        return new PortOpenChecker(serviceName, internalPort, 60_000, 100);
    }

    /**
     * Waits for the given port of the given service to be available at most timeout milliseconds.
     * Check will be performed in periods of 100 milliseconds.
     */
    public static StartupCallback portOpen(String serviceName, int internalPort, int timeout) {
        return new PortOpenChecker(serviceName, internalPort, timeout, 100);
    }

    /**
     * Waits for the given port of the given service to be available at most timeout milliseconds.
     * Check will be performed in periods of the given retryWaitTime in milliseconds.
     */
    public static StartupCallback portOpen(String serviceName, int internalPort, int timeout,
                                           int retryWaitTime) {
        return new PortOpenChecker(serviceName, internalPort, timeout, retryWaitTime);
    }


}
