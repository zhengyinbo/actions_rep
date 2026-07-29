package com.bo.personalwebsite.vo;

public record ProjectVO(
        Long id,
        String name,
        String description,
        String techStack,
        String repoUrl,
        String demoUrl
) {
}

