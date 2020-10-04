package ir.sahab.dockercomposer;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Map;
import org.assertj.core.api.Condition;
import org.junit.Assert;
import org.junit.Test;
import org.zeroturnaround.exec.ProcessExecutor;

public class DockerComposeRunnerTest {

    private static final int PORT = 2181;
    private static final Condition<Service> accessible = new ServiceAccessible(PORT);

    @Test
    public void testEnvironment() throws Exception {
        Path file = Paths
                .get(getClass().getResource("/docker-compose/alpine-with-env.yaml").getFile());

        String version = "3.12.0";
        DockerComposeRunner runner = null;
        try {
            runner = new DockerComposeRunner(file, null, Collections.singletonMap("ALPINE_VERSION", version));
            Map<String, Service> services = runner.start(true);
            String image = getContainerInfo(services.get("alpine-with-env"), "Config.Image");
            assertThat(image).isEqualTo("alpine:" + version);
        } finally {
            if (runner != null) {
                runner.down();
            }
        }

    }

    @Test
    public void testServiceWithInjectedUserId() throws Exception {
        DockerComposeRunner runner = null;
        try {
            // Start the zookeeper service with injected uid
            Path file = Paths
                    .get(getClass().getResource("/docker-compose/j-group-with-uid.yaml").getFile());
            runner = new DockerComposeRunner(file);
            Map<String, Service> services = runner.start(true);
            Service jGroupService = services.get("jgroup-with-uid");

            WaitFor.portOpen("jgroup-with-uid", PORT).process(services);
            assertThat(jGroupService).isNotNull();
            String containerId = jGroupService.getId();
            // Get user id in machine

            ProcessExecutor executor = new ProcessExecutor("id", "-u");
            String expectedUserId = executor.readOutput(true).execute().outputString().trim();
            // Get user id inside the container

            executor = new ProcessExecutor("docker", "exec", containerId, "id", "-u");
            String containerUserId = executor.readOutput(true).execute().outputString().trim();
            Assert.assertEquals(expectedUserId, containerUserId);
        } finally {
            if(runner != null) {
                runner.down();
            }
        }
    }

    @Test
    public void testCreate() throws Exception {
        // Start the zookeeper service
        Path file = Paths.get(getClass().getResource("/docker-compose/zookeeper.yaml").getFile());
        DockerComposeRunner runner = new DockerComposeRunner(file);
        Map<String, Service> services = runner.start(false);
        Service zkService = services.get("zookeeper");
        // Keep container current id since it is only changed if the container recreated

        WaitFor.portOpen("zookeeper", PORT).process(services);
        assertThat(zkService).is(accessible);

        String currentId = getContainerInfo(zkService, "Id");
        // Run the zookeeper file again, first without force start and check whether the
        // currentId is changed or not
        zkService = runner.start(false).get("zookeeper");
        assertThat(currentId).isEqualTo(getContainerInfo(zkService, "Id"));

        // Now run with forceRecreate, the id must be changed this time but
        // the zkService must remain accessible
        zkService = runner.start(true).get("zookeeper");
        String newId = getContainerInfo(zkService, "Id");
        assertThat(currentId).isNotEqualTo(newId);

        // Now check for different project name
        runner = new DockerComposeRunner(file, "prj", Collections.emptyMap());
        zkService = runner.start(false).get("zookeeper");
        assertThat(currentId).isNotEqualTo(getContainerInfo(zkService, "Id"));

        // Test down
        try {
            runner.down();
        } catch (Exception e) {
            // Not important (container is killed but network is not guaranteed to be down
        }
        assertThat(zkService).isNot(accessible);
    }

    private String getContainerInfo(Service container, String name) throws Exception {
        return new ProcessExecutor()
                .command("docker", "inspect",  String.format("--format={{.%s}}", name), container.getId())
                .readOutput(true)
                .execute()
                .outputString()
                .trim();

    }

}