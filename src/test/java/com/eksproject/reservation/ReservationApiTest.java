package com.eksproject.reservation;

import com.eksproject.reservation.common.SeatUnavailableException;
import com.eksproject.reservation.reservation.ReservationService;
import com.eksproject.reservation.seat.Seat;
import com.eksproject.reservation.seat.SeatRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
class ReservationApiTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    SeatRepository seats;

    @Autowired
    ReservationService reservationService;

    // 테스트끼리 좌석이 겹치지 않도록 공연별로 다른 좌석 사용
    private Seat seat(long performanceId, int index) {
        return seats.findByPerformanceIdOrderByIdAsc(performanceId).get(index);
    }

    @Test
    void 공연_목록과_좌석을_조회한다() throws Exception {
        mvc.perform(get("/api/performances"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)));

        mvc.perform(get("/api/performances/1/seats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(50)))
                .andExpect(jsonPath("$[0].seatNo").value("A1"));

        mvc.perform(get("/api/performances/999/seats"))
                .andExpect(status().isNotFound());
    }

    @Test
    void 예약하고_중복예약은_409_취소하면_다시_예약가능() throws Exception {
        long seatId = seat(1, 0).getId();
        String body = "{\"seatId\":" + seatId + ",\"customerName\":\"홍길동\"}";

        String location = mvc.perform(post("/api/reservations").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.seatNo").value("A1"))
                .andReturn().getResponse().getHeader("Location");

        mvc.perform(post("/api/reservations").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict());

        mvc.perform(get(location))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customerName").value("홍길동"));

        mvc.perform(delete(location))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        mvc.perform(delete(location))
                .andExpect(status().isConflict());

        mvc.perform(post("/api/reservations").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
    }

    @Test
    void 잘못된_요청은_400() throws Exception {
        mvc.perform(post("/api/reservations").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"seatId\":1,\"customerName\":\"  \"}"))
                .andExpect(status().isBadRequest());

        mvc.perform(get("/api/reservations/NOTEXIST"))
                .andExpect(status().isNotFound());
    }

    @Test
    void 같은_좌석에_동시에_예약하면_하나만_성공한다() throws Exception {
        long seatId = seat(2, 0).getId();
        int threads = 10;

        List<Callable<Boolean>> tasks = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            String name = "user" + i;
            tasks.add(() -> {
                try {
                    reservationService.reserve(seatId, name);
                    return true;
                } catch (SeatUnavailableException e) {
                    return false;
                }
            });
        }

        int success = 0;
        try (ExecutorService pool = Executors.newFixedThreadPool(threads)) {
            for (Future<Boolean> f : pool.invokeAll(tasks)) {
                if (f.get()) {
                    success++;
                }
            }
        }
        assertThat(success).isEqualTo(1);
    }

    @Test
    void 헬스_프로브_경로가_앱포트에_노출된다() throws Exception {
        mvc.perform(get("/livez")).andExpect(status().isOk());
        mvc.perform(get("/readyz")).andExpect(status().isOk());
    }
}
