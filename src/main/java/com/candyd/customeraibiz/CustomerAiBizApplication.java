package com.candyd.customeraibiz;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.web.bind.annotation.GetMapping; // 新增这一行
import org.springframework.web.bind.annotation.RestController; // 新增这一行

@SpringBootApplication
@EnableAsync // 👇 2. 加上这个注解，告诉 Spring 开启全局异步支持！
@MapperScan("com.candyd.customeraibiz.mapper") // 核心！必须加在这里，且路径要写对
public class CustomerAiBizApplication {

    public static void main(String[] args) {
        SpringApplication.run(CustomerAiBizApplication.class, args);
    }

    // 新增下面这 4 行代码：
    @GetMapping("/hello")
    public String hello() {
        return "下午5点，主角觉醒！AI 毕设系统已就绪！";
    }
}