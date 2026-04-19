package com.agentlibrary.cli;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * Command-line utility that generates a BCrypt hash for a given password.
 * The output can be pasted directly into users.yaml.
 *
 * <p>Usage: {@code BcryptCli <password>}</p>
 *
 * <p>Intended to be run via {@code ./mvnw exec:java} rather than as a standalone JAR,
 * since the Spring Boot fat JAR places classes under BOOT-INF/classes/.</p>
 */
public class BcryptCli {

    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println("Usage: BcryptCli <password>");
            System.exit(1);
            return; // unreachable but aids testing with SecurityManager
        }

        String hash = generateHash(args[0]);
        System.out.println(hash);
    }

    /**
     * Generates a BCrypt hash for the given password.
     * Extracted for testability.
     */
    static String generateHash(String password) {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        return encoder.encode(password);
    }
}

