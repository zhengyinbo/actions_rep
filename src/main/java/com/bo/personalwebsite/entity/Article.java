package com.bo.personalwebsite.entity;

import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("blog_article")
public class Article {
    public Long id;
    public String title;
    public String slug;
    public String summary;
    public String content;
    public String coverUrl;
    public Long categoryId;
    public Integer status;
    public Integer viewCount;
    public LocalDateTime publishedAt;
    public LocalDateTime createdAt;
    public LocalDateTime updatedAt;
}

