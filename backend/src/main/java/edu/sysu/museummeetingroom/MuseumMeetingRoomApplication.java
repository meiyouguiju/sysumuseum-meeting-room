package edu.sysu.museummeetingroom;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;

@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
public class MuseumMeetingRoomApplication {

    public static void main(String[] args) {
        SpringApplication.run(MuseumMeetingRoomApplication.class, args);
    }
}
