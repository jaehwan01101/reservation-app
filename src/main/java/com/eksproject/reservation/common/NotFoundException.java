package com.eksproject.reservation.common;

public class NotFoundException extends RuntimeException {

    public NotFoundException(String resource, Object id) {
        super(resource + "을(를) 찾을 수 없습니다: " + id);
    }
}
