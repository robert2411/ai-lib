package com.agentlibrary.model;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class UserTest {

    @Test
    void validConstruction() {
        User user = new User("alice", Set.of("USER"));
        assertEquals("alice", user.username());
        assertEquals(Set.of("USER"), user.roles());
    }

    @Test
    void nullUsernameThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> new User(null, Set.of("USER")));
    }

    @Test
    void blankUsernameThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> new User("  ", Set.of("USER")));
    }

    @Test
    void nullRolesThrows() {
        assertThrows(NullPointerException.class,
                () -> new User("alice", null));
    }

    @Test
    void defensiveCopyOfRoles() {
        Set<String> mutable = new HashSet<>();
        mutable.add("USER");
        User user = new User("alice", mutable);
        mutable.add("ADMIN"); // mutating original
        assertFalse(user.roles().contains("ADMIN"),
                "Mutating original set should not affect User");
    }

    @Test
    void rolesAreUnmodifiable() {
        User user = new User("alice", Set.of("USER"));
        assertThrows(UnsupportedOperationException.class,
                () -> user.roles().add("ADMIN"));
    }

    @Test
    void equality() {
        User a = new User("alice", Set.of("USER"));
        User b = new User("alice", Set.of("USER"));
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }
}
