package com.eksproject.reservation.reservation;

import java.time.Instant;

public record ReservationResponse(
        String code,
        Long performanceId,
        String performanceTitle,
        Instant performanceStartsAt,
        String seatNo,
        String customerName,
        ReservationStatus status,
        Instant createdAt,
        Instant cancelledAt) {

    static ReservationResponse from(Reservation r) {
        var seat = r.getSeat();
        var performance = seat.getPerformance();
        return new ReservationResponse(r.getCode(), performance.getId(), performance.getTitle(),
                performance.getStartsAt(), seat.getSeatNo(), r.getCustomerName(), r.getStatus(),
                r.getCreatedAt(), r.getCancelledAt());
    }
}
