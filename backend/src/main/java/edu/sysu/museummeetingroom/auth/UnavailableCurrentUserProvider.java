package edu.sysu.museummeetingroom.auth;

import edu.sysu.museummeetingroom.common.exception.ApiException;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
@Profile("!local")
public class UnavailableCurrentUserProvider implements CurrentUserProvider {
    @Override
    public CurrentUser currentUser() {
        throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "当前环境尚未配置认证提供方。");
    }
}
