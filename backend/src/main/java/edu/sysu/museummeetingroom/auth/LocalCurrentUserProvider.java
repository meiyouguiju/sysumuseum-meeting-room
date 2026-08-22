package edu.sysu.museummeetingroom.auth;

import edu.sysu.museummeetingroom.common.exception.ApiException;
import edu.sysu.museummeetingroom.user.mapper.SysUserMapper;
import edu.sysu.museummeetingroom.user.mapper.UserRow;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
@Profile("local")
public class LocalCurrentUserProvider implements CurrentUserProvider {

    private final long localUserId;
    private final SysUserMapper sysUserMapper;

    public LocalCurrentUserProvider(
            @Value("${local.current-user-id}") long localUserId,
            SysUserMapper sysUserMapper) {
        this.localUserId = localUserId;
        this.sysUserMapper = sysUserMapper;
    }

    @Override
    public CurrentUser currentUser() {
        UserRow user = sysUserMapper.findById(localUserId);
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
