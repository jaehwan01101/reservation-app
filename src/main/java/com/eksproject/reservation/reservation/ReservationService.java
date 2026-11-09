package com.eksproject.reservation.reservation;

import com.eksproject.reservation.common.NotFoundException;
import com.eksproject.reservation.common.SeatUnavailableException;
import com.eksproject.reservation.seat.Seat;
import com.eksproject.reservation.seat.SeatRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;

@Service
public class ReservationService {

    // 헷갈리는 문자(0/O, 1/I) 제외
    private static final char[] CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();
    private static final int CODE_LENGTH = 8;

    private final SecureRandom random = new SecureRandom();
    private final SeatRepository seats;
    private final ReservationRepository reservations;
    private final Counter createdCounter;
    private final Counter cancelledCounter;
    private final Counter conflictCounter;

    public ReservationService(SeatRepository seats, ReservationRepository reservations, MeterRegistry registry) {
        this.seats = seats;
        this.reservations = reservations;
        // Prometheus: reservation_events_total{result="created|cancelled|conflict"}
        this.createdCounter = registry.counter("reservation.events", "result", "created");
        this.cancelledCounter = registry.counter("reservation.events", "result", "cancelled");
        this.conflictCounter = registry.counter("reservation.events", "result", "conflict");
    }

    @Transactional
    public ReservationResponse reserve(Long seatId, String customerName) {
        Seat seat = seats.findByIdForUpdate(seatId)
                .orElseThrow(() -> new NotFoundException("좌석", seatId));
        if (!seat.isAvailable()) {
            conflictCounter.increment();
            throw new SeatUnavailableException(seat.getSeatNo());
        }
        seat.reserve();
        Reservation reservation = reservations.save(Reservation.create(newCode(), seat, customerName));
        createdCounter.increment();
        return ReservationResponse.from(reservation);
    }

    @Transactional(readOnly = true)
    public ReservationResponse get(String code) {
        return ReservationResponse.from(find(code));
    }

    @Transactional
    public ReservationResponse cancel(String code) {
        Reservation reservation = find(code);
        reservation.cancel();
        cancelledCounter.increment();
        return ReservationResponse.from(reservation);
    }

    private Reservation find(String code) {
        return reservations.findByCode(code.toUpperCase())
                .orElseThrow(() -> new NotFoundException("예약", code));
    }

    private String newCode() {
        char[] code = new char[CODE_LENGTH];
        for (int i = 0; i < CODE_LENGTH; i++) {
            code[i] = CODE_CHARS[random.nextInt(CODE_CHARS.length)];
        }
        return new String(code);
    }
}
