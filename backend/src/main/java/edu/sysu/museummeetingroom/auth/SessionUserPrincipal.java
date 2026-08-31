package edu.sysu.museummeetingroom.auth;

import java.io.Serializable;

public record SessionUserPrincipal(long userId) implements Serializable {
    private static final long serialVersionUID = 1L;
}
