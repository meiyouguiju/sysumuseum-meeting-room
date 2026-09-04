package edu.sysu.museummeetingroom.user.bootstrap;

import java.util.List;
import java.util.Set;

public final class UserBootstrapLegacyUsers {

    public static final List<String> DISPLAY_NAMES = List.of("周绅", "张诚", "胡家健");

    public static final Set<String> DISPLAY_NAME_SET = Set.copyOf(DISPLAY_NAMES);

    private UserBootstrapLegacyUsers() {
    }
}
