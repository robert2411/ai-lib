package com.agentlibrary.cli;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link BcryptCli}.
 */
class BcryptCliTest {

    private final PrintStream originalOut = System.out;
    private final PrintStream originalErr = System.err;
    private ByteArrayOutputStream capturedOut;
    private ByteArrayOutputStream capturedErr;

    @BeforeEach
    void captureOutput() {
        capturedOut = new ByteArrayOutputStream();
        capturedErr = new ByteArrayOutputStream();
        System.setOut(new PrintStream(capturedOut));
        System.setErr(new PrintStream(capturedErr));
    }

    @AfterEach
    void restoreOutput() {
        System.setOut(originalOut);
        System.setErr(originalErr);
    }

    @Test
    void mainWithPassword_printsValidBcryptHash() {
        BcryptCli.main(new String[]{"testpassword"});

        String output = capturedOut.toString().trim();
        assertTrue(output.startsWith("$2a$") || output.startsWith("$2b$"),
                "Output should be a BCrypt hash, got: " + output);
        // Typical BCrypt hash is 60 chars
        assertEquals(60, output.length(),
                "BCrypt hash should be 60 characters, got: " + output.length());
    }

    @Test
    void mainWithPassword_hashVerifiesCorrectly() {
        String password = "mysecretpassword";
        BcryptCli.main(new String[]{password});

        String hash = capturedOut.toString().trim();
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        assertTrue(encoder.matches(password, hash),
                "Generated hash should verify against the original password");
    }

    @Test
    void mainWithDifferentPasswords_producesDifferentHashes() {
        BcryptCli.main(new String[]{"password1"});
        String hash1 = capturedOut.toString().trim();

        // Reset capture
        capturedOut.reset();

        BcryptCli.main(new String[]{"password2"});
        String hash2 = capturedOut.toString().trim();

        assertNotEquals(hash1, hash2, "Different passwords should produce different hashes");
    }

    @Test
    void generateHash_producesValidBcryptHash() {
        String hash = BcryptCli.generateHash("testpass");
        assertTrue(hash.startsWith("$2a$") || hash.startsWith("$2b$"),
                "Should produce BCrypt hash");
        assertEquals(60, hash.length());
    }

    @Test
    void generateHash_verifiesCorrectly() {
        String password = "verify-me";
        String hash = BcryptCli.generateHash(password);
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        assertTrue(encoder.matches(password, hash));
    }

    @Test
    void mainWithSpecialCharacters_works() {
        String password = "p@ss w0rd!#$%";
        BcryptCli.main(new String[]{password});

        String hash = capturedOut.toString().trim();
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        assertTrue(encoder.matches(password, hash),
                "Hash should verify for passwords with special characters");
    }
}
