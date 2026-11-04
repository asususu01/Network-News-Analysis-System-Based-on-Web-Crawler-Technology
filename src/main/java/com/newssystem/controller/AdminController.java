package com.newssystem.controller;

import com.newssystem.common.Result;
import com.newssystem.interceptor.LoginInterceptor;
import com.newssystem.service.AdminService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import javax.servlet.http.HttpSession;

@Controller
@RequestMapping("/admin")
public class AdminController {

    private static final Logger log = LoggerFactory.getLogger(AdminController.class);

    @Autowired
    private AdminService adminService;

    @GetMapping({"/login", "/login.html"})
    public String toLoginPage() {
        return "login";
    }

    @PostMapping("/doLogin")
    public String doLogin(@RequestParam String username,
                          @RequestParam String password,
                          HttpSession session,
                          RedirectAttributes redirectAttributes) {
        boolean ok = adminService.login(username, password, session);
        if (ok) {
            return "redirect:/index";
        }
        redirectAttributes.addFlashAttribute("errorMsg", "账号或密码错误");
        redirectAttributes.addFlashAttribute("username", username);
        return "redirect:/login";
    }

    @PostMapping("/login")
    @ResponseBody
    public Result<String> loginAjax(@RequestParam String username,
                                     @RequestParam String password,
                                     HttpSession session) {
        boolean ok = adminService.login(username, password, session);
        return ok ? Result.success("登录成功", null)
                : Result.fail("账号或密码错误");
    }

    @GetMapping("/logout")
    public String logout(HttpSession session) {
        session.removeAttribute(LoginInterceptor.SESSION_ADMIN_KEY);
        session.invalidate();
        return "redirect:/login";
    }
}
