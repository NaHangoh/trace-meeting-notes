package tracenotes.summary;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tracenotes.minutes.Utterance;
import tracenotes.minutes.Whitespace;

/**
 * LLM 응답 검증 (SPEC F2 "코드 확인", rules/llm.md). LLM이 준 값을 그대로 믿지 않는다.
 * - 최상위가 JSON 객체이고 items가 배열이 아니면 형식 오류(INVALID_OUTPUT, 다시 부름)
 * - 항목 단위 문제는 그 항목이나 값만 버린다: 알 수 없는 type, 비었거나 500자를 넘는 text, 100개를 넘는 항목, 스키마 밖 필드
 * - 근거 번호는 덩어리에 실제로 있는 발언 번호만 남긴다. 남은 근거가 없으면 "근거 없음"
 * - 담당자·기한은 TODO에만, 공백을 뺀 2자 이상인 표현만 확인한다(공백 정규화 후).
 *   담당자는 근거 발언 화자 이름과 전체 일치 또는 근거 발언 내용에 부분 일치, 기한은 근거 발언 내용에 부분 일치할 때만 남긴다
 */
public class SummaryValidator {

    static final int MAX_ITEMS = 100;
    static final int MAX_TEXT_LENGTH = 500;
    static final int MAX_FIELD_LENGTH = 100;
    /** 담당자·기한의 최소 길이(공백 제외). "다", "A"처럼 짧은 값은 아무 발언에나 일치한다. */
    static final int MIN_FIELD_LENGTH = 2;

    private final JsonMapper json;

    public SummaryValidator(JsonMapper json) {
        this.json = json;
    }

    public List<SummaryItem> validate(String output, List<Utterance> chunk) {
        JsonNode items = itemsOf(output);
        Map<Integer, List<Utterance>> byNumber = chunk.stream()
                .collect(Collectors.groupingBy(Utterance::no));
        List<SummaryItem> result = new ArrayList<>();
        for (JsonNode node : items) {
            if (result.size() == MAX_ITEMS) {
                break;
            }
            SummaryItem item = item(node, byNumber);
            if (item != null) {
                result.add(item);
            }
        }
        return result;
    }

    private JsonNode itemsOf(String output) {
        JsonNode root;
        try {
            root = json.readTree(output == null ? "" : output);
        } catch (JacksonException e) {
            throw new InvalidSummaryOutputException("summary output is not JSON");
        }
        if (root == null || !root.isObject() || !root.path("items").isArray()) {
            throw new InvalidSummaryOutputException("summary output has no items array");
        }
        return root.path("items");
    }

    /** 버릴 항목이면 null. */
    private static SummaryItem item(JsonNode node, Map<Integer, List<Utterance>> byNumber) {
        if (!node.isObject()) {
            return null;
        }
        SummaryItemType type = type(node.path("type"));
        JsonNode textNode = node.path("text");
        if (type == null || !textNode.isString()) {
            return null;
        }
        String text = Whitespace.strip(textNode.asString());
        if (Whitespace.isBlank(text) || text.codePointCount(0, text.length()) > MAX_TEXT_LENGTH) {
            return null;
        }
        List<Integer> evidence = evidence(node.path("evidence"), byNumber);
        String assignee = null;
        String due = null;
        if (type == SummaryItemType.TODO) {
            List<Utterance> sources = evidence.stream().flatMap(no -> byNumber.get(no).stream()).toList();
            assignee = assignee(node.path("assignee"), sources);
            due = due(node.path("due"), sources);
        }
        return new SummaryItem(type, text, evidence, assignee, due);
    }

    private static SummaryItemType type(JsonNode node) {
        if (!node.isString()) {
            return null;
        }
        for (SummaryItemType type : SummaryItemType.values()) {
            if (type.name().equals(node.asString())) {
                return type;
            }
        }
        return null;
    }

    /** 정수이고 덩어리에 있는 번호만, 처음 나온 순서로, 중복 없이. */
    private static List<Integer> evidence(JsonNode node, Map<Integer, List<Utterance>> byNumber) {
        if (!node.isArray()) {
            return List.of();
        }
        Set<Integer> numbers = new LinkedHashSet<>();
        for (JsonNode number : node) {
            if (number.isIntegralNumber() && number.canConvertToInt() && byNumber.containsKey(number.asInt())) {
                numbers.add(number.asInt());
            }
        }
        return List.copyOf(numbers);
    }

    /** 담당자: 근거 발언의 화자 이름과 전체가 같거나, 근거 발언 내용에 들어 있을 때만. 아니면 null. */
    private static String assignee(JsonNode node, List<Utterance> sources) {
        String value = candidate(node);
        if (value == null) {
            return null;
        }
        for (Utterance source : sources) {
            if (Whitespace.normalize(source.speaker()).equals(value) || Whitespace.normalize(source.text()).contains(value)) {
                return value;
            }
        }
        return null;
    }

    /** 기한: 근거 발언 내용에 들어 있을 때만 (화자 이름과는 비교하지 않음). 아니면 null. */
    private static String due(JsonNode node, List<Utterance> sources) {
        String value = candidate(node);
        if (value == null) {
            return null;
        }
        for (Utterance source : sources) {
            if (Whitespace.normalize(source.text()).contains(value)) {
                return value;
            }
        }
        return null;
    }

    /**
     * 공백을 정규화한 문자열 값. 문자열이 아니거나, 공백을 뺀 2자 미만이거나(아무 데나 일치함), 100자를 넘으면 null.
     */
    private static String candidate(JsonNode node) {
        if (!node.isString()) {
            return null;
        }
        String value = Whitespace.normalize(node.asString());
        long nonSpace = value.codePoints().filter(cp -> !Whitespace.isSpace(cp)).count();
        if (nonSpace < MIN_FIELD_LENGTH || value.codePointCount(0, value.length()) > MAX_FIELD_LENGTH) {
            return null;
        }
        return value;
    }
}
