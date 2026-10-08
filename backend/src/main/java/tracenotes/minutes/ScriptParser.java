package tracenotes.minutes;

import java.util.List;
import org.springframework.stereotype.Component;
import tracenotes.common.AppProperties;
import tracenotes.minutes.InvalidScriptException.Reason;

/** 서버 검사 + 분할 + 발언 0개 거부. 미리보기와 작업 생성이 같이 써서 결과가 같다 (SPEC F1). */
@Component
public class ScriptParser {

    private final ScriptValidator validator;
    private final ScriptSplitter splitter = new ScriptSplitter();

    public ScriptParser(AppProperties properties) {
        this.validator = new ScriptValidator(properties.maxScriptChars());
    }

    public List<Utterance> parse(String script) {
        validator.validate(script);
        List<Utterance> utterances = splitter.split(script);
        if (utterances.isEmpty()) {
            throw new InvalidScriptException(Reason.NO_UTTERANCES);
        }
        return utterances;
    }
}
