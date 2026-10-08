package tracenotes.common;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 시간에 따라 바뀌는 동작(요청 수 제한, 보관 기간)이 같은 시계를 쓰게 한다. 테스트는 시계를 바꿔 끼운다. */
@Configuration
public class ClockConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
