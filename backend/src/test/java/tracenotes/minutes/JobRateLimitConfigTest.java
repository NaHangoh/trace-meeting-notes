package tracenotes.minutes;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static tracenotes.minutes.JobRateLimitTest.request;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

/** 작업 생성 상한은 미리보기와 다른 설정값(app.rate-limit.job-per-hour)을 따르고, 서로 영향을 주지 않는다. */
@SpringBootTest(properties = {
        "app.rate-limit.job-per-hour=2",
        "app.rate-limit.preview-per-minute=1"
})
@AutoConfigureMockMvc
@Import(PreviewRateLimitTest.FixedClockConfig.class)
class JobRateLimitConfigTest {

    @Autowired
    MockMvc mvc;

    @Test
    void jobLimitUsesSeparateConfig() throws Exception {
        String ip = "192.0.2.70";
        mvc.perform(request("/api/preview", ip)).andExpect(status().isOk());
        mvc.perform(request("/api/preview", ip)).andExpect(status().isTooManyRequests());

        mvc.perform(request("/api/jobs", ip)).andExpect(status().isCreated());
        mvc.perform(request("/api/jobs", ip)).andExpect(status().isCreated());
        mvc.perform(request("/api/jobs", ip)).andExpect(status().isTooManyRequests());
    }
}
