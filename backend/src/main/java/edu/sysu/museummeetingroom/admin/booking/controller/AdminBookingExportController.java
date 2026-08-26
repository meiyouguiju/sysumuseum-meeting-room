package edu.sysu.museummeetingroom.admin.booking.controller;

import edu.sysu.museummeetingroom.admin.booking.service.AdminBookingExportService;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class AdminBookingExportController {

    private static final MediaType CSV_MEDIA_TYPE = MediaType.parseMediaType("text/csv;charset=UTF-8");

    private final AdminBookingExportService adminBookingExportService;

    @GetMapping("/api/v1/admin/bookings/export")
    public ResponseEntity<byte[]> export(
            @RequestParam(required = false) String organizerKeyword,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate) {
        byte[] csv = adminBookingExportService.export(organizerKeyword, date, status, fromDate, toDate);
        ContentDisposition contentDisposition = ContentDisposition.attachment()
                .filename("booking-records.csv")
                .build();
        return ResponseEntity.ok()
                .contentType(CSV_MEDIA_TYPE)
                .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition.toString())
                .body(csv);
    }
}
