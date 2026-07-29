package com.bo.personalwebsite.entity;

import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("project")
public class Project {
    public Long id;
    public String name;
    public String description;
    public String techStack;
    public String repoUrl;
    public String demoUrl;
    public String coverUrl;
    public Integer sortOrder;
    public Integer status;
    public LocalDateTime createdAt;
    public LocalDateTime updatedAt;
}

