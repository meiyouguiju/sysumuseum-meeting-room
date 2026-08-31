package edu.sysu.museummeetingroom.auth;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.session.web.http.CookieSerializer;
import org.springframework.session.web.http.DefaultCookieSerializer;

@Configuration
@Profile("!local")
public class SessionCookieConfiguration {

    @Bean
    CookieSerializer cookieSerializer() {
        DefaultCookieSerializer serializer = new DefaultCookieSerializer();
        serializer.setCookieName("MUSEUM_SESSION");
        serializer.setUseHttpOnlyCookie(true);
        serializer.setSameSite("Lax");
        serializer.setCookieMaxAge(60 * 60 * 24 * 30);
        // Leave useSecureCookie unset so Spring Session follows request.isSecure().
        // Forwarded headers make Cloudflare HTTPS secure while direct LAN HTTP remains usable.
        return serializer;
    }
}
