package tracenotes;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ClassPathResource;

/**
 * H2 파일 DB는 기본 설정에서 SQL 오류와 실패한 값(곧 회의 스크립트 본문)을 trace.db 파일에 남긴다.
 * 이 파일은 보관 기간 삭제 대상이 아니므로 파일 추적을 끈다 (ADR 0005).
 */
class H2TraceFileDisabledTest {

    @Test
    void fileDatasourceDisablesTraceFile() {
        YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(new ClassPathResource("application.yml"));

        String url = yaml.getObject().getProperty("spring.datasource.url");

        assertThat(url).startsWith("jdbc:h2:file:").contains(";TRACE_LEVEL_FILE=0");
    }
}
