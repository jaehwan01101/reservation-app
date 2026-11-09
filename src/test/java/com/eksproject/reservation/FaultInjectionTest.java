package com.eksproject.reservation;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "app.fault.error-rate=1.0")
@AutoConfigureMockMvc
@ActiveProfiles("local")
class FaultInjectionTest {

    @Autowired
    MockMvc mvc;

    @Test
    void 장애주입시_api는_500_프로브는_정상() throws Exception {
        mvc.perform(get("/api/performances")).andExpect(status().isInternalServerError());
        // 프로브까지 죽으면 canary 분석 전에 K8s 가 Pod 를 재시작해버리므로 /api/** 만 대상
        mvc.perform(get("/livez")).andExpect(status().isOk());
    }
}
