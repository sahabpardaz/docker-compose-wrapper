package ir.sahab.dockercomposer;

import static java.util.Collections.singletonList;

import com.google.common.net.InetAddresses;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;

/**
 * This class provides a utility to add (Hostname,IP) mappings to existing java DNS resolution mechanism.
 */
public class NameServiceEditor {

    private NameServiceEditor() {
    }

    /**
     * Modifies Java's name-service resolution by invoking "nameServices" or "nameService" based on Java version (the
     * private static field in class {@link InetAddress} and adding a custom NameService of type {@link
     * FixedHostNameService} to that. The new NameService will hold mapping of the given hostname and IP. We create a
     * dynamic proxy of {@link sun.net.spi.nameservice.NameService} instead of defining a class implementing this
     * interface to avoid compiler warning for using internal proprietary API. It's not a good workaround but it is the
     * simplest solution we found at the moment.
     *
     * @param hostname Hostname of the given mapping.
     * @param ip IP String of the given mapping.
     */
    public static void addHostnameToNameService(String hostname, String ip)
            throws IllegalAccessException, IOException, ClassNotFoundException, NoSuchFieldException {
        String nameServiceInterface;
        Object nameServiceProxy;
        String nameServiceFieldName;
        Field nameServiceField;
        try {
            // Newer versions of Java(> 1.8) uses java.net.InetAddress$nameService field.
            nameServiceInterface = "java.net.InetAddress$NameService";
            nameServiceFieldName = "nameService";
            final Class<?> nameService = Class.forName(nameServiceInterface);
            nameServiceField = InetAddress.class.getDeclaredField(nameServiceFieldName);
            nameServiceProxy = Proxy.newProxyInstance(nameService.getClassLoader(), new Class<?>[] {nameService},
                    new FixedHostNameService(hostname, ip));
        } catch (final ClassNotFoundException | NoSuchFieldException e) {
            // Old versions of Java (<= 1.8) uses java.net.InetAddress$nameServices field.
            nameServiceInterface = "sun.net.spi.nameservice.NameService";
            nameServiceFieldName = "nameServices";
            nameServiceField = InetAddress.class.getDeclaredField(nameServiceFieldName);
            final Class<?> nameService = Class.forName(nameServiceInterface);
            nameServiceProxy = singletonList(Proxy.newProxyInstance(nameService.getClassLoader(),
                    new Class<?>[] {nameService}, new FixedHostNameService(hostname, ip)));
        }
        nameServiceField.setAccessible(true);
        nameServiceField.set(InetAddress.class, nameServiceProxy);
    }

    /**
     * This class defines an {@link InvocationHandler} for implementing {@link sun.net.spi.nameservice.NameService}
     * interface.
     */
    public static class FixedHostNameService implements InvocationHandler {

        private final InetAddress address;

        FixedHostNameService(String host, String ip) throws UnknownHostException {
            final InetAddress ipAddress = InetAddresses.forString(ip);
            this.address = InetAddress.getByAddress(host, ipAddress.getAddress());
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
                return new InetAddress[] {address};
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