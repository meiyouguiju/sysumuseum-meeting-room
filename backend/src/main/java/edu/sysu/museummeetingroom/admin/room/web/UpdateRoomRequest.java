package edu.sysu.museummeetingroom.admin.room.web;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSetter;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public class UpdateRoomRequest {

    @Size(max = 120, message = "name长度不能超过120。")
    private String name;
    @Size(max = 200, message = "location长度不能超过200。")
    private String location;
    @Positive(message = "capacity必须为正整数。")
    @Max(value = 65535, message = "capacity超出允许范围。")
    private Integer capacity;
    private String facilitiesText;
    private String usageNotice;
    private Integer sortOrder;
    private boolean namePresent;
    private boolean locationPresent;
    private boolean capacityPresent;
    private boolean facilitiesTextPresent;
    private boolean usageNoticePresent;
    private boolean sortOrderPresent;

    public String name() {
        return name;
    }

    @JsonSetter
    public void setName(String name) {
        this.name = name;
        this.namePresent = true;
    }

    public String location() {
        return location;
    }

    @JsonSetter
    public void setLocation(String location) {
        this.location = location;
        this.locationPresent = true;
    }

    public Integer capacity() {
        return capacity;
    }

    @JsonSetter
    public void setCapacity(Integer capacity) {
        this.capacity = capacity;
        this.capacityPresent = true;
    }

    public String facilitiesText() {
        return facilitiesText;
    }

    @JsonSetter
    public void setFacilitiesText(String facilitiesText) {
        this.facilitiesText = facilitiesText;
        this.facilitiesTextPresent = true;
    }

    public String usageNotice() {
        return usageNotice;
    }

    @JsonSetter
    public void setUsageNotice(String usageNotice) {
        this.usageNotice = usageNotice;
        this.usageNoticePresent = true;
    }

    public Integer sortOrder() {
        return sortOrder;
    }

    @JsonSetter
    public void setSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
        this.sortOrderPresent = true;
    }

    @JsonIgnore
    public boolean isNamePresent() {
        return namePresent;
    }

    @JsonIgnore
    public boolean isLocationPresent() {
        return locationPresent;
    }

    @JsonIgnore
    public boolean isCapacityPresent() {
        return capacityPresent;
    }

    @JsonIgnore
    public boolean isFacilitiesTextPresent() {
        return facilitiesTextPresent;
    }

    @JsonIgnore
    public boolean isUsageNoticePresent() {
        return usageNoticePresent;
    }

    @JsonIgnore
    public boolean isSortOrderPresent() {
        return sortOrderPresent;
    }

    @JsonIgnore
    public boolean hasAnyField() {
        return namePresent || locationPresent || capacityPresent || facilitiesTextPresent
                || usageNoticePresent || sortOrderPresent;
    }
}
