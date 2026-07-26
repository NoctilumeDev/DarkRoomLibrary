package org.darkroomlibrary.service;

import org.darkroomlibrary.web.response.ApiResponse;
import org.darkroomlibrary.web.dto.command.UserAdminUpdateDto;
import org.darkroomlibrary.web.dto.command.UserLoginDto;
import org.darkroomlibrary.web.dto.command.UserRegisterDto;
import org.darkroomlibrary.web.dto.command.UserUpdateDto;
import org.darkroomlibrary.web.dto.command.PasswordResetDto;
import org.darkroomlibrary.web.dto.command.PasswordUpdateDto;
import org.darkroomlibrary.web.dto.query.UserPageQuery;
import org.darkroomlibrary.domain.model.User;
import org.darkroomlibrary.web.view.MetricPoint;
import org.darkroomlibrary.web.view.UserProfileView;

import java.util.List;

/**
 * 用户服务类
 */
public interface UserService {
    ApiResponse<String> register(UserRegisterDto userRegisterDTO);

    ApiResponse<Object> login(UserLoginDto userLoginDTO);

    ApiResponse<UserProfileView> auth();

    ApiResponse<List<User>> query(UserPageQuery userPageQuery);

    ApiResponse<String> update(UserUpdateDto userUpdateDTO);

    ApiResponse<String> batchDelete(List<Integer> ids);

    ApiResponse<String> updatePwd(PasswordUpdateDto dto);

    ApiResponse<UserProfileView> getById(Integer id);

    ApiResponse<String> insert(UserRegisterDto userRegisterDTO);

    ApiResponse<String> backUpdate(UserAdminUpdateDto dto);

    ApiResponse<List<MetricPoint>> queryByDays(Integer day);

    /**
     * 忘记密码 - 通过邮箱+验证码重置密码
     */
    ApiResponse<String> resetPwd(PasswordResetDto dto);

    /**
     * 发送邮箱验证码
     */
    ApiResponse<String> sendVerifyCode(String email);

    ApiResponse<String> sendVerifyCode(String email, String purpose);

    /**
     * 冻结用户
     */
    ApiResponse<String> freezeUser(Integer userId);

    /**
     * 解冻用户
     */
    ApiResponse<String> unfreezeUser(Integer userId);

    /**
     * 读者自助注销账号
     */
    ApiResponse<String> cancelAccount();

    ApiResponse<List<UserProfileView>> queryCollaborationUsers(Integer role);

}
