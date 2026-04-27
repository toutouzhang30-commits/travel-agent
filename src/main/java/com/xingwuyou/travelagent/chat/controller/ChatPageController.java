package com.xingwuyou.travelagent.chat.controller;


import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

//页面入口
@Controller
public class ChatPageController {
    @GetMapping({"/", "/chat"})
    public String chatPage() {
        // 返回模板名称 "chat"
        // Thymeleaf 会自动寻找 src/main/resources/templates/chat.html
        return "chat";
    }
}
