package edu.sysu.museummeetingroom.booking.mapper;

import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class BookingEntity {

    private Long id;
    private String bookingNo;
    private Long roomId;
    private Long organizerUserId;
    private String organizerNameSnapshot;
    private String subject;
    private Integer attendeeCount;
    private String participantsText;
    private String description;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Long lastModifiedByUserId;
    private LocalDateTime occurredAt;

}
