package ir.sahab.dockercomposer;

import org.junit.ClassRule;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class UserIdTest {
    @ClassRule
    public static DockerCompose dockerCompose = DockerCompose.builder()
            .file("/docker-compose/jGroup.yml")
            .afterStart(WaitFor.portOpen("jGroup", 4444))
            .forceRecreate()
            .projectName("jGroup_test")
            .build();

    Service jGroupService = dockerCompose.getServiceByName("jGroup");

    @Test
    public void testServiceByNameWithInjectedUserId() {
        assertThat(jGroupService).isNotNull();
    }
}
