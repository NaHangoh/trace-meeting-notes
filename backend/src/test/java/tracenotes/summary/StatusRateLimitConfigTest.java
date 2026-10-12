package tracenotes.summary;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** 조회 상한은 app.rate-limit.status-per-minute 설정값을 따른다 (미리보기·작업 생성 설정과 별개). */
@SpringBootTest(properties = {
        "app.rate-limit.status-per-minute=2",
        "app.rate-limit.preview-per-minute=1",
        "app.rate-limit.job-per-hour=1"
})
@AutoConfigureMockMvc
@Import(RetryRateLimitTest.FixedClockConfig.class)
class StatusRateLimitConfigTest {

    @Autowired
    MockMvc mvc;

    private static MockHttpServletRequestBuilder statusRequest(String ip) {
        return get("/api/jobs/" + UUID.randomUUID()).with(r -> { r.setRemoteAddr(ip); return r; });
    }

    @Test
    void statusLimitUsesItsOwnSetting() throws Exception {
        String ip = "192.0.2.96";
        mvc.perform(statusRequest(ip)).andExpect(status().isNotFound());
        mvc.perform(statusRequest(ip)).andExpect(status().isNotFound());

        mvc.perform(statusRequest(ip)).andExpect(status().isTooManyRequests());
    }
}
