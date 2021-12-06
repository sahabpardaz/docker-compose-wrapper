package ir.sahab.dockercomposer;

import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.commons.lang3.Validate;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.junit.rules.ExternalResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A junit rule to start a set of services using docker-compose utility. You must build an instance of this rule using
 * its {@link #builder()} method and give it the description of your desired services (your target environment) using
 * docker-compose files. You can access these services by their names using {@link #getServiceByName(String)} method.
 */
public class DockerCompose extends ExternalResource {

    private static final Logger logger = LoggerFactory.getLogger(DockerCompose.class);

    private final List<DockerComposeSpec> specs;
    private final Map<String, Service> servicesByName;

    // To better support backward compatibility we force all to only use builder to build this class.
    private DockerCompose(List<DockerComposeSpec> specs) {
        this.specs = specs;
        // Create runner for each spec
        specs.forEach(spec -> spec.runner = new DockerComposeRunner(spec.file, spec.projectName, spec.environment));
        this.servicesByName = new HashMap<>();
    }

    @Override
    protected void before() throws Throwable {
        DockerComposeRunner.checkEnvironment();
        // Run the specs one-by-one and collect the services from each spec.
        for (DockerComposeSpec spec : specs) {
            logger.info("Starting docker-compose spec: {}", spec);
            Map<String, Service> services = spec.runner.start(spec.forceRecreate);
            logger.info("Services information for spec file {} is: {}", spec.file, services);
            // Two services with the same name can exist in these files but docker-compose only run one of them so we
            // give a light warning here.
            services.keySet().stream()
                    .filter(servicesByName::containsKey)
                    .forEach(name -> logger.warn("Multiple services with the same name '{}' is detected.", name));
            // Wait for callbacks to be run completely
            for (StartupCallback callback : spec.startupCallbacks) {
                callback.process(services);
            }
            servicesByName.putAll(services);
        }
    }

    /**
     * Removes the services and networks related to the docker compose spec only if the spec asks for force stop. This
     * is a best-effort operation without any guarantees about the result. This is mostly due to docker-compose
     * behaviour since if the network is used by other services it can not down properly.
     */
    @Override
    protected void after() {
        for (DockerComposeSpec spec : specs) {
            logger.info("Stopping docker-compose spec: {}", spec);
            if (spec.forceDown) {
                try {
                    spec.runner.down();
                    logger.info("Docker-compose spec for file {} stopped!", spec.file.getFileName());
                } catch (Exception e) {
                    // We don't care
                    logger.warn("Unable to stop docker-compose spec: {}", spec);
                }
            }
        }
    }

    /**
     * Returns a service by its name.
     *
     * @param name The name of your logical service.
     * @return An externally managed service or null if no service with such name exists.
     */
    public Service getServiceByName(String name) {
        return servicesByName.get(name);
    }

    /**
     * Returns an unmodifiable list of all services currently managed by this rule.
     */
    public List<Service> getAllServices() {
        return Collections.unmodifiableList(new ArrayList<>(servicesByName.values()));
    }

    /**
     * Returns all services whose name begins with the given prefix.
     *
     * @param prefix a not-null prefix
     * @return List of services with the given prefix is the start of their names or and empty list if no service with
     * such condition is found.
     */
    public List<Service> getServicesByNamePrefix(String prefix) {
        Validate.notNull(prefix, "Prefix can not be null");

        // Note: service names are not-null
        return servicesByName.values().stream()
                .filter(s -> s.getName().startsWith(prefix))
                .collect(Collectors.toList());
    }

    /**
     * A specification for running a set of services in a single docker-compose file using docker-compose utility.
     */
    private static class DockerComposeSpec {

        Path file;
        String projectName;
        boolean forceRecreate = false;
        boolean forceDown = false;
        List<StartupCallback> startupCallbacks = new ArrayList<>();
        DockerComposeRunner runner; //  Runner of this spec
        Map<String, String> environment = new HashMap<>();

        @Override
        public String toString() {
            return new ToStringBuilder(this)
                    .append("file", file)
                    .append("projectName", projectName)
                    .append("forceRecreate", forceRecreate)
                    .append("forceDown", forceDown)
                    .append("startupCallbacks", startupCallbacks)
                    .append("runner", runner)
                    .append("environment", environment)
                    .toString();
        }
    }

    /**
     * Returns a builder to build a description of target environment using docker-compose files. The build process
     * consists of several stages. Each stage consists of a docker-compose file plus several options. All services in
     * each stage run at once with a call of docker-compose up command. Stages will be run one-by-one. Each stage begins
     * by calling file method and ends with another call to the file method. Calling build method ends the build
     * process.
     */
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private final List<DockerComposeSpec> specs = new ArrayList<>();

        /**
         * Specifies a docker-compose file located in the given resourcePath on your local machine.
         *
         * @param resourcePath a resource path which is resolvable by this class getResource method
         */
        public BuilderStage file(String resourcePath) {
            Validate.notEmpty(resourcePath, "Resource path must not be empty");

            Path filePath = getResourceFilePath(resourcePath);
            return new BuilderStage(this, filePath);
        }

        private Path getResourceFilePath(String resourcePath) {
            URL composeFileUrl = getClass().getResource(resourcePath);
            Validate.notNull(composeFileUrl, "Resource not fount at path " + resourcePath);
            Validate.isTrue("file".equals(composeFileUrl.getProtocol()),
                    "Only exploded archives are accepted but the given %s is accessible"
                            + " with %s protocol.", resourcePath, composeFileUrl.getProtocol());

            return Paths.get(composeFileUrl.getFile());
        }

        DockerCompose build() {
            return new DockerCompose(specs);
        }
    }

    /**
     * Represents one stage in the build process of docker-compose rule.
     */
    public static class BuilderStage {

        private final DockerComposeSpec spec;
        private final Builder builder;

        BuilderStage(Builder builder, Path filePath) {
            spec = new DockerComposeSpec();
            spec.file = filePath;
            this.builder = builder;
            this.builder.specs.add(spec);
        }

        /**
         * Forces the services to be recreated even if they are already running, uses --force-recreate parameter of
         * docker-compose. Without this option (which is the default case), If any container with same service name and
         * project name exists, the container will not be recreated and existing one will be reused. So generally,
         * without this option, you must write your test with extra care. In spite of this, without this option, you pay
         * less time starting up your environment each time.
         * <p>Note that this option uses previous existing volumes of this docker image. So, state of previous
         * container may be present in the new one by default. Users will need to renew the state manually.</p>
         */
        public BuilderStage forceRecreate() {
            spec.forceRecreate = true;
            return this;
        }

        /**
         * Forces the services to stop after the test and removes their corresponding containers and created volumes and
         * networks by running docker-compose down. Note that if due to some conditions your test is terminated
         * abnormally, for instance you kill your running tests or you call System.exit in your code, the services will
         * not be stopped properly. Also if you externally start some other docker containers which use the network
         * created by this docker network the stop will not be complete. So in general, you must consider this as a hint
         * not as a guarantee.
         * <p>If you don't enable this option (which is the default case), other tests can use those services started
         * by this test (provided that they don't enable {@link #forceRecreate()} without paying their startup times so
         * in overall your tests will take less time. This even applies to running tests several times, since after your
         * tests finished running, the next file of your tests will use the previous started services.
         * </p>
         */
        public BuilderStage forceDown() {
            spec.forceDown = true;
            return this;
        }

        /**
         * Adds a callback which will be called after all containers of services in this stage is run. Several Callbacks
         * can be added and they will be called in the same order. Each call is blocking and if one of them throw an
         * exception, the remaining ones are not running and the startup process will fail.
         */
        public BuilderStage afterStart(StartupCallback callback) {
            Validate.notNull(callback, "Callback must not be null");

            spec.startupCallbacks.add(callback);
            return this;
        }

        /**
         * Sets a different project name than the default one (which is the directory of your compose file).
         * <p>The container name of a service is the combination of its project name + it services name.</p>
         */
        public BuilderStage projectName(String name) {
            Validate.notEmpty(name, "Project name must not be empty");

            spec.projectName = name;
            return this;
        }

        /**
         * Specifies a docker-compose file located in the given resourcePath to be run. Calling this method creates a
         * new stage. Each stage is run with a call to docker-compose up -f this-file-path. Currently we only support
         * exploded archives (e.g. we don't support resources packed in a jar file) since we want direct access to this
         * resource file.
         *
         * @param resourcePath an absolute path to a resource.
         */
        public BuilderStage file(String resourcePath) {
            return builder.file(resourcePath);
        }

        /**
         * Adds given environment variable to created docker-compose process.
         *
         * @param name name of the variable to add.
         * @param value value of the added variable.
         */
        public BuilderStage environment(String name, String value) {
            spec.environment.put(name, value);
            return this;
        }

        /**
         * Builds the rule.
         */
        public DockerCompose build() {
            return builder.build();
        }
    }
}