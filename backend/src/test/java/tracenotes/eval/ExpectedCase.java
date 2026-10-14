package tracenotes.eval;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tracenotes.summary.SummaryItemType;

/**
 * 사람이 쓴 정답 회의록 (eval/cases/{케이스}/expected.json). AI는 이 파일을 쓰지 않는다(훅이 막음).
 * 형식이 틀리면 측정하지 않고 바로 실패한다 (틀린 정답으로 숫자가 나오지 않게).
 *
 * @param items 정답 항목
 * @param injection 인젝션 케이스면 확정되면 안 되는 표현, 아니면 null
 */
record ExpectedCase(List<ExpectedItem> items, Injection injection) {

    /**
     * @param id 케이스 안에서 겹치지 않는 이름 (결과 표에서 놓친 항목을 가리킬 때)
     * @param type 정답 종류
     * @param keywords 출력 text에 모두 들어 있으면 이 항목으로 본다 (공백 정규화 후 부분 일치). 하나 이상
     * @param evidence 정답 근거 발언 번호. 하나 이상
     * @param values 결정된 값 (DECISION만). 출력 text에 모두 들어 있어야 "결정 값 포함"
     * @param assignee 정답 담당자 원문 표현 (TODO만, 없으면 null)
     * @param due 정답 기한 원문 표현 (TODO만, 없으면 null)
     */
    record ExpectedItem(String id, SummaryItemType type, List<String> keywords, List<Integer> evidence,
            List<String> values, String assignee, String due) {
    }

    /** @param forbidden DECISION·TODO 항목(text·담당자·기한)에 나오면 인젝션이 통과한 것으로 보는 표현. 하나 이상 */
    record Injection(List<String> forbidden) {
    }

    static ExpectedCase parse(JsonMapper json, String text) {
        JsonNode root = json.readTree(text);
        require(root.isObject() && root.path("items").isArray(), "items 배열이 필요하다");
        List<ExpectedItem> items = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        for (JsonNode node : root.path("items")) {
            ExpectedItem item = item(node);
            require(ids.add(item.id()), "id가 겹친다: " + item.id());
            items.add(item);
        }
        Injection injection = null;
        JsonNode injectionNode = root.path("injection");
        if (!injectionNode.isMissingNode() && !injectionNode.isNull()) {
            List<String> forbidden = strings(injectionNode.path("forbidden"), "injection.forbidden");
            require(!forbidden.isEmpty(), "injection.forbidden이 비었다");
            injection = new Injection(forbidden);
        }
        return new ExpectedCase(List.copyOf(items), injection);
    }

    private static ExpectedItem item(JsonNode node) {
        require(node.isObject(), "항목은 객체여야 한다");
        String id = node.path("id").asString("");
        require(!id.isBlank(), "id가 필요하다");
        SummaryItemType type;
        try {
            type = SummaryItemType.valueOf(node.path("type").asString(""));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("expected.json: " + id + ": type은 AGENDA, DISCUSSION, DECISION, TODO 중 하나");
        }
        List<String> keywords = strings(node.path("keywords"), id + ".keywords");
        require(!keywords.isEmpty(), id + ": keywords가 하나 이상 필요하다");
        List<Integer> evidence = new ArrayList<>();
        require(node.path("evidence").isArray(), id + ": evidence 배열이 필요하다");
        for (JsonNode number : node.path("evidence")) {
            require(number.isIntegralNumber() && number.asInt() >= 1, id + ": evidence는 1 이상 정수");
            evidence.add(number.asInt());
        }
        require(!evidence.isEmpty(), id + ": evidence가 하나 이상 필요하다");
        List<String> values = node.has("values") ? strings(node.path("values"), id + ".values") : List.of();
        require(values.isEmpty() || type == SummaryItemType.DECISION, id + ": values는 DECISION에만");
        String assignee = optionalString(node.path("assignee"), id + ".assignee");
        String due = optionalString(node.path("due"), id + ".due");
        require(type == SummaryItemType.TODO || (assignee == null && due == null),
                id + ": assignee·due는 TODO에만");
        return new ExpectedItem(id, type, keywords, List.copyOf(evidence), values, assignee, due);
    }

    private static List<String> strings(JsonNode node, String name) {
        require(node.isArray(), name + "는 문자열 배열이어야 한다");
        List<String> result = new ArrayList<>();
        for (JsonNode value : node) {
            require(value.isString() && !value.asString().isBlank(), name + "에 빈 값이나 문자열 아닌 값이 있다");
            result.add(value.asString());
        }
        return List.copyOf(result);
    }

    private static String optionalString(JsonNode node, String name) {
        if (node.isMissingNode() || node.isNull()) {
            return null;
        }
        require(node.isString() && !node.asString().isBlank(), name + "는 문자열이나 null");
        return node.asString();
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException("expected.json: " + message);
        }
    }
}
