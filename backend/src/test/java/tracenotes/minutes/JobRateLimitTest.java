package tracenotes.minutes;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** SPEC S4: 작업 생성은 IP당 시간당 20회, 넘으면 429. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(PreviewRateLimitTest.FixedClockConfig.class)
class JobRateLimitTest {

    @Autowired
    MockMvc mvc;

    @Test
    void jobCreationOverLimitReturns429() throws Exception {
        for (int i = 0; i < 20; i++) {
            mvc.perform(request("/api/jobs", "192.0.2.60")).andExpect(status().isCreated());
        }

        mvc.perform(request("/api/jobs", "192.0.2.60"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("TOO_MANY_REQUESTS"));
        mvc.perform(request("/api/jobs", "192.0.2.61")).andExpect(status().isCreated());
    }

    static MockHttpServletRequestBuilder request(String path, String remoteAddr) {
        return post(path)
                .with(r -> { r.setRemoteAddr(remoteAddr); return r; })
                .contentType(MediaType.APPLICATION_JSON)
                .characterEncoding("UTF-8")
                .content("{\"text\":\"김민수: 안녕하세요\"}");
    }
}
