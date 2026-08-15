# stacc-api-gateway

## Reference Documentation

For further reference, please consider the following sections:

* [Official Apache Maven documentation](https://maven.apache.org/guides/index.html)
* [Spring Boot Maven Plugin Reference Guide](https://docs.spring.io/spring-boot/docs/3.2.5/maven-plugin/reference/html/)
* [Create an OCI image](https://docs.spring.io/spring-boot/docs/3.2.5/maven-plugin/reference/html/#build-image)

API Gateway for Campaign Controller project written in Java programming language.

## Dependency Management

The project uses Maven for dependency management. The dependencies are defined in the `pom.xml` file, and Maven will automatically download and include them in the project.

## Overview

The application is built using Spring Boot framework, which simplifies the development of Java applications by providing a set of conventions and pre-configured components. The project structure follows the standard Maven directory layout, with source code located in the `src/main/java` directory and resources in the `src/main/resources` directory.
The main class of the application is `StaccApiGatewayApplication`, which serves as the entry point for the Spring Boot application. The application is designed to act as an API Gateway, routing requests to various microservices that handle different aspects of the campaign management system. The API Gateway is responsible for handling incoming requests, performing authentication and authorization, and forwarding the requests to the appropriate microservices based on the defined routes and configurations. The application also includes various configurations for security, routing, and other aspects of the API Gateway functionality. Overall, the project is structured to provide a scalable and maintainable architecture for managing campaigns in a microservices environment.
