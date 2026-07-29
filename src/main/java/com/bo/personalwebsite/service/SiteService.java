package com.bo.personalwebsite.service;

import com.bo.personalwebsite.vo.ArticleSummaryVO;
import com.bo.personalwebsite.vo.ProjectVO;
import com.bo.personalwebsite.vo.SiteProfileVO;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SiteService {

    public SiteProfileVO getProfile() {
        return new SiteProfileVO(
                "Bo",
                "Bo 的个人网站",
                "一个用 Vue 3 + Spring Boot 搭建的个人网站，用来沉淀项目、博客和工程化部署经验。",
                "China",
                List.of("Vue 3", "TypeScript", "Spring Boot", "Shiro", "MyBatis-Plus", "MySQL", "Redis", "Docker")
        );
    }

    public List<ProjectVO> listProjects() {
        return List.of(
                new ProjectVO(
                        1L,
                        "Personal Website",
                        "前后端分离个人网站，第一阶段先跑通基础页面和 API。",
                        "Vue 3 / Spring Boot / MyBatis-Plus",
                        null,
                        null
                ),
                new ProjectVO(
                        2L,
                        "Docker Deploy Lab",
                        "后续用于练习 Docker Compose、Nginx 反向代理和自动化部署。",
                        "Docker / Nginx / GitHub Actions",
                        null,
                        null
                )
        );
    }

    public List<ArticleSummaryVO> listArticles() {
        return List.of(
                new ArticleSummaryVO(
                        1L,
                        "个人网站项目初始化记录",
                        "记录为什么选择一个仓库、前后端分目录，以及第一版工程结构。",
                        "2026-07-18"
                ),
                new ArticleSummaryVO(
                        2L,
                        "从本地启动到 Docker 化的路线",
                        "先本地开发，再容器化基础服务，最后接入 Nginx 和 CI/CD。",
                        "2026-07-18"
                )
        );
    }
}

