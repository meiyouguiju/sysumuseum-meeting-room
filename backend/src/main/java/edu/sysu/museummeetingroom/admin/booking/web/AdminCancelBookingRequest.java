package edu.sysu.museummeetingroom.admin.booking.web;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record AdminCancelBookingRequest(@NotNull @Positive Integer version, @Size(max = 500) String reason) {
}
