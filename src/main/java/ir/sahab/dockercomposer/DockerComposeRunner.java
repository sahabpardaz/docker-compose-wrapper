package ir.sahab.dockercomposer;


import java.util.Collections;
import org.apache.commons.lang3.Validate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zeroturnaround.exec.InvalidExitValueException;
import org.zeroturnaround.exec.ProcessExecutor;
import org.zeroturnaround.exec.ProcessResult;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * A utility class for calling docker-compose utility commands on a specif compose file.
 * This class heavily uses CLI to call docker-compose and docker utilities by simply
 * calling their names so the environment must be properly setup so that this class can directly
 * work with these utilities.
 */
public class DockerComposeRunner {
    private static final Logger logger = LoggerFactory.getLogger(DockerComposeRunner.class);

    /**
     * Checks environment for the existence of docker-compose and docker utilities and
     * throws exception if it can not use them.
     */
    static {
        try {
            // Test docker utility to be accessible without sudo
            execute("docker", "info");
            // Test docker-compose utility to be accessible
            execute("docker-compose", "--version");
        } catch (Exception e) {
            throw new IllegalStateException("It seems your environment is not properly setup. "
                                            + "Check docker info and docker-compose --version can "
                                            + "be run properly.", e);
        }
    }

    private final Path composeFile;
    private final String projectName;
    private final Map<String, String> environment;

    /**
     * Creates a runner for the given file with the default project name
     */
    public DockerComposeRunner(Path composeFile) {
        this(composeFile, null, Collections.emptyMap());
    }

    /**
     * Creates a runner for the given file and with the given project name
     * @param composeFile the path to the compose file, required.
     * @param projectName the project name, optional.
     * @param environment environment variables to pass to docker-compose process.
     */
    public DockerComposeRunner(Path composeFile, String projectName, Map<String, String> environment) {
        Validate.notNull(composeFile);
        Validate.notNull(environment);

        this.composeFile = composeFile;
        this.projectName = projectName;
        this.environment = environment;
    }

    /**
     * Starts the services and returns a map from the name of these services to their actual object.
     */
    public Map<String, Service> start(boolean forceRecreate) {
        Map<String, Service> servicesByName = new HashMap<>();
        // Run the docker-compose files and get the list of its defined services
        List<String> serviceNames = up(forceRecreate);

        for (String serviceName : serviceNames) {
            // Create the service
            Service service = createService(serviceName);
            servicesByName.put(serviceName, service);
        }

        return servicesByName;
    }

    /**
     * Executes the docker-compose up command and returns the list of services that get run.
     *
     * @return List of services get run, or empty list in case of no service.
     */
    public List<String> up(boolean forceRecreate) {
        executeDockerCompose("up", "-d", forceRecreate ? "--force-recreate" : "--no-recreate");

        return executeDockerCompose("config", "--services").getOutput().getLines();
    }

    /**
     * Execute docker-compose down.
     */
    public void down() {
        executeDockerCompose("down");
    }

    /**
     * Starts the given service using docker-compose start command.
     *
     * @return A new service instance
     */
    public Service startService(String serviceName) {
        executeDockerCompose("start", serviceName);
        // Port mappings (and possibly other conditions) might be changed
        return createService(serviceName);
    }

    /**
     * Stops the given service using docker-compose stop command.
     */
    public void stopService(String serviceName) {
        executeDockerCompose("stop", serviceName);
    }

    private Service createService(String name) {
        List<String> serviceStateLines = executeDockerCompose("ps", name).getOutput().getLines();

        if (serviceStateLines.size() != 3) {
            throw new IllegalStateException("Unexpected output of docker-compose ps for service "
                                            + name + ". This is mostly due to your container is "
                                            + "terminated early.");
        }

        // The third line is the state of docker service
        String serviceState = serviceStateLines.get(2);
        return  parseServiceState(serviceState, name);
    }

    /**
     * Parses the docker-compose service state line and creates a service from the parsed result.
     */
    // We might later convert this to an strategy based on docker-compose version
    private Service parseServiceState(String serviceState, String name) {
        /* Docker-compose output is like this:
         * zookeeper-test-1     ...   2888/tcp, 3888/tcp, 0.0.0.0:32768->2181/tcp
         * We want to extract these: zookeeper-test-1 as id and 2181 as the default port which
         * is mapped to port 32768. The service externalIp is 0.0.0.0 which means local host.
         */
        final Pattern portPattern
                = Pattern.compile("((\\d+).(\\d+).(\\d+).(\\d+)):(\\d+)->(\\d+)/tcp");
        final int ipAddress = 1;
        final int externalPort = 6;
        final int internalPort = 7;
        final String noIpAddress = "0.0.0.0";
        final String localhost = "127.0.0.1";

        int firstSpace = serviceState.indexOf(' ');
        String id = serviceState.substring(0, firstSpace);

        Matcher matcher = portPattern.matcher(serviceState);
        String externalIp = localhost;
        Map<Integer, Integer> portMappings = new HashMap<>();
        /* Each match will be a mapping from some internal port to some external port.
         * The first published port will be considered as the default port.
         */
        while (matcher.find()) {
            int exPort = Integer.parseInt(matcher.group(externalPort));
            int inPort = Integer.parseInt(matcher.group(internalPort));
            portMappings.put(inPort, exPort);
        }
        String internalIp = null;
        try {
            internalIp = getContainerIp(id);
            NameServiceEditor.addHostnameToNameService(name, internalIp);
        } catch (Exception e) {
            throw new IllegalStateException("Could not set mapping of service name and internal IP"
                    , e);
        }

        return new Service(id, name, externalIp, internalIp, portMappings, this);
    }

    /**
     * Executes docker-compose on the given file with additional command line arguments. The
     * directory where the given file resides added as an environment variable named DIRECTORY.
     */
    public ProcessResult executeDockerCompose(String...commands) {
        // Attach docker-compose -f ... to the up of commands
        List<String> enhancedCmds = new ArrayList<>();
        enhancedCmds.add("docker-compose");
        // We can also add multiple files and use the override mechanism of docker-compose.
        enhancedCmds.add("-f");
        enhancedCmds.add(composeFile.toString());
        if (projectName != null) {
            enhancedCmds.add("-p");
            enhancedCmds.add(projectName);
        }
        //Get current user id from linux host
        String uid = execute("id", "-u").outputString().trim();
        enhancedCmds.addAll(Arrays.asList(commands));
        ProcessExecutor executor =
            new ProcessExecutor()
                .environment(environment)
                .environment("DIRECTORY", composeFile.getParent().toString())
                .environment("CURRENT_USER_ID", uid);
        return execute(executor, enhancedCmds);
    }

    /**
     * Executes the given commands and and ensures that the process exited with 0.
     */
    private static ProcessResult execute(String... commands) {
        return execute(new ProcessExecutor(), Arrays.asList(commands));
    }

    /**
     * Executes the given commands using the given executor and ensures that the process exited
     * with 0.
     *
     * @throws InvalidExitValueException when the commands exit with non-zero value
     * @throws IllegalStateException if the commands is not executed properly or being interrupted
     *                               in the mean time.
     */
    private static ProcessResult execute(ProcessExecutor executor, List<String> commands) {
        String command = commands.stream().collect(Collectors.joining(" "));
        ProcessResult result;
        try {
            result = executor
                    .command(commands)
                    .readOutput(true)
                    .exitValue(0)
                    .execute();
        } catch (InvalidExitValueException e) {
            throw e;
        } catch (Exception e) {
            /* This is not a retriable case since mostly is due to os/security/disk problems.
               Also we treat the InterruptedException the same since we don't file in a
               multi-threaded environment */
            throw new IllegalStateException("Fundamental error during running command " + command,
                                            e);
        }

        // Log and validate the result of running
        logger.debug("Command {} resulted to {}.", command, result.outputString());

        return result;
    }

    /**
     * Gets container IP using "{@code docker inspect}" command and container name.
     * @return The IP can be found in the json output of "docker inspect" command expected to
     *         be in node {{range .NetworkSettings.Networks}}{{.IPAddress}}{{end}}
     */
    private String getContainerIp(String containerId) {
        ProcessResult result = execute("docker", "inspect", "-f",
                                       "{{range .NetworkSettings.Networks}}{{.IPAddress}}{{end}}",
                                       containerId);
        return result.outputString().trim();
    }
}
