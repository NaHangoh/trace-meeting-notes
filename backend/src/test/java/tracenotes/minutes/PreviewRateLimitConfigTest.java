package tracenotes.minutes;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/** 미리보기 상한은 app.rate-limit.preview-per-minute 설정값을 따른다. */
@SpringBootTest(properties = "app.rate-limit.preview-per-minute=2")
@AutoConfigureMockMvc
@Import(PreviewRateLimitTest.FixedClockConfig.class)
class PreviewRateLimitConfigTest {

    @Autowired
    MockMvc mvc;

    @Test
    void previewLimitFollowsConfig() throws Exception {
        for (int i = 0; i < 2; i++) {
            mvc.perform(preview()).andExpect(status().isOk());
        }

        mvc.perform(preview()).andExpect(status().isTooManyRequests());
    }

    private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder preview() {
        return post("/api/preview")
                .with(r -> { r.setRemoteAddr("192.0.2.50"); return r; })
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"text\":\"김민수: 안녕하세요\"}");
    }
}
