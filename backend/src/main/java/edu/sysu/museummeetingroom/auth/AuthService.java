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

@Service
@Profile("!local")
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final SysUserMapper sysUserMapper;
    private final PasswordEncoder passwordEncoder;
    private final SecurityContextRepository securityContextRepository;

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
}
