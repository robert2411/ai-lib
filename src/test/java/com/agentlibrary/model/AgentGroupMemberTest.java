package com.agentlibrary.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AgentGroupMemberTest {

    @Test
    void validConstruction() {
        AgentGroupMember member = new AgentGroupMember("my-agent", "manager");
        assertEquals("my-agent", member.slug());
        assertEquals("manager", member.role());
    }

    @Test
    void slugAndRoleAccessible() {
        AgentGroupMember member = new AgentGroupMember("analyser", "analyse");
        assertEquals("analyser", member.slug());
        assertEquals("analyse", member.role());
    }

    @Test
    void nullSlugThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> new AgentGroupMember(null, "manager"));
    }

    @Test
    void blankSlugThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> new AgentGroupMember("  ", "manager"));
    }

    @Test
    void nullRoleThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> new AgentGroupMember("my-agent", null));
    }

    @Test
    void blankRoleThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> new AgentGroupMember("my-agent", "  "));
    }

    @Test
    void hasRecognisedRoleTrue() {
        assertTrue(new AgentGroupMember("a", "manager").hasRecognisedRole());
        assertTrue(new AgentGroupMember("a", "implementation").hasRecognisedRole());
        assertTrue(new AgentGroupMember("a", "analyse").hasRecognisedRole());
        assertTrue(new AgentGroupMember("a", "custom").hasRecognisedRole());
    }

    @Test
    void hasRecognisedRoleFalse() {
        assertFalse(new AgentGroupMember("a", "unknown").hasRecognisedRole());
        assertFalse(new AgentGroupMember("a", "MANAGER").hasRecognisedRole());
    }

    @Test
    void equality() {
        AgentGroupMember a = new AgentGroupMember("my-agent", "manager");
        AgentGroupMember b = new AgentGroupMember("my-agent", "manager");
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    void inequality() {
        AgentGroupMember a = new AgentGroupMember("my-agent", "manager");
        AgentGroupMember b = new AgentGroupMember("my-agent", "analyse");
        assertNotEquals(a, b);
    }
}
