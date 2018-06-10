package ir.sahab.dockercomposer;

import static org.assertj.core.api.Assertions.assertThat;

import org.assertj.core.api.Condition;
import org.junit.ClassRule;
import org.junit.Test;

import java.util.List;

/**
 * This test brings an ensemble consists of three zookeepers. This is done by defining
 * a DockerCompose rule as a field annotated @ClassRule. So in test methods, we expect that the rule
 * is applied and the corresponding service is up and and running.
 */
public class MultiServiceTest {
    private static final int PORT = 2181;
    private static final Condition<Service> accessible = new ServiceAccessible(PORT);

    @ClassRule
    public static DockerCompose dockerCompose = DockerCompose.builder()
            .file("/docker-compose/zookeepers_1.yaml")  // Stage 1
            .afterStart(WaitFor.portOpen("zookeeper-1", PORT))
            .afterStart(WaitFor.portOpen("zookeeper-2", PORT))
            .projectName("docker_compose_test")
            .file("/docker-compose/zookeepers_2.yaml")  // Stage 2
            .afterStart(WaitFor.portOpen("zookeeper-3", PORT))
            .projectName("docker_compose_test")
            .build();

    @Test
    public void testServicesState() throws Exception {
        // Assuming the above rule is correctly applied (started) the services now must be available
        List<Service> services = dockerCompose.getServicesByNamePrefix("zookeeper");
        // Check there are three services
        assertThat(services).hasSize(3);
        // Order is not guaranteed by docker-compose (unless depends_on is used)
        assertThat(services.stream().map(ep -> ep.getName()))
                .containsExactlyInAnyOrder("zookeeper-1", "zookeeper-2", "zookeeper-3");
        assertThat(dockerCompose.getAllServices()).isEqualTo(services);
        // Check all services are available
        for (Service service : services) {
            assertThat(service).is(accessible);
        }
    }

}
