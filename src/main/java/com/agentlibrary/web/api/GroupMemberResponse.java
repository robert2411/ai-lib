package com.agentlibrary.web.api;

import com.agentlibrary.service.ResolvedGroupMember;

/**
 * Response DTO for a single resolved group member.
 */
public record GroupMemberResponse(
        String slug,
        String role,
        ArtifactResponse metadata
) {
    /**
     * Creates a GroupMemberResponse from a ResolvedGroupMember.
     */
    public static GroupMemberResponse from(ResolvedGroupMember member) {
        return new GroupMemberResponse(
                member.slug(),
                member.role(),
                ArtifactResponse.fromMetadata(member.metadata())
        );
    }
}
