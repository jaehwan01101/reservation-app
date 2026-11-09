package com.eksproject.reservation.common;

public class ReservationAlreadyCancelledException extends RuntimeException {

    public ReservationAlreadyCancelledException(String code) {
        super("이미 취소된 예약입니다: " + code);
    }
}
