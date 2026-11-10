package com.gradeup;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * GradeUp — a study assistant for university students.
 *
 * WHAT THIS FILE DOES
 * This is where the program starts. Running main() boots Spring Boot, which then:
 *   1. scans every class in the com.gradeup package (and sub-packages),
 *   2. creates the objects we marked with @Controller, @Service, @Component, etc.,
 *   3. connects them together (this is called "dependency injection"),
 *   4. starts a web server on port 8081.
 *
 * ANNOTATIONS USED HERE
 *   @SpringBootApplication       - "this is a Spring Boot app, set everything up for me".
 *   @ConfigurationPropertiesScan - also load our settings classes (see GradeUpProperties),
 *                                  which read values from application.yml.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class GradeUpApplication {

    public static void main(String[] args) {
        SpringApplication.run(GradeUpApplication.class, args);
    }
}
