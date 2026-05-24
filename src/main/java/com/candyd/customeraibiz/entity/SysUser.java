package com.candyd.customeraibiz.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Data;

@Data
public class SysUser {
    @TableId(type = IdType.AUTO)
    private Integer userId;
    private String username;
    private String password;
    private String role; // admin 或 user
    private String nickname;
}