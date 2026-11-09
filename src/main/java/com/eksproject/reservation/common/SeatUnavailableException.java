package com.eksproject.reservation.common;

public class SeatUnavailableException extends RuntimeException {

    public SeatUnavailableException(String seatNo) {
        super("이미 예약된 좌석입니다: " + seatNo);
    }
}
