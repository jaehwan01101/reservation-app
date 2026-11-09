package com.eksproject.reservation.performance;

import com.eksproject.reservation.common.NotFoundException;
import com.eksproject.reservation.seat.SeatRepository;
import com.eksproject.reservation.seat.SeatStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/performances")
@Transactional(readOnly = true)
public class PerformanceController {

    private final PerformanceRepository performances;
    private final SeatRepository seats;

    public PerformanceController(PerformanceRepository performances, SeatRepository seats) {
        this.performances = performances;
        this.seats = seats;
    }

    @GetMapping
    public List<PerformanceResponse> list() {
        return performances.findAllByOrderByStartsAtAsc().stream()
                .map(p -> new PerformanceResponse(p.getId(), p.getTitle(), p.getVenue(), p.getStartsAt(), p.getPrice(),
                        seats.countByPerformanceIdAndStatus(p.getId(), SeatStatus.AVAILABLE)))
                .toList();
    }

    @GetMapping("/{id}/seats")
    public List<SeatResponse> seats(@PathVariable Long id) {
        if (!performances.existsById(id)) {
            throw new NotFoundException("공연", id);
        }
        return seats.findByPerformanceIdOrderByIdAsc(id).stream()
                .map(s -> new SeatResponse(s.getId(), s.getSeatNo(), s.getStatus()))
                .toList();
    }

    public record PerformanceResponse(Long id, String title, String venue, Instant startsAt, int price,
                                      long availableSeats) {
    }

    public record SeatResponse(Long id, String seatNo, SeatStatus status) {
    }
}
