package com.bo.personalwebsite.entity;

import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("sys_user")
public class SysUser {
    public Long id;
    public String username;
    public String password;
    public String nickname;
    public String avatar;
    public Integer status;
    public LocalDateTime createdAt;
    public LocalDateTime updatedAt;
}

