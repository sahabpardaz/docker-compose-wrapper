package ir.sahab.dockercomposer;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import org.junit.Assert;
import org.junit.ClassRule;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NameServiceEditorTest {

    private static final Logger logger = LoggerFactory.getLogger(NameServiceEditorTest.class);

    @ClassRule
    public static DockerCompose dockerCompose = DockerCompose
            .builder()
            .file("/docker-compose/alpine.yml")
            .forceDown()
            .forceRecreate()
            .build();

    @Test
    public void testHostnameAddAndRemove() throws IOException {
        Service service = dockerCompose.getServiceByName("alpine");
        String ip = service.getInternalIp();
        String hostname = service.getName();

        Assert.assertTrue(isHostUp(ip));

        Assert.assertTrue(isMappedCorrectly(hostname, ip));

        service.stop();
        Assert.assertFalse(isHostUp(ip));

    }

    public static boolean isMappedCorrectly(String hostname, String ip) {
        InetAddress inetAddress;
        try {
            inetAddress = InetAddress.getByName(hostname);
            if (inetAddress.getHostAddress() != null) {
                return inetAddress.getHostAddress().equalsIgnoreCase(ip);
            } else {
                return false;
            }
        } catch (UnknownHostException e) {
            logger.info("unknown host name: {}", hostname, e);
            return false;
        }
    }

    private static boolean isHostUp(String host) throws IOException {
        return InetAddress.getByName(host).isReachable(100);
    }
}