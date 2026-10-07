package tracenotes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.core.env.Environment;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class H2ConsoleDisabledTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    Environment environment;

    @Test
    void consoleIsDisabledInConfiguration() {
        assertThat(environment.getProperty("spring.h2.console.enabled", Boolean.class)).isFalse();
    }

    @Test
    void consolePathReturns404() throws Exception {
        mockMvc.perform(get("/h2-console")).andExpect(status().isNotFound());
        mockMvc.perform(get("/h2-console/")).andExpect(status().isNotFound());
    }
}
