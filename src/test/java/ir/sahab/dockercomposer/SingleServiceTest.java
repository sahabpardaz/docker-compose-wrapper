package ir.sahab.dockercomposer;

import static org.assertj.core.api.Assertions.assertThat;

import org.assertj.core.api.Condition;
import org.junit.ClassRule;
import org.junit.Test;

/**
 * This test brings a single zookeeper instance and start working with it. This is done by defining
 * a DockerCompose rule as a field annotated @ClassRule. So in test methods, we expect that the rule
 * is applied and the corresponding service is up and and running.
 */
public class SingleServiceTest {
    private static final int PORT = 2181;
    private static final Condition<Service> accessible = new ServiceAccessible(PORT);

    @ClassRule
    public static DockerCompose dockerCompose = DockerCompose.builder()
            .file("/docker-compose/zookeeper.yaml")
            .afterStart(WaitFor.portOpen("zookeeper", PORT))
            .projectName("docker_compose_test")
            .build();

    Service zkService = dockerCompose.getServiceByName("zookeeper");

    @Test
    public void testServiceByName() {
        assertThat(zkService).isNotNull();
        assertThat(dockerCompose.getServiceByName("")).isNull();
        assertThat(dockerCompose.getServiceByName("zk")).isNull();
    }

    @Test
    public void testAllServices() {
        assertThat(dockerCompose.getAllServices()).containsExactly(zkService);
    }

    @Test
    public void testServiceByPrefix() {
        assertThat(dockerCompose.getServicesByNamePrefix("zoo")).containsExactly(zkService);
        assertThat(dockerCompose.getServicesByNamePrefix("")).containsExactly(zkService);
        assertThat(dockerCompose.getServicesByNamePrefix("zk")).isEmpty();
    }

    @Test(timeout = 10000)
    public void testServiceState() throws Exception {
        Service zkService = dockerCompose.getServiceByName("zookeeper");
        // Check service has correct state
        assertThat(zkService.getName()).isEqualTo("zookeeper");
        assertThat(zkService.getExternalIp()).isEqualTo("127.0.0.1");

        assertThat(zkService).is(accessible);
        // Test start/stop
        zkService.stop();
        assertThat(zkService).isNot(accessible);
        // Test stop again
        zkService.stop();
        zkService.start();

        // Wait for service to be accessible
        while (!accessible.matches(zkService)) {
            Thread.sleep(200);
        }
        // Test start again
        zkService.start();
    }

}
