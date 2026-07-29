package com.bo.personalwebsite.controller;

import com.bo.personalwebsite.common.ApiResponse;
import com.bo.personalwebsite.service.SiteService;
import com.bo.personalwebsite.vo.ArticleSummaryVO;
import com.bo.personalwebsite.vo.ProjectVO;
import com.bo.personalwebsite.vo.SiteProfileVO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
public class SiteController {

    private final SiteService siteService;

    public SiteController(SiteService siteService) {
        this.siteService = siteService;
    }

    @GetMapping("/site/profile")
    public ApiResponse<SiteProfileVO> profile() {
        return ApiResponse.success(siteService.getProfile());
    }

    @GetMapping("/projects")
    public ApiResponse<List<ProjectVO>> projects() {
        return ApiResponse.success(siteService.listProjects());
    }

    @GetMapping("/articles")
    public ApiResponse<List<ArticleSummaryVO>> articles() {
        return ApiResponse.success(siteService.listArticles());
    }
}

