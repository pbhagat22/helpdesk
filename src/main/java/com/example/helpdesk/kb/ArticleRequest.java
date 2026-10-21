package com.example.helpdesk.kb;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// Body is plain text; a blank line starts a new paragraph.
public record ArticleRequest(@NotBlank @Size(max = 255) String title, @NotBlank @Size(max = 20000) String body) {
}
