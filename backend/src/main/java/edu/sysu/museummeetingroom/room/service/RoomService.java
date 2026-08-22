package edu.sysu.museummeetingroom.room.service;

import edu.sysu.museummeetingroom.room.dto.RoomResponse;
import edu.sysu.museummeetingroom.room.mapper.MeetingRoomMapper;
import edu.sysu.museummeetingroom.room.mapper.RoomRow;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RoomService {

    private final MeetingRoomMapper meetingRoomMapper;

    public List<RoomResponse> findAll() {
        return meetingRoomMapper.findAllOrdered().stream().map(this::toResponse).toList();
    }
    private RoomResponse toResponse(RoomRow room) {
        return new RoomResponse(room.id(), room.name(), room.location(), room.capacity(), room.facilitiesText(),
                room.usageNotice(), room.status(), room.sortOrder());
    }
}
