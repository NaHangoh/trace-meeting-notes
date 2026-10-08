package tracenotes.minutes;

import java.util.List;
import org.springframework.stereotype.Service;

/** 미리보기 (SPEC F1). 검사·분할만 하고 저장하지 않으며 본문을 로그에 남기지 않는다. */
@Service
public class PreviewService {

    private final ScriptParser parser;

    public PreviewService(ScriptParser parser) {
        this.parser = parser;
    }

    public Preview preview(String script) {
        List<Utterance> utterances = parser.parse(script);
        return new Preview(utterances.size(), ScriptSplitter.speakersOf(utterances));
    }

    /**
     * 발언 수와 화자 목록만. 발언 본문은 담지 않는다.
     * 화자 이름도 입력에서 온 값이라 toString에는 개수만 넣는다 (Spring 웹 DEBUG 로그가 응답 객체를 toString으로 남긴다).
     */
    public record Preview(int utteranceCount, List<String> speakers) {

        @Override
        public String toString() {
            return "Preview[utteranceCount=" + utteranceCount + ", speakerCount=" + speakers.size() + "]";
        }
    }
}
