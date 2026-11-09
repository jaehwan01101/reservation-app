package com.eksproject.reservation.reservation;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
@RequestMapping("/api/reservations")
public class ReservationController {

    private final ReservationService service;

    public ReservationController(ReservationService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<ReservationResponse> reserve(@Valid @RequestBody ReserveRequest request) {
        ReservationResponse created = service.reserve(request.seatId(), request.customerName().strip());
        return ResponseEntity.created(URI.create("/api/reservations/" + created.code())).body(created);
    }

    @GetMapping("/{code}")
    public ReservationResponse get(@PathVariable String code) {
        return service.get(code);
    }

    @DeleteMapping("/{code}")
    public ReservationResponse cancel(@PathVariable String code) {
        return service.cancel(code);
    }

    public record ReserveRequest(
            @NotNull Long seatId,
            @NotBlank @Size(max = 50) String customerName) {
    }
}
