package com.oryxos.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 管理台静态页入口：裸 /admin 跳到真实静态资源 /admin/index.html，
 * 避免落到 NoResourceFoundException 的 JSON 信封（contracts/admin-rest-api.md）。
 */
@Controller
public class AdminPageController {

    @GetMapping("/admin")
    public String index() {
        return "redirect:/admin/index.html";
    }
}
