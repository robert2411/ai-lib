package com.agentlibrary.web.ui;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import java.util.ArrayList;
import java.util.List;

/**
 * Form-backing object for creating/editing artifacts.
 */
public class ArtifactForm {

    @NotBlank(message = "Name is required")
    @Pattern(regexp = "^[a-z0-9][a-z0-9\\-]*[a-z0-9]$", message = "Name must be a valid slug (lowercase, hyphens, no leading/trailing hyphen)")
    private String name;

    private String title;

    @NotBlank(message = "Type is required")
    private String type;

    @NotBlank(message = "Version is required")
    private String version;

    private String description;
    private List<String> harnesses = new ArrayList<>();
    private String tags;
    private String category;
    private String language;
    private String author;
    private String visibility;
    private String content;

    // Getters and setters

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public List<String> getHarnesses() { return harnesses; }
    public void setHarnesses(List<String> harnesses) { this.harnesses = harnesses; }

    public String getTags() { return tags; }
    public void setTags(String tags) { this.tags = tags; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getLanguage() { return language; }
    public void setLanguage(String language) { this.language = language; }

    public String getAuthor() { return author; }
    public void setAuthor(String author) { this.author = author; }

    public String getVisibility() { return visibility; }
    public void setVisibility(String visibility) { this.visibility = visibility; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
}
