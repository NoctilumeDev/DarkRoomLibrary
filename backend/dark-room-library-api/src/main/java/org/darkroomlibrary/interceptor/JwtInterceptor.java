package org.darkroomlibrary.interceptor;

import org.darkroomlibrary.context.CurrentUserContext;
import org.darkroomlibrary.infrastructure.security.UserAuthLookup;
import org.darkroomlibrary.web.response.ApiResponse;
import org.darkroomlibrary.utils.JwtUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.Writer;

/**
 * token拦截器，做请求拦截
 */
public class JwtInterceptor implements HandlerInterceptor {

    private final String apiPrefix;
    private final UserAuthLookup userAuthLookup;
    private final ObjectMapper objectMapper;
    private final JwtUtil jwtUtil;

    public JwtInterceptor(
            String apiPrefix,
            UserAuthLookup userAuthLookup,
            ObjectMapper objectMapper,
            JwtUtil jwtUtil) {
        this.apiPrefix = apiPrefix;
        this.userAuthLookup = userAuthLookup;
        this.objectMapper = objectMapper;
        this.jwtUtil = jwtUtil;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String requestMethod = request.getMethod();
        // 放行预检请求
        if ("OPTIONS".equals(requestMethod)) {
            return true;
        }
        String requestURI = request.getRequestURI();
        // 白名单路径：使用精确前缀匹配替代 contains 子串匹配（防止绕过）
        if (matchesPath(requestURI, apiPrefix + "/user/login")
                || matchesPath(requestURI, apiPrefix + "/user/register")
                || matchesPath(requestURI, apiPrefix + "/user/resetPwd")
                || matchesPath(requestURI, apiPrefix + "/user/sendVerifyCode")
                || matchesPath(requestURI, apiPrefix + "/file/getFile")
                || matchesPath(requestURI, apiPrefix + "/file/public")
                || matchesPath(requestURI, apiPrefix + "/captcha/generate")
                || matchesPath(requestURI, apiPrefix + "/captcha/verify")
                || matchesPath(requestURI, apiPrefix + "/health/live")
                || matchesPath(requestURI, apiPrefix + "/health/ready")
                || matchesPath(requestURI, apiPrefix + "/error")) {
            return true;
        }
        Claims claims = jwtUtil.fromToken(resolveToken(request));
        if (claims == null) {
            writeAuthError(response);
            return false;
        }
        Integer userId = claims.get("id", Integer.class);
        UserAuthLookup.AuthUser user = userAuthLookup.getActiveUser(userId).orElse(null);
        if (user == null || Boolean.TRUE.equals(user.getDisabled())) {
            writeAuthError(response);
            return false;
        }
        CurrentUserContext.bind(user.getId(), user.getUserRole());
        return true;
    }

    private String resolveToken(HttpServletRequest request) {
        String authorization = request.getHeader("Authorization");
        if (authorization != null && authorization.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return authorization.substring(7).trim();
        }
        return request.getHeader("token");
    }

    private boolean matchesPath(String requestURI, String path) {
        return requestURI.equals(path) || requestURI.startsWith(path + "/");
    }

    private void writeAuthError(HttpServletResponse response) throws Exception {
        ApiResponse<String> error = ApiResponse.error("身份认证异常，请先登录");
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        Writer stream = response.getWriter();
        stream.write(objectMapper.writeValueAsString(error));
        stream.flush();
        stream.close();
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        CurrentUserContext.clear();
    }
}
