package tracenotes.common;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.logging.LogLevel;
import org.springframework.boot.logging.LoggingSystem;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.servlet.DispatcherServlet;

/**
 * DispatcherServlet은 DEBUG에서 요청 URI를 로그에 남긴다. URI에는 작업 ID(곧 열람 권한)가 들어가므로
 * application.yml에서 이 로거를 INFO로 고정한다 (SPEC F2 알려진 한계). org.springframework.web을 DEBUG로 켜도
 * 그대로인지 확인한다. MockMvc는 이 로거로 요청 줄을 찍지 않아 로그 출력으로는 확인할 수 없다.
 */
@SpringBootTest(properties = "logging.level.org.springframework.web=DEBUG")
class DispatcherServletLogLevelTest {

    @Autowired
    LoggingSystem loggingSystem;

    @Test
    void dispatcherServletStaysInfoWhenSpringWebIsDebug() {
        assertThat(loggingSystem.getLoggerConfiguration("org.springframework.web").getEffectiveLevel())
                .isEqualTo(LogLevel.DEBUG);
        assertThat(loggingSystem.getLoggerConfiguration(DispatcherServlet.class.getName()).getEffectiveLevel())
                .isEqualTo(LogLevel.INFO);
    }
}
