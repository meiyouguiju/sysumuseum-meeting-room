package edu.sysu.museummeetingroom.booking.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Delete;

@Mapper
public interface BookingSlotMapper {

    @Insert("""
            <script>
            INSERT INTO booking_slot (booking_id, room_id, slot_start, occupancy_state)
            VALUES
            <foreach collection="slots" item="slot" separator=",">
                (#{slot.bookingId}, #{slot.roomId}, #{slot.slotStart}, 'ACTIVE')
            </foreach>
            </script>
            """)
    int insertBatch(@Param("slots") List<BookingSlotEntity> slots);

    @Delete("DELETE FROM booking_slot WHERE booking_id = #{bookingId}")
    int deleteByBookingId(@Param("bookingId") long bookingId);

    @org.apache.ibatis.annotations.Update("UPDATE booking_slot SET occupancy_state='CANCELLED_CURRENT_SLOT_HOLD' WHERE booking_id=#{bookingId} AND slot_start=#{slotStart}")
    int holdCurrentSlot(@Param("bookingId") long bookingId, @Param("slotStart") java.time.LocalDateTime slotStart);

    @Delete("DELETE FROM booking_slot WHERE booking_id=#{bookingId} AND slot_start > #{slotStart}")
    int deleteFutureSlots(@Param("bookingId") long bookingId, @Param("slotStart") java.time.LocalDateTime slotStart);
}
