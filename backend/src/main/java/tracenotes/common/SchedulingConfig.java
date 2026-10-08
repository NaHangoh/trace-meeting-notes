package tracenotes.common;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** 스케줄 작업(보관 기간 삭제)을 켠다. */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
