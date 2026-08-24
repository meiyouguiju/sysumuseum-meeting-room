package edu.sysu.museummeetingroom.admin.room.service;

import edu.sysu.museummeetingroom.admin.room.mapper.AdminRoomEntity;
import edu.sysu.museummeetingroom.admin.room.mapper.AdminRoomMapper;
import edu.sysu.museummeetingroom.admin.room.web.CreateRoomRequest;
import edu.sysu.museummeetingroom.admin.room.web.UpdateRoomRequest;
import edu.sysu.museummeetingroom.auth.CurrentUser;
import edu.sysu.museummeetingroom.auth.CurrentUserProvider;
import edu.sysu.museummeetingroom.common.exception.ApiException;
import edu.sysu.museummeetingroom.room.dto.RoomResponse;
import edu.sysu.museummeetingroom.room.mapper.MeetingRoomMapper;
import edu.sysu.museummeetingroom.room.mapper.RoomRow;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminRoomService {

    private static final long NO_EXCLUDED_ROOM = 0L;

    private final CurrentUserProvider currentUserProvider;
    private final AdminRoomMapper adminRoomMapper;
    private final MeetingRoomMapper meetingRoomMapper;

    @Transactional
    public RoomResponse create(CreateRoomRequest request) {
        CurrentUser currentUser = requireActiveAdmin();
        AdminRoomEntity room = new AdminRoomEntity();
        room.setName(requireText(request.name(), "会议室名称不能为空。"));
        room.setLocation(requireText(request.location(), "会议室位置不能为空。"));
        room.setCapacity(request.capacity());
        room.setFacilitiesText(normalizeOptionalText(request.facilitiesText()));
        room.setUsageNotice(normalizeOptionalText(request.usageNotice()));
        room.setStatus("ENABLED");
        room.setSortOrder(request.sortOrder() == null ? 0 : request.sortOrder());
        room.setActorUserId(currentUser.userId());
        requireUniqueName(room.getName(), NO_EXCLUDED_ROOM);
        try {
            adminRoomMapper.insert(room);
        } catch (DuplicateKeyException exception) {
            throw nameConflict();
        }
        return toResponse(requireRoom(room.getId()));
    }

    @Transactional
    public RoomResponse update(long roomId, UpdateRoomRequest request) {
        CurrentUser currentUser = requireActiveAdmin();
        RoomRow existing = requireRoom(roomId);
        if (!request.hasAnyField()) {
            throw validationError("至少提交一个需要修改的字段。");
        }
        AdminRoomEntity updated = merge(existing, request, currentUser.userId());
        requireUniqueName(updated.getName(), roomId);
        try {
            adminRoomMapper.update(updated);
        } catch (DuplicateKeyException exception) {
            throw nameConflict();
        }
        return toResponse(requireRoom(roomId));
    }

    @Transactional
    public RoomResponse enable(long roomId) {
        CurrentUser currentUser = requireActiveAdmin();
        RoomRow room = requireRoom(roomId);
        requireUniqueName(room.name(), roomId);
        adminRoomMapper.updateStatus(roomId, "ENABLED", currentUser.userId());
        return toResponse(requireRoom(roomId));
    }

    @Transactional
    public RoomResponse disable(long roomId) {
        CurrentUser currentUser = requireActiveAdmin();
        requireRoom(roomId);
        adminRoomMapper.updateStatus(roomId, "DISABLED", currentUser.userId());
        return toResponse(requireRoom(roomId));
    }

    private CurrentUser requireActiveAdmin() {
        CurrentUser currentUser = currentUserProvider.currentUser();
        if (!"ADMIN".equals(currentUser.roleCode())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "仅管理员可管理会议室。");
        }
        if (!"ACTIVE".equals(currentUser.userStatus())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "当前用户不可用。");
        }
        return currentUser;
    }

    private RoomRow requireRoom(long roomId) {
        RoomRow room = meetingRoomMapper.findById(roomId);
        if (room == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "MEETING_ROOM_NOT_FOUND", "会议室不存在。");
        }
        return room;
    }

    private AdminRoomEntity merge(RoomRow existing, UpdateRoomRequest request, long actorUserId) {
        AdminRoomEntity room = new AdminRoomEntity();
        room.setId(existing.id());
        room.setName(request.isNamePresent()
                ? requireText(request.name(), "会议室名称不能为空。") : existing.name());
        room.setLocation(request.isLocationPresent()
                ? requireText(request.location(), "会议室位置不能为空。") : existing.location());
        if (request.isCapacityPresent() && request.capacity() == null) {
            throw validationError("会议室容量不能为空。");
        }
        if (request.isSortOrderPresent() && request.sortOrder() == null) {
            throw validationError("会议室排序不能为空。");
        }
        room.setCapacity(request.isCapacityPresent() ? request.capacity() : existing.capacity());
        room.setFacilitiesText(request.isFacilitiesTextPresent()
                ? normalizeOptionalText(request.facilitiesText()) : existing.facilitiesText());
        room.setUsageNotice(request.isUsageNoticePresent()
                ? normalizeOptionalText(request.usageNotice()) : existing.usageNotice());
        room.setSortOrder(request.isSortOrderPresent() ? request.sortOrder() : existing.sortOrder());
        room.setActorUserId(actorUserId);
        return room;
    }

    private String requireText(String value, String message) {
        if (value == null || value.strip().isEmpty()) {
            throw validationError(message);
        }
        return value.strip();
    }

    private String normalizeOptionalText(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.strip();
        return normalized.isEmpty() ? null : normalized;
    }

    private void requireUniqueName(String name, long excludedRoomId) {
        if (adminRoomMapper.countByNameExcludingId(name, excludedRoomId) > 0) {
            throw nameConflict();
        }
    }

    private ApiException nameConflict() {
        return new ApiException(HttpStatus.CONFLICT, "MEETING_ROOM_NAME_CONFLICT", "会议室名称已存在。");
    }

    private ApiException validationError(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, "REQUEST_VALIDATION_ERROR", message);
    }

    private RoomResponse toResponse(RoomRow room) {
        return new RoomResponse(room.id(), room.name(), room.location(), room.capacity(), room.facilitiesText(),
                room.usageNotice(), room.status(), room.sortOrder());
    }
}
