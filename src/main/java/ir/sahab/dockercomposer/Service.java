package ir.sahab.dockercomposer;

import java.util.Map;
import org.apache.commons.lang3.Validate;
import org.apache.commons.lang3.builder.ToStringBuilder;

/**
 * Represents a docker-compose service which actually corresponds to a docker container (running or dead).
 * <p>Generally, a docker container can be accessed from an IP address with some published ports. Since some containers
 * might use port-forwarding this class holds a mapping between internal ports to external ports but depending on your
 * docker networking you might not need such mappings. Since most containers only publish one port, the first point
 * designated as the default port of this container. On the other hand, each container will have an IP in the internal
 * network of docker, which is accessible from the host machine. So the internal IP of the container is also set in this
 * class, which enables the user to access all internal ports of the container. Consequently, <b>if the user access
 * internal IP, port mapping will not be necessary anymore</b>.</p>
 */
public class Service {

    private final String id;  // Container id
    private final String name;  // Service name
    private String externalIp;
    private String internalIp;
    private Map<Integer, Integer> portMappings;
    // Docker-compose runner that is responsible for running this service
    private final DockerComposeRunner runner;

    /**
     * @param id container id
     * @param name service name in docker-compose file
     * @param internalIp Ip of the container inside the docker network.
     * @param externalIp Ip of the existing machine.
     * @param portMappings mappings from internal ports to external ports, can be null.
     */
    Service(String id, String name, String externalIp, String internalIp,
            Map<Integer, Integer> portMappings, DockerComposeRunner runner) {
        Validate.notEmpty(id, "Id can not be empty");
        Validate.notEmpty(name, "Name can not be empty");
        Validate.notEmpty(externalIp, "External Ip can not be empty");
        Validate.notEmpty(internalIp, "Internal Ip can not be empty");
        Validate.notNull(runner, "Runner is required");

        this.id = id;
        this.name = name;
        this.externalIp = externalIp;
        this.internalIp = internalIp;
        this.portMappings = portMappings;
        this.runner = runner;
    }

    /**
     * Starts this service by calling docker-compose start. Starting an already started service has no effect.
     */
    public void start() {
        Service newService = runner.startService(name);
        copyFrom(newService);
    }

    /**
     * Stops this service by calling docker-compose stop. Stopping an already stopped service has no effect.
     */
    public void stop() {
        runner.stopService(name);
    }

    /**
     * Returns this service container id.
     */
    public String getId() {
        return id;
    }

    /**
     * Returns the docker-compose service name.
     */
    public String getName() {
        return name;
    }

    /**
     * Returns the docker externalIp
     */
    public String getExternalIp() {
        return this.externalIp;
    }

    /**
     * Returns the docker internalIP
     */
    public String getInternalIp() {
        return this.internalIp;
    }

    /**
     * Returns the external mapping of the given internal port. If no such mapping is found, the internal port is
     * returned since in some docker networks internal ports are actually reachable.
     */
    public int getPort(int internalPort) {
        return portMappings == null ? internalPort : portMappings.getOrDefault(internalPort, internalPort);
    }

    // Used for changing this service state during its restart
    private void copyFrom(Service other) {
        this.externalIp = other.externalIp;
        this.internalIp = other.internalIp;
        this.portMappings = other.portMappings;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this)
                .append("id", id)
                .append("name", name)
                .append("externalIp", externalIp)
                .append("internalIp", internalIp)
                .append("portMappings", portMappings)
                .append("runner", runner)
                .toString();
    }
}