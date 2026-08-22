package edu.sysu.museummeetingroom.booking.idempotency;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.sysu.museummeetingroom.booking.command.CreateBookingCommand;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.stereotype.Component;

@Component
public class CreateBookingRequestHasher {

    private static final DateTimeFormatter LOCAL_DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    private final ObjectMapper objectMapper;

    public CreateBookingRequestHasher(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public byte[] hash(CreateBookingCommand command) {
        Map<String, Object> canonicalFields = new TreeMap<>();
        canonicalFields.put("attendeeCount", command.attendeeCount());
        canonicalFields.put("description", command.description());
        canonicalFields.put("endTime", format(command.endTime()));
        canonicalFields.put("participantsText", command.participantsText());
        canonicalFields.put("roomId", command.roomId());
        canonicalFields.put("startTime", format(command.startTime()));
        canonicalFields.put("subject", command.subject());
        return sha256(writeCanonicalJson(canonicalFields));
    }

    private String format(LocalDateTime value) {
        return value == null ? null : LOCAL_DATE_TIME_FORMATTER.format(value);
    }

    private String writeCanonicalJson(Map<String, Object> canonicalFields) {
        try {
            return objectMapper.writeValueAsString(canonicalFields);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("无法序列化规范化预约命令", exception);
        }
    }

    private byte[] sha256(String canonicalJson) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(canonicalJson.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("当前运行环境不支持SHA-256", exception);
        }
    }
}
