package com.eksproject.reservation.reservation;

import com.eksproject.reservation.common.ReservationAlreadyCancelledException;
import com.eksproject.reservation.seat.Seat;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "reservation")
public class Reservation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String code;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "seat_id")
    private Seat seat;

    @Column(name = "customer_name", nullable = false)
    private String customerName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReservationStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    protected Reservation() {
    }

    static Reservation create(String code, Seat seat, String customerName) {
        Reservation r = new Reservation();
        r.code = code;
        r.seat = seat;
        r.customerName = customerName;
        r.status = ReservationStatus.CONFIRMED;
        r.createdAt = Instant.now();
        return r;
    }

    void cancel() {
        if (status == ReservationStatus.CANCELLED) {
            throw new ReservationAlreadyCancelledException(code);
        }
        status = ReservationStatus.CANCELLED;
        cancelledAt = Instant.now();
        seat.release();
    }

    public Long getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public Seat getSeat() {
        return seat;
    }

    public String getCustomerName() {
        return customerName;
    }

    public ReservationStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getCancelledAt() {
        return cancelledAt;
    }
}
