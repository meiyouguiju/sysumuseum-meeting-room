package edu.sysu.museummeetingroom.user.bootstrap;

import java.nio.file.Path;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("user-bootstrap")
@ConditionalOnProperty(name = "user-bootstrap.enabled", havingValue = "true")
@RequiredArgsConstructor
public class UserBootstrapRunner implements ApplicationRunner {

    private final ConfigurableApplicationContext applicationContext;
    private final UserBootstrapFileParser userBootstrapFileParser;
    private final UserBootstrapService userBootstrapService;

    @Value("${user-bootstrap.file}")
    private String bootstrapFile;

    @Override
    public void run(ApplicationArguments args) {
        try {
            List<UserBootstrapAccount> accounts = userBootstrapFileParser.parse(Path.of(bootstrapFile));
            userBootstrapService.bootstrap(accounts);
        } finally {
            applicationContext.close();
        }
    }
}
