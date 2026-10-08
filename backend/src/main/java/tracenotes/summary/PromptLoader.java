package tracenotes.summary;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;
import org.springframework.core.io.ClassPathResource;

/**
 * 제품 프롬프트를 읽는다 (rules/llm.md). 파일은 저장소의 prompts/에 두고, 빌드 때 classpath의 prompts/로 복사된다.
 * 파일 맨 앞의 변경 기록 주석(<!-- ... -->)은 LLM에 보내지 않는다.
 */
public class PromptLoader {

    /** 버전은 v숫자만. 경로 조작으로 다른 파일을 읽지 못하게 한다. */
    private static final Pattern VERSION = Pattern.compile("v[0-9]+");

    public String summarizeTemplate(String version) {
        if (version == null || !VERSION.matcher(version).matches()) {
            throw new IllegalArgumentException("app.summary.prompt-version must look like v2");
        }
        String fileName = "summarize." + version + ".md";
        ClassPathResource resource = new ClassPathResource("prompts/" + fileName);
        if (!resource.exists()) {
            throw new IllegalArgumentException("prompt file not found: " + fileName);
        }
        try (InputStream in = resource.getInputStream()) {
            return withoutLeadingComments(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException("could not read prompt file: " + fileName, e);
        }
    }

    private static String withoutLeadingComments(String text) {
        String rest = text.strip();
        while (rest.startsWith("<!--")) {
            int end = rest.indexOf("-->");
            if (end < 0) {
                throw new IllegalArgumentException("unclosed comment at start of prompt file");
            }
            rest = rest.substring(end + 3).strip();
        }
        return rest;
    }
}
