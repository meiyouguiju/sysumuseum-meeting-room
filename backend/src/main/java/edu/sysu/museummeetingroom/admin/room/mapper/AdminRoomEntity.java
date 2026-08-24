package edu.sysu.museummeetingroom.admin.room.mapper;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AdminRoomEntity {

    private Long id;
    private String name;
    private String location;
    private Integer capacity;
    private String facilitiesText;
    private String usageNotice;
    private String status;
    private Integer sortOrder;
    private Long actorUserId;
}
