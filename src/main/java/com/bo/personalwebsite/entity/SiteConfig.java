package com.bo.personalwebsite.entity;

import com.baomidou.mybatisplus.annotation.TableName;

@TableName("site_config")
public class SiteConfig {
    public Long id;
    public String configKey;
    public String configValue;
}

