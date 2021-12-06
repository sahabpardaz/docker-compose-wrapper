package ir.sahab.dockercomposer;

import java.util.Map;

/**
 * Called after docker-compose initially starts all the services in the docker-compose files. Note that in this state,
 * only the containers of services are started and the services themselves might not started completely yet. You can use
 * this callback for waiting for your services to become healthy or do any initialization stuff.
 */
public interface StartupCallback {

    /**
     * Called after containers of services are started.
     *
     * @param servicesByName a map of all services in the current stage (those services specified in the docker-compose
     * file) from service name to the service itself.
     * @throws Exception if thrown, the startup process will be failed.
     */
    void process(Map<String, Service> servicesByName) throws Exception;
}
