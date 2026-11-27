-- 정확성 SLO(같은 좌석 중복 예약 0건)를 DB 가 보장한다.
-- 앱은 좌석 행 잠금(SELECT ... FOR UPDATE)으로 이미 막고 있다. 이 제약은 그 잠금에 구멍이 생겨도 중복을 물리적으로 막는 마지막 방어선이다.
-- 좌석당 CONFIRMED 예약은 1개만 허용한다. 취소(CANCELLED)된 예약은 제외해서 같은 좌석을 다시 예약할 수 있다.
-- 부분 인덱스(WHERE)는 H2 가 지원하지 않아 PostgreSQL 전용 위치(db/vendor/postgresql)에 둔다.
CREATE UNIQUE INDEX uk_reservation_active_seat ON reservation (seat_id) WHERE status = 'CONFIRMED';
