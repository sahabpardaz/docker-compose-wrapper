package ir.sahab.dockercomposer;

import com.google.common.net.InetAddresses;
import org.apache.commons.lang3.reflect.FieldUtils;

import java.io.IOException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeoutException;

/**
 * This class provides a utility to add (Hostname,IP) mappings to existing java DNS resolution
 * mechanism.
 */
public class NameServiceEditor {

    private static final String NAME_SERVICE_INTERFACE = "sun.net.spi.nameservice.NameService";

    /**
     * Modified Java's name-service resolution by invoking "nameServices", (the private static field
     * in class {@link InetAddress} and adding a custom NameService of type
     * {@link FixedHostNameService} to that. The new NameService will hold mapping of the given
     * hostname and IP. By adding this NameService to first position (position 0) of "nameServices"
     * list, the new mapping will be preferred to other existing mappings with same hostname. We
     * create a dynamic proxy of {@link sun.net.spi.nameservice.NameService} instead of defining a
     * class implementing this interface to avoid compiler warning for using internal proprietary
     * API. It's not a good workaround but it is the simplest solution we found at the moment.
     *
     * @param hostname Hostname of the given mapping.
     * @param ip IP String of the given mapping.
     */
    public static void addHostnameToNameService(String hostname, String ip)
            throws IllegalAccessException, InterruptedException, IOException, TimeoutException,
                   ClassNotFoundException {

        @SuppressWarnings("unchecked")
        List<Object> nameServices =
                (List<Object>) FieldUtils.readStaticField(InetAddress.class, "nameServices", true);

        Class<?> nameServiceClass = Class.forName(NAME_SERVICE_INTERFACE);
        Object nameService = Proxy.newProxyInstance(
                Thread.currentThread().getContextClassLoader(),
                new Class<?>[] {nameServiceClass}, new FixedHostNameService(hostname, ip));
        // Note: adding to index zero makes this NameService's priority higher than existing ones.
        nameServices.add(0, nameService);
    }

    /**
     * This class defines an {@link InvocationHandler} for implementing
     * {@link sun.net.spi.nameservice.NameService} interface.
     */
    public static class FixedHostNameService implements InvocationHandler {

        private final InetAddress address;

        FixedHostNameService(String host, String ip) throws UnknownHostException {
            final InetAddress address = InetAddresses.forString(ip);
            this.address = InetAddress.getByAddress(host, address.getAddress());
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            switch (method.getName()) {
                case "lookupAllHostAddr":
                    return lookupAllHostAddr((String) args[0]);
                case "getHostByAddr":
                    return getHostByAddr((byte[]) args[0]);
                default:
                    throw new UnsupportedOperationException(
                            "Method " + method + " is not supported");
            }
        }

        /**
         * Implementation for {@link sun.net.spi.nameservice.NameService#lookupAllHostAddr(String)}
         */
        private InetAddress[] lookupAllHostAddr(String paramString) throws UnknownHostException {
            if (address.getHostName().equals(paramString)) {
                return new InetAddress[] { address };
            } else {
                throw new UnknownHostException();
            }
        }

        /**
         * Implementation for {@link sun.net.spi.nameservice.NameService#getHostByAddr(byte[])}
         */
        private String getHostByAddr(byte[] paramArrayOfByte) throws UnknownHostException {
            if (Arrays.equals(address.getAddress(), paramArrayOfByte)) {
                return address.getHostName();
            }
            throw new UnknownHostException();
        }
    }
}

