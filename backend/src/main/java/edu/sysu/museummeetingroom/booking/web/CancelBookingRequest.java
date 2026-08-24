package edu.sysu.museummeetingroom.booking.web;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record CancelBookingRequest(@NotNull @Positive Integer version, @Size(max = 500) String reason) {
}
