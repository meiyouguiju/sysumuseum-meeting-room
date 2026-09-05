package edu.sysu.museummeetingroom.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/me")
@Profile("!local")
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@RequiredArgsConstructor
public class CurrentUserPinController {

    private final AuthService authService;

    @PatchMapping("/pin")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePin(
            @Valid @RequestBody ChangePinRequest request,
            HttpServletRequest servletRequest,
            HttpServletResponse servletResponse) {
        authService.changeOwnPin(request);
        authService.logout(servletRequest, servletResponse);
    }
}
