Docker-Compose Test Utility
===========================
[![Build Status](https://travis-ci.org/sahabpardaz/docker-compose-wrapper.svg?branch=master)](https://travis-ci.org/sahabpardaz/docker-compose-wrapper)

### Introduction

This utility is supposed to help test developers to use external services in their tests using a Dockerized environment. This utility is used as a test-rule in Junit tests. It can help developers in the situations such as:
- When we want to be more realistic than using a simple mock or an embedded version of a service. For example,   when we want to test some performance aspects, availability aspects or integration aspects.
- When we want to test using a cluster of a service not just an embedded single instance (e.g., a Zookeeper cluster).
- When we want to test an external service that has not an official/well-working embedded version.

### Prerequisites
In order to use this tool in your Java project, you need to have the following situations:
- Have Docker and Docker-Compose installed on your system
- The Docker configured to run without `sudo` access

### Some Considerations
1. Containers started by this rule are likely to stay alive in your system until explicitly killed by yourself. Therefore, you will need some bookkeeping to clean your environment regularly using `docker kill $(docker ps -q) && docker rm $(docker ps -q -a)`.
(Warn: This command will also kill and remove other containers. Be careful!)

2. By default, there is no guarantee that the container is not used before by some tests. If you want a fresh container in your test, you can use `forceRecreate` param in your builder.

3. Docker-compose uses project name to group services together so docker-compose creates two different containers for two services with the same name but with different project names. By default docker-compose uses the COMPOSE_PROJECT_NAME environment variable and if it is not set it uses the name of directory of your docker-compose file as the project name. You can change this using a property of the builder.

4. Since docker-compose setups a dns server, containers are also accessible by their names but only on their private network and not from your tests.

5. There is a special environment variable named DIRECTORY injected by this rule which points to the directory that your compose-file is located. You can use this variable to mount a directory in your resource folder to your docker-compose service like this:
``` volumes: - "$DIRECTORY/zookeeper/conf:/opt/zookeeper/conf"```

6. We do not support docker swarm, so in writing docker-compose files, some features like replicas and relay networks are not valid to use.

7.  Versions >= 3 of `docker-compose`  do not provide any guarantee that the service inside the container is actually up, it only starts the container. To wait for some conditions you can provide a callback to the builder using `afterStart` method, which will be called after the containers are started. There is a helper class `WaitFor` which implements some common cases.

8. If some of your services depend on other services to be started completely, you can use the multi-stage facility this rule provides. Suppose you have two services s1 and s2 where s2 depends on s1 to be running. To this end you must place the definition of these services in two docker-compose files, and build the rule like this:
```java
DockerCompose dockerCompose = DockerCompose.builder()
        .file("/s1.yaml")                   // Stage 1
        .afterStart(WaitFor.portOpen("s1")) // Wait until services in this stage complete
        .file("/s2.yaml")                   // Stage 2
        .build()
```
9. While creating each Service, we add a DNS mapping to current JVM to map service name to internal IP of its container. So, the user can access container's network by `Service.getName()` as a hostname. So, the user does not have to set mappings of desired port to local machine and can access any port inside the container.
For those use-cases that need to access container's hostname (e.g. HBase), they need to set hostname in the docker-compose YAML file and need to set it equal to service name.

### Usage
To use this in the tests, you need to create an instance of `DockerCompose` class as a JUnit rule to start a set of services using docker-compose utility. You must build an instance of this rule using its builder() method and give it the description of your desired services (your target environment) using docker-compose files. You can access these services by their names in the given file.

Suppose you want to test your code, and your code depends on the zookeeper to be properly running. You can bring zookeeper up in a docker container using this rule if you follow these steps:
Install docker and then docker-compose in your system. Don't forget to follow after- install instructions available at docker site which removes the necessity to run docker with sudo command. After this step running "docker info" and "docker-compose --version" must give you no errors.
Find or build an appropriate docker image for zookeeper. This image must be in your private registry or public docker hub. You can test this by running "docker pull image-name"
Write a docker-compose file say zookeeper.yaml with a service named zookeeper with the above image and publish zookeeper client port which is 2181. Put this file somewhere under your test resources directory. To test this file you can go to the location of this file and run "docker-compose -f zookeeper.yaml up". This command must bring your zookeeper container up so running "docker ps" must list this container with its published port 2181.
The YAML file `zookeeper.yaml` can be like below:
```yaml
version: "3"
services:
  zookeeper:
    image: zookeeper:latest
```
A sample code to show how to add this rule to your test:
```java
@ClassRule
static DockerCompose dockerCompose =
	DockerCompose.builder()
	.file("/zookeeper.yaml")
	.afterStart(WaitFor.portOpen("zookeeper", 2181))
	.build();
```
In your test, you can get the service address this way:
```java
Service zkService = dockerCompose.getServiceByName("zookeeper");
String ip = zkService.getInternalIp();
String port = zkService.getPort(2181);
```
OR this way:
```java
String hostname = zkService.getName();
String port = "2181";
 ```
## Add it to your project
You can reference to this library by either of java build systems (Maven, Gradle, SBT or Leiningen) using snippets from this jitpack link:
[![](https://jitpack.io/v/sahabpardaz/docker-compose-wrapper.svg)](https://jitpack.io/#sahabpardaz/docker-compose-wrapper)
