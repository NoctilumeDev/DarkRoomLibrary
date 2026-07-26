package org.darkroomlibrary.controller;

import org.darkroomlibrary.aop.NormalizePageQuery;
import org.darkroomlibrary.aop.RequireRole;
import org.darkroomlibrary.domain.type.UserRole;
import org.darkroomlibrary.web.response.ApiResponse;
import org.darkroomlibrary.web.response.PageResponse;
import org.darkroomlibrary.web.dto.query.UserPageQuery;
import org.darkroomlibrary.web.dto.command.UserAdminUpdateDto;
import org.darkroomlibrary.web.dto.command.UserLoginDto;
import org.darkroomlibrary.web.dto.command.UserRegisterDto;
import org.darkroomlibrary.web.dto.command.UserUpdateDto;
import org.darkroomlibrary.web.dto.command.PasswordResetDto;
import org.darkroomlibrary.web.dto.command.PasswordUpdateDto;
import org.darkroomlibrary.web.dto.command.SendVerificationCodeDto;
import org.darkroomlibrary.domain.model.User;
import org.darkroomlibrary.web.view.MetricPoint;
import org.darkroomlibrary.web.view.UserProfileView;
import org.darkroomlibrary.service.UserService;
import org.springframework.beans.BeanUtils;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/user")
public class UserController {

    @Resource
    private UserService userService;

    /**
     * 用户登录
     */
    @PostMapping(value = "/login")
    public ApiResponse<Object> login(@Valid @RequestBody UserLoginDto userLoginDTO) {
        return userService.login(userLoginDTO);
    }

    /**
     * token校验
     */
    @RequireRole
    @GetMapping(value = "/auth")
    public ApiResponse<UserProfileView> auth() {
        return userService.auth();
    }

    /**
     * 通过ID查询用户信息
     */
    @RequireRole
    @GetMapping(value = "/getById/{id}")
    public ApiResponse<UserProfileView> getById(@PathVariable Integer id) {
        return userService.getById(id);
    }

    /**
     * 用户注册
     */
    @PostMapping(value = "/register")
    public ApiResponse<String> register(@Valid @RequestBody UserRegisterDto userRegisterDTO) {
        return userService.register(userRegisterDTO);
    }

    /**
     * 发送邮箱验证码
     */
    @PostMapping(value = "/sendVerifyCode")
    public ApiResponse<String> sendVerifyCode(@Valid @RequestBody SendVerificationCodeDto dto) {
        return userService.sendVerifyCode(dto.getEmail(), dto.getPurpose());
    }

    /**
     * 后台新增用户
     */
    @RequireRole(UserRole.ADMIN)
    @PostMapping(value = "/insert")
    public ApiResponse<String> insert(@Valid @RequestBody UserRegisterDto userRegisterDTO) {
        return userService.insert(userRegisterDTO);
    }

    /**
     * 用户信息修改
     */
    @RequireRole
    @PutMapping(value = "/update")
    public ApiResponse<String> update(@Valid @RequestBody UserUpdateDto userUpdateDTO) {
        return userService.update(userUpdateDTO);
    }

    /**
     * 后台用户信息修改
     */
    @RequireRole(UserRole.ADMIN)
    @PutMapping(value = "/backUpdate")
    public ApiResponse<String> backUpdate(@Valid @RequestBody UserAdminUpdateDto dto) {
        return userService.backUpdate(dto);
    }

    /**
     * 用户修改密码
     */
    @RequireRole
    @PutMapping(value = "/updatePwd")
    public ApiResponse<String> updatePwd(@Valid @RequestBody PasswordUpdateDto dto) {
        return userService.updatePwd(dto);
    }

    /**
     * 批量删除用户信息
     */
    @RequireRole(UserRole.ADMIN)
    @PostMapping(value = "/batchDelete")
    public ApiResponse<String> batchDelete(@RequestBody List<Integer> ids) {
        return userService.batchDelete(ids);
    }

    /**
     * 冻结用户
     */
    @RequireRole(UserRole.ADMIN)
    @PutMapping(value = "/freeze/{id}")
    public ApiResponse<String> freezeUser(@PathVariable Integer id) {
        return userService.freezeUser(id);
    }

    /**
     * 解冻用户
     */
    @RequireRole(UserRole.ADMIN)
    @PutMapping(value = "/unfreeze/{id}")
    public ApiResponse<String> unfreezeUser(@PathVariable Integer id) {
        return userService.unfreezeUser(id);
    }

    /**
     * 读者自助注销账号
     */
    @RequireRole
    @PutMapping(value = "/cancelAccount")
    public ApiResponse<String> cancelAccount() {
        return userService.cancelAccount();
    }

    /**
     * 查询用户数据
     */
    @NormalizePageQuery
    @RequireRole(UserRole.ADMIN)
    @PostMapping(value = "/query")
    public ApiResponse<List<UserProfileView>> query(@RequestBody UserPageQuery userPageQuery) {
        ApiResponse<List<User>> result = userService.query(userPageQuery);
        if (result.getCode() != 200 || result.getData() == null) {
            return ApiResponse.error(result.getMsg());
        }
        List<UserProfileView> vos = result.getData().stream().map(user -> {
            UserProfileView vo = new UserProfileView();
            BeanUtils.copyProperties(user, vo);
            return vo;
        }).collect(Collectors.toList());
        return PageResponse.success(vos, ((PageResponse<?>) result).getTotal());
    }

    /**
     * 统计用户存量数据
     */
    @RequireRole({UserRole.ADMIN, UserRole.SUPER_ADMIN})
    @GetMapping(value = "/queryByDays/{day}")
    public ApiResponse<List<MetricPoint>> query(@PathVariable Integer day) {
        return userService.queryByDays(day);
    }

    @RequireRole({UserRole.ADMIN, UserRole.ACQUISITIONS})
    @GetMapping(value = "/collaborationUsers")
    public ApiResponse<List<UserProfileView>> collaborationUsers(@RequestParam Integer role) {
        return userService.queryCollaborationUsers(role);
    }

    /**
     * 忘记密码 - 通过邮箱重置
     */
    @PostMapping(value = "/resetPwd")
    public ApiResponse<String> resetPwd(@Valid @RequestBody PasswordResetDto dto) {
        return userService.resetPwd(dto);
    }
}
