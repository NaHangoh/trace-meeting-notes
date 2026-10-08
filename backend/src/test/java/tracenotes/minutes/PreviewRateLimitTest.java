package tracenotes.minutes;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** SPEC F1: 같은 IP에서 1분 안에 미리보기 61번째 호출은 429. IP는 getRemoteAddr만 쓴다. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(PreviewRateLimitTest.FixedClockConfig.class)
class PreviewRateLimitTest {

    /** 실제 시계를 쓰면 60회 요청 도중 정각 분이 지나 카운터가 초기화될 수 있다. 시계를 고정한다. */
    @TestConfiguration
    static class FixedClockConfig {

        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(Instant.parse("2026-10-08T09:00:30Z"), ZoneOffset.UTC);
        }
    }

    @Autowired
    MockMvc mvc;

    @Test
    void request61InSameMinuteReturns429() throws Exception {
        for (int i = 0; i < 60; i++) {
            mvc.perform(preview("192.0.2.10")).andExpect(status().isOk());
        }

        mvc.perform(preview("192.0.2.10"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("TOO_MANY_REQUESTS"));
    }

    @Test
    void separateLimitPerIp() throws Exception {
        for (int i = 0; i < 60; i++) {
            mvc.perform(preview("192.0.2.20")).andExpect(status().isOk());
        }
        mvc.perform(preview("192.0.2.20")).andExpect(status().isTooManyRequests());

        mvc.perform(preview("192.0.2.21")).andExpect(status().isOk());
    }

    /** X-Forwarded-For는 클라이언트가 바꿀 수 있으므로 보지 않는다. */
    @Test
    void forwardedForHeaderIsIgnored() throws Exception {
        for (int i = 0; i < 60; i++) {
            mvc.perform(preview("192.0.2.30").header("X-Forwarded-For", "198.51.100." + i)).andExpect(status().isOk());
        }

        mvc.perform(preview("192.0.2.30").header("X-Forwarded-For", "198.51.100.99"))
                .andExpect(status().isTooManyRequests());
    }

    /** CORS 사전 요청(OPTIONS)은 세지 않는다. */
    @Test
    void preflightIsNotCounted() throws Exception {
        for (int i = 0; i < 70; i++) {
            mvc.perform(options("/api/preview")
                    .with(r -> { r.setRemoteAddr("192.0.2.40"); return r; })
                    .header("Origin", "http://localhost:5173")
                    .header("Access-Control-Request-Method", "POST"));
        }

        mvc.perform(preview("192.0.2.40")).andExpect(status().isOk());
    }

    private static MockHttpServletRequestBuilder preview(String remoteAddr) {
        return post("/api/preview")
                .with(r -> { r.setRemoteAddr(remoteAddr); return r; })
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"text\":\"김민수: 안녕하세요\"}");
    }
}
