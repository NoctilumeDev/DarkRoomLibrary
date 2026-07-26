package org.darkroomlibrary.controller;

import org.darkroomlibrary.web.response.ApiResponse;
import org.darkroomlibrary.service.CaptchaService;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;
import java.util.Map;

/**
 * 滑块验证码控制器（简化版：数学运算题）
 */
@RestController
@RequestMapping("/captcha")
public class CaptchaController {

    @Resource
    private CaptchaService captchaService;

    /**
     * 生成验证码
     */
    @GetMapping("/generate")
    public ApiResponse<Map<String, String>> generate() {
        return ApiResponse.success(captchaService.generate());
    }

    /**
     * 验证验证码
     */
    @PostMapping("/verify")
    public ApiResponse<Boolean> verify(@RequestParam String captchaId, @RequestParam Integer answer) {
        return ApiResponse.success(captchaService.verify(captchaId, answer));
    }
}
