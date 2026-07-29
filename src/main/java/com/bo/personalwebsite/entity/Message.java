package com.bo.personalwebsite.entity;

import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("message")
public class Message {
    public Long id;
    public String nickname;
    public String email;
    public String content;
    public Integer status;
    public String ip;
    public LocalDateTime createdAt;
}

