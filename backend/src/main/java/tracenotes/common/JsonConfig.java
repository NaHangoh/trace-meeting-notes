package tracenotes.common;

import java.io.IOException;
import java.util.Map;
import org.springframework.boot.http.converter.autoconfigure.ServerHttpMessageConvertersCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.ResolvableType;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import tools.jackson.databind.json.JsonMapper;

/**
 * JSON 읽기 오류를 입력 없는 예외로 바꾼다.
 * Jackson 오류 메시지에는 입력 토큰(예: "Unrecognized token '...'")이 들어가고,
 * Spring 웹 DEBUG 로그가 그 메시지를 그대로 남기기 때문이다. 응답은 ApiExceptionHandler가 고정 문구로 만든다.
 *
 * 변환기 빈으로 등록하면 맨 앞 커스텀 변환기로 끼어들고(String 처리가 바뀜) 클라이언트 쪽에도 들어가므로,
 * 서버 커스터마이저에서 JSON 변환기 자리만 바꾼다. Boot 기본 커스터마이저보다 뒤에 실행되게 한다.
 */
@Configuration
public class JsonConfig {

    @Bean
    @Order(Ordered.LOWEST_PRECEDENCE)
    ServerHttpMessageConvertersCustomizer quietJsonConverterCustomizer(JsonMapper jsonMapper) {
        return builder -> builder.withJsonConverter(new QuietJsonConverter(jsonMapper));
    }

    static final class QuietJsonConverter extends JacksonJsonHttpMessageConverter {

        QuietJsonConverter(JsonMapper jsonMapper) {
            super(jsonMapper);
        }

        @Override
        public Object read(ResolvableType type, HttpInputMessage inputMessage, Map<String, Object> hints)
                throws IOException {
            try {
                return super.read(type, inputMessage, hints);
            } catch (HttpMessageNotReadableException e) {
                throw quiet(inputMessage);
            }
        }

        @Override
        protected Object readInternal(Class<?> clazz, HttpInputMessage inputMessage) throws IOException {
            try {
                return super.readInternal(clazz, inputMessage);
            } catch (HttpMessageNotReadableException e) {
                throw quiet(inputMessage);
            }
        }

        /** 원인 예외를 붙이지 않는다. 원인의 메시지에도 입력이 들어 있다. */
        private static HttpMessageNotReadableException quiet(HttpInputMessage inputMessage) {
            return new HttpMessageNotReadableException("JSON parse error", inputMessage);
        }
    }
}
