package ir.sahab.dockercomposer;

/**
 * This package contains machinery to start external dependencies of your tests such as
 * databases, hadoop, .. as docker containers. You must describe these services as a set of
 * docker-compose files and add {@link ir.sahab.dockercomposer.DockerCompose} as a
 * rule to your test. This rule will bring those services up before your test and you can access
 * them by their ips and published ports.
 **/