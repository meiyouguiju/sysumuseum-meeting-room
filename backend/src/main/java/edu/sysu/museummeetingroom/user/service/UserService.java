package edu.sysu.museummeetingroom.user.service;

import edu.sysu.museummeetingroom.auth.CurrentUser;
import edu.sysu.museummeetingroom.auth.CurrentUserProvider;
import edu.sysu.museummeetingroom.user.dto.CurrentUserResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserService {

    private final CurrentUserProvider currentUserProvider;

    public CurrentUserResponse currentUser() {
        CurrentUser user = currentUserProvider.currentUser();
        return new CurrentUserResponse(user.userId(), user.displayName(), user.departmentName(), user.roleCode(), user.userStatus());
    }
}
