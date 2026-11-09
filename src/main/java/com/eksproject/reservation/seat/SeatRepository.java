package com.eksproject.reservation.seat;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SeatRepository extends JpaRepository<Seat, Long> {

    List<Seat> findByPerformanceIdOrderByIdAsc(Long performanceId);

    long countByPerformanceIdAndStatus(Long performanceId, SeatStatus status);

    // SELECT ... FOR UPDATE: 같은 좌석에 동시 예약이 들어와도 한 트랜잭션만 통과
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Seat s where s.id = :id")
    Optional<Seat> findByIdForUpdate(@Param("id") Long id);
}
