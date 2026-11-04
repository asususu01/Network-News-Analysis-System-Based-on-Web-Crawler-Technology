package com.newssystem.controller;

import com.newssystem.common.Result;
import com.newssystem.entity.User;
import com.newssystem.service.UserLogService;
import com.newssystem.service.UserService;
import com.newssystem.service.impl.UserServiceImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import javax.servlet.http.HttpSession;

/**
 * 用户端控制器（注册/登录/登出）
 * 用户与管理员是两套独立账号体系，会话 key 不同。
 *
 * @author NewsSystem Dev
 */
@Controller
@RequestMapping("/user")
public class UserController {

    private static final Logger log = LoggerFactory.getLogger(UserController.class);

    @Autowired
    private UserService userService;

    @Autowired
    private UserLogService userLogService;

    /** 用户注册页 */
    @GetMapping("/register")
    public String toRegister() {
        return "register";
    }

    /** 处理注册：用户名/密码/年龄/性别/职业 */
    @PostMapping("/doRegister")
    public String doRegister(@RequestParam String username,
                             @RequestParam String password,
                             @RequestParam(required = false) Integer age,
                             @RequestParam(required = false) Integer gender,
                             @RequestParam(required = false) String occupation,
                             RedirectAttributes redirectAttributes) {
        boolean ok = userService.register(username, password, age, gender, occupation);
        if (ok) {
            redirectAttributes.addFlashAttribute("successMsg", "注册成功，请登录");
            return "redirect:/login";
        }
        redirectAttributes.addFlashAttribute("errorMsg", "注册失败：用户名已存在或参数不合法");
        redirectAttributes.addFlashAttribute("username", username);
        redirectAttributes.addFlashAttribute("age", age);
        redirectAttributes.addFlashAttribute("gender", gender);
        redirectAttributes.addFlashAttribute("occupation", occupation);
        return "redirect:/user/register";
    }

    /** 用户登录：跳转到统一登录页（系统自动识别身份） */
    @GetMapping("/login")
    public String toLogin() {
        return "redirect:/login";
    }

    /** 处理用户登录，成功后跳转用户端新闻列表 */
    @PostMapping("/doLogin")
    public String doLogin(@RequestParam String username,
                          @RequestParam String password,
                          HttpSession session,
                          RedirectAttributes redirectAttributes) {
        boolean ok = userService.login(username, password, session);
        if (ok) {
            return "redirect:/client";
        }
        redirectAttributes.addFlashAttribute("errorMsg", "账号或密码错误");
        redirectAttributes.addFlashAttribute("username", username);
        return "redirect:/login";
    }

    /** 注销 */
    @GetMapping("/logout")
    public String logout(HttpSession session) {
        // 必须在session失效前取出用户并记录退出日志
        User loginUser = (User) session.getAttribute(UserServiceImpl.SESSION_USER_KEY);
        if (loginUser != null) {
            userLogService.record(loginUser.getId(), loginUser.getUsername(),
                    UserLogService.ACTION_LOGOUT, "用户退出登录");
        }
        session.removeAttribute(UserServiceImpl.SESSION_USER_KEY);
        session.invalidate();
        return "redirect:/client";
    }

    /** 获取当前登录用户信息（供前端判断登录状态） */
    @GetMapping("/current")
    @ResponseBody
    public Result<User> current(HttpSession session) {
        User user = (User) session.getAttribute(UserServiceImpl.SESSION_USER_KEY);
        return Result.success(user);
    }

    /**
     * 修改个人信息（年龄、性别、职业、新密码）。
     * 新密码为空表示不修改密码。修改成功后刷新 session 中的用户信息。
     */
    @PostMapping("/profile")
    public String updateProfile(@RequestParam(required = false) Integer age,
                                @RequestParam(required = false) Integer gender,
                                @RequestParam(required = false) String occupation,
                                @RequestParam(required = false) String newPassword,
                                HttpSession session,
                                RedirectAttributes redirectAttributes) {
        User loginUser = (User) session.getAttribute(UserServiceImpl.SESSION_USER_KEY);
        if (loginUser == null) {
            redirectAttributes.addFlashAttribute("errorMsg", "请先登录");
            return "redirect:/login";
        }
        boolean ok = userService.updateProfile(loginUser.getId(), age, gender, occupation, newPassword);
        if (ok) {
            // 刷新 session 中的用户信息（密码置空）
            User fresh = userService.getByUsername(loginUser.getUsername());
            if (fresh != null) {
                fresh.setPassword(null);
                session.setAttribute(UserServiceImpl.SESSION_USER_KEY, fresh);
            }
            redirectAttributes.addFlashAttribute("successMsg", "个人信息修改成功");
        } else {
            redirectAttributes.addFlashAttribute("errorMsg", "个人信息修改失败");
        }
        return "redirect:/client";
    }
}
