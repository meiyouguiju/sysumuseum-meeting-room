package edu.sysu.museummeetingroom.auth;

import edu.sysu.museummeetingroom.common.exception.ApiException;
import edu.sysu.museummeetingroom.user.dto.CurrentUserResponse;
import edu.sysu.museummeetingroom.user.mapper.SysUserMapper;
import edu.sysu.museummeetingroom.user.mapper.UserRow;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile("!local")
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final SysUserMapper sysUserMapper;
    private final PasswordEncoder passwordEncoder;
    private final SecurityContextRepository securityContextRepository;
    private final CurrentUserProvider currentUserProvider;

    public CurrentUserResponse login(LoginRequest request, HttpServletRequest servletRequest, HttpServletResponse servletResponse) {
        String displayName = request.name().trim();
        List<UserRow> candidates = sysUserMapper.findActiveByDisplayName(displayName);
        List<UserRow> matches = candidates.stream()
                .filter(candidate -> passwordEncoder.matches(request.pin(), candidate.pinHash()))
                .toList();
        if (matches.size() != 1) {
            if (matches.size() > 1) {
                log.error("Multiple active users matched the same name and PIN, displayName={}", displayName);
            }
            throw new ApiException(HttpStatus.UNAUTHORIZED, "LOGIN_FAILED", "姓名或 PIN 不正确。");
        }

        UserRow user = matches.getFirst();
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                new SessionUserPrincipal(user.id()),
                null,
                List.of(new SimpleGrantedAuthority("ROLE_" + user.roleCode())));
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, servletRequest, servletResponse);
        return new CurrentUserResponse(user.id(), user.displayName(), user.departmentName(), user.roleCode(), user.status());
    }

    public void logout(HttpServletRequest request, HttpServletResponse response) {
        var session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        SecurityContextHolder.clearContext();
        var sessionCookie = new jakarta.servlet.http.Cookie("MUSEUM_SESSION", "");
        sessionCookie.setPath(request.getContextPath().isEmpty() ? "/" : request.getContextPath());
        sessionCookie.setMaxAge(0);
        sessionCookie.setHttpOnly(true);
        sessionCookie.setSecure(request.isSecure());
        sessionCookie.setAttribute("SameSite", "Lax");
        response.addCookie(sessionCookie);
    }

    @Transactional
    public void changeOwnPin(ChangePinRequest request) {
        CurrentUser currentUser = currentUserProvider.currentUser();
        List<UserRow> sameNameUsers = sysUserMapper.findActiveByDisplayNameForUpdate(currentUser.displayName());
        UserRow user = sameNameUsers.stream()
                .filter(candidate -> currentUser.userId().equals(candidate.id()))
                .findFirst()
                .orElse(null);
        if (user == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "当前用户不可用。");
        }
        if (!passwordEncoder.matches(request.currentPin(), user.pinHash())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "CURRENT_PIN_INCORRECT", "当前 PIN 不正确。");
        }
        if (passwordEncoder.matches(request.newPin(), user.pinHash())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PIN_UNCHANGED", "新 PIN 不能与当前 PIN 相同。");
        }
        if (hasSameNamePinConflict(sameNameUsers, user, request.newPin())) {
            throw new ApiException(HttpStatus.CONFLICT, "PIN_CONFLICT", "同名用户已使用该 PIN，请选择其他 PIN。");
        }

        String pinHash = passwordEncoder.encode(request.newPin());
        if (sysUserMapper.updateActivePin(user.id(), pinHash) != 1) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "当前用户不可用。");
        }
    }

    private boolean hasSameNamePinConflict(List<UserRow> sameNameUsers, UserRow currentUser, String newPin) {
        return sameNameUsers.stream()
                .filter(candidate -> !candidate.id().equals(currentUser.id()))
                .anyMatch(candidate -> passwordEncoder.matches(newPin, candidate.pinHash()));
    }
}
