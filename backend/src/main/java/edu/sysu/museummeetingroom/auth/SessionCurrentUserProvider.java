package edu.sysu.museummeetingroom.auth;

import edu.sysu.museummeetingroom.common.exception.ApiException;
import edu.sysu.museummeetingroom.user.mapper.SysUserMapper;
import edu.sysu.museummeetingroom.user.mapper.UserRow;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
@Profile("!local")
@RequiredArgsConstructor
public class SessionCurrentUserProvider implements CurrentUserProvider {

    private final SysUserMapper sysUserMapper;

    @Override
    public CurrentUser currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof SessionUserPrincipal principal)) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "当前登录状态不可用。");
        }
        UserRow user = sysUserMapper.findActiveById(principal.userId());
        if (user == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "当前用户不存在或不可用。");
        }
        return new CurrentUser(
                user.id(),
                user.displayName(),
                user.roleCode(),
                user.status(),
                user.departmentName());
    }
}
