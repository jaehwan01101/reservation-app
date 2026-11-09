package com.eksproject.reservation;

import com.eksproject.reservation.ops.OriginVerifyProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "app.origin-verify.enabled=true",
        "app.origin-verify.secret=test-secret"
})
@AutoConfigureMockMvc
@ActiveProfiles("local")
class OriginVerifyTest {

    @Autowired
    MockMvc mvc;

    @Test
    void 헤더가_없거나_틀리면_403() throws Exception {
        mvc.perform(get("/api/performances")).andExpect(status().isForbidden());
        mvc.perform(get("/api/performances").header("X-Origin-Verify", "wrong"))
                .andExpect(status().isForbidden());
    }

    @Test
    void 헤더가_맞으면_통과() throws Exception {
        mvc.perform(get("/api/performances").header("X-Origin-Verify", "test-secret"))
                .andExpect(status().isOk());
    }

    @Test
    void 헬스체크는_헤더_없이도_정상() throws Exception {
        // ALB 헬스체크는 CloudFront 를 거치지 않으므로 /api/** 밖의 경로는 검증 대상이 아니어야 한다.
        mvc.perform(get("/livez")).andExpect(status().isOk());
    }

    @Test
    void 켜져_있는데_시크릿이_비어_있으면_기동_실패() {
        assertThatThrownBy(() -> new OriginVerifyProperties(true, "X-Origin-Verify", ""))
                .isInstanceOf(IllegalStateException.class);
    }
}
