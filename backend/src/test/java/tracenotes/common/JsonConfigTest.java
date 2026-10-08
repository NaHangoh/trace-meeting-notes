package tracenotes.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter;
import tools.jackson.databind.json.JsonMapper;

/** 조용한 JSON 변환기가 기본 JSON 변환기 자리를 대신하는지 (앞에 끼어들거나 둘이 되지 않는지). */
@SpringBootTest
class JsonConfigTest {

    @Autowired
    RequestMappingHandlerAdapter adapter;

    @Autowired
    JsonMapper jsonMapper;

    @Test
    void quietConverterReplacesDefaultJsonConverter() {
        List<HttpMessageConverter<?>> converters = adapter.getMessageConverters();

        List<HttpMessageConverter<?>> jackson = converters.stream()
                .filter(c -> c.getClass() == JacksonJsonHttpMessageConverter.class
                        || c instanceof JsonConfig.QuietJsonConverter)
                .toList();
        assertThat(jackson).hasSize(1);
        assertThat(jackson.get(0)).isInstanceOf(JsonConfig.QuietJsonConverter.class);
        assertThat(((JsonConfig.QuietJsonConverter) jackson.get(0)).getMapper()).isSameAs(jsonMapper);
    }

    /** String 변환기보다 뒤에 있어야 String 요청·응답이 기본 동작(text/plain, 본문 그대로)을 유지한다. */
    @Test
    void quietConverterComesAfterStringConverter() {
        List<HttpMessageConverter<?>> converters = adapter.getMessageConverters();

        int string = indexOf(converters, StringHttpMessageConverter.class);
        int quiet = indexOf(converters, JsonConfig.QuietJsonConverter.class);
        assertThat(string).isNotNegative();
        assertThat(quiet).isGreaterThan(string);
    }

    private static int indexOf(List<HttpMessageConverter<?>> converters, Class<?> type) {
        for (int i = 0; i < converters.size(); i++) {
            if (type.isInstance(converters.get(i))) {
                return i;
            }
        }
        return -1;
    }
}
