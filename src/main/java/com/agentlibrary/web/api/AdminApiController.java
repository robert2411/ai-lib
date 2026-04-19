package com.agentlibrary.web.api;

import com.agentlibrary.index.IndexService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * REST controller for administrative operations.
 * Requires ADMIN role for all endpoints.
 */
@RestController
@RequestMapping("/api/v1/admin")
public class AdminApiController {

    private final IndexService indexService;

    public AdminApiController(IndexService indexService) {
        this.indexService = indexService;
    }

    /**
     * Triggers a full index rebuild from the repository.
     * Requires ADMIN role.
     */
    @PostMapping("/reindex")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, String>> reindex() {
        indexService.refresh();
        return ResponseEntity.ok(Map.of(
                "status", "ok",
                "message", "Index rebuilt successfully"
        ));
    }
}
