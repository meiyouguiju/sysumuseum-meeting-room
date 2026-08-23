package edu.sysu.museummeetingroom.booking.idempotency;

import edu.sysu.museummeetingroom.common.exception.ApiException;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class IdempotencyKeyValidator {

    private static final Pattern IDEMPOTENCY_KEY_PATTERN = Pattern.compile("[A-Za-z0-9._:-]{1,128}");

    public String requireValid(String idempotencyKey) {
        if (idempotencyKey == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "IDEMPOTENCY_KEY_REQUIRED", "缺少Idempotency-Key。" );
        }
        if (!IDEMPOTENCY_KEY_PATTERN.matcher(idempotencyKey).matches()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "IDEMPOTENCY_KEY_INVALID", "Idempotency-Key格式不合法。" );
        }
        return idempotencyKey;
    }
}
