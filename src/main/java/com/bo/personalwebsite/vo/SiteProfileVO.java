package com.bo.personalwebsite.vo;

import java.util.List;

public record SiteProfileVO(
        String ownerName,
        String title,
        String summary,
        String location,
        List<String> skills
) {
}

