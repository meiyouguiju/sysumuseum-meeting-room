package edu.sysu.museummeetingroom.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import edu.sysu.museummeetingroom.common.exception.ApiException;
import edu.sysu.museummeetingroom.user.mapper.SysUserMapper;
import edu.sysu.museummeetingroom.user.mapper.UserRow;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.SecurityContextRepository;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Mock
    private SysUserMapper sysUserMapper;

    @Mock
    private SecurityContextRepository securityContextRepository;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(sysUserMapper, passwordEncoder, securityContextRepository);
    }

    @Test
    void loginAcceptsLeadingZeroPinAndStoresOnlyUserIdPrincipal() {
        UserRow user = activeUser(700001L, "张伟", "0376");
        when(sysUserMapper.findActiveByDisplayName("张伟")).thenReturn(List.of(user));
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        var currentUser = authService.login(new LoginRequest("张伟", "0376"), request, response);

        assertThat(currentUser.id()).isEqualTo(700001L);
        assertThat(currentUser.displayName()).isEqualTo("张伟");
        verify(securityContextRepository).saveContext(any(), any(), any());
        Object principal = org.springframework.security.core.context.SecurityContextHolder.getContext()
                .getAuthentication()
                .getPrincipal();
        assertThat(principal).isEqualTo(new SessionUserPrincipal(700001L));
        authService.logout(request, response);
    }

    @Test
    void sameNameUsersAreDistinguishedByTheirOwnPins() {
        UserRow firstUser = activeUser(700001L, "张伟", "1357");
        UserRow secondUser = activeUser(700002L, "张伟", "4826");
        when(sysUserMapper.findActiveByDisplayName("张伟")).thenReturn(List.of(firstUser, secondUser));

        var currentUser = authService.login(
                new LoginRequest("张伟", "4826"), new MockHttpServletRequest(), new MockHttpServletResponse());

        assertThat(currentUser.id()).isEqualTo(700002L);
    }

    @Test
    void invalidNameOrPinReturnsTheSameGenericFailure() {
        when(sysUserMapper.findActiveByDisplayName("不存在")).thenReturn(List.of());

        assertThatThrownBy(() -> authService.login(
                        new LoginRequest("不存在", "1234"),
                        new MockHttpServletRequest(),
                        new MockHttpServletResponse()))
                .isInstanceOf(ApiException.class)
                .satisfies(exception -> {
                    ApiException apiException = (ApiException) exception;
                    assertThat(apiException.status().value()).isEqualTo(401);
                    assertThat(apiException.errorCode()).isEqualTo("LOGIN_FAILED");
                    assertThat(apiException.getMessage()).isEqualTo("姓名或 PIN 不正确。");
                });
    }

    private UserRow activeUser(long id, String displayName, String pin) {
        return new UserRow(
                id,
                displayName,
                passwordEncoder.encode(pin),
                "校史馆",
                "USER",
                "ACTIVE");
    }
}
