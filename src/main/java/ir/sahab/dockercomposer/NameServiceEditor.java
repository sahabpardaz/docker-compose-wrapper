package ir.sahab.dockercomposer;

import com.google.common.net.InetAddresses;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.List;
import org.apache.commons.lang3.reflect.FieldUtils;

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
            // Old versions of Java (<= 1.8) uses java.net.InetAddress$nameServices field.
            nameServiceInterface = "sun.net.spi.nameservice.NameService";
            nameServiceFieldName = "nameServices";
            final Class<?> nameService = Class.forName(nameServiceInterface);
            @SuppressWarnings("unchecked")
            List<Object> nameServices =
                    (List<Object>) FieldUtils.readStaticField(InetAddress.class, nameServiceFieldName, true);
            nameServiceProxy = Proxy.newProxyInstance(nameService.getClassLoader(),
                    new Class<?>[] {nameService}, new FixedHostNameService(hostname, ip));
            // Note: adding to index zero makes this NameService's priority higher than existing ones.
            nameServices.add(0, nameServiceProxy);
        } catch (final ClassNotFoundException e) {
            // Newer versions of Java(> 1.8) uses java.net.InetAddress$nameService field.
            nameServiceInterface = "java.net.InetAddress$NameService";
            nameServiceFieldName = "nameService";
            final Class<?> nameService = Class.forName(nameServiceInterface);
            nameServiceField = InetAddress.class.getDeclaredField(nameServiceFieldName);
            Object parentNameService = FieldUtils.readStaticField(InetAddress.class, nameServiceFieldName, true);
            nameServiceProxy = Proxy.newProxyInstance(nameService.getClassLoader(), new Class<?>[] {nameService},
                    new FixedHostNameService(hostname, ip, parentNameService));
            nameServiceField.setAccessible(true);
            nameServiceField.set(InetAddress.class, nameServiceProxy);
        }
    }

    /**
     * This class defines an {@link InvocationHandler} for implementing {@link sun.net.spi.nameservice.NameService}
     * interface.
     */
    public static class FixedHostNameService implements InvocationHandler {

        private final InetAddress address;
        private final Object parentNameService;

        FixedHostNameService(String host, String ip) throws UnknownHostException {
            final InetAddress ipAddress = InetAddresses.forString(ip);
            this.address = InetAddress.getByAddress(host, ipAddress.getAddress());
            this.parentNameService = null;
        }

        FixedHostNameService(String host, String ip, Object proxyNameService) throws UnknownHostException {
            final InetAddress ipAddress = InetAddresses.forString(ip);
            this.address = InetAddress.getByAddress(host, ipAddress.getAddress());
            this.parentNameService = proxyNameService;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            try {
                switch (method.getName()) {
                    case "lookupAllHostAddr":
                        return lookupAllHostAddr((String) args[0]);
                    case "getHostByAddr":
                        return getHostByAddr((byte[]) args[0]);
                    default:
                        throw new UnsupportedOperationException("Method " + method + " is not supported");
                }
            } catch (UnknownHostException ex) {
                if (parentNameService != null) {
                    Method calledMethod = parentNameService.getClass().getDeclaredMethod(method.getName());
                    return calledMethod.invoke(parentNameService, args);
                }
                throw ex;
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