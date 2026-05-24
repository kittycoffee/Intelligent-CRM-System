package com.candyd.customeraibiz.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.candyd.customeraibiz.entity.SysUser;
import com.candyd.customeraibiz.mapper.SysUserMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/auth")
@CrossOrigin(origins = "*")
public class AuthController {

    @Autowired
    private SysUserMapper userMapper;

    /**
     * 登录接口
     */
    @PostMapping("/login")
    public Map<String, Object> login(@RequestBody SysUser loginUser) {
        Map<String, Object> result = new HashMap<>();

        // 1. 查询数据库
        SysUser user = userMapper.selectOne(
                new QueryWrapper<SysUser>()
                        .eq("username", loginUser.getUsername())
                        .eq("password", loginUser.getPassword())
        );

        if (user != null) {
            // 2. 登录成功
            result.put("code", 200);
            result.put("msg", "登录成功");
            result.put("role", user.getRole());      // 核心：告诉前端我是谁
            result.put("nickname", user.getNickname());
        } else {
            // 3. 登录失败
            result.put("code", 401);
            result.put("msg", "账号或密码错误");
        }
        return result;
    }


    /**
     * 注册接口
     * 规则：账号不可重复，默认角色为 user
     */
    @PostMapping("/register")
    public Map<String, Object> register(@RequestBody SysUser user) {
        Map<String, Object> result = new HashMap<>();

        // 1. 检查账号是否已存在
        Long count = userMapper.selectCount(
                new QueryWrapper<SysUser>().eq("username", user.getUsername())
        );
        if (count > 0) {
            result.put("code", 400);
            result.put("msg", "账号已存在");
            return result;
        }

        // 2. 补全信息
        user.setRole("user");          // 默认注册为普通用户
        if (user.getNickname() == null || user.getNickname().isEmpty()) {
            user.setNickname(user.getUsername());
        }

        // 3. 插入数据库
        userMapper.insert(user);

        result.put("code", 200);
        result.put("msg", "注册成功");
        return result;
    }
}
