package tracenotes.summary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.json.JsonMapper;
import tracenotes.minutes.Utterance;

/** SPEC F2 응답 검증: 스키마 밖은 버리고, 근거 번호와 담당자·기한은 코드로 다시 확인한다. */
class SummaryValidatorTest {

    /** 덩어리: 발언 3~5번. */
    private static final List<Utterance> CHUNK = List.of(
            new Utterance(3, "진행자", "보고서는 다음 주 금요일까지 내기로 하죠."),
            new Utterance(4, "참석자 A", "제가 맡겠습니다."),
            new Utterance(5, "참석자 B", "디자인 검토는 참석자 C가 해 주세요."));

    private final SummaryValidator validator = new SummaryValidator(JsonMapper.builder().build());

    private static String item(String type, String text, String evidence, String assignee, String due) {
        return "{\"type\":\"" + type + "\",\"text\":\"" + text + "\",\"evidence\":" + evidence
                + ",\"assignee\":" + assignee + ",\"due\":" + due + "}";
    }

    private static String items(String... items) {
        return "{\"items\":[" + String.join(",", items) + "]}";
    }

    @Test
    void validItemIsKept() {
        List<SummaryItem> result = validator.validate(
                items(item("TODO", "보고서 제출", "[3,4]", "\"참석자 A\"", "\"다음 주 금요일\"")), CHUNK);

        assertThat(result).containsExactly(
                new SummaryItem(SummaryItemType.TODO, "보고서 제출", List.of(3, 4), "참석자 A", "다음 주 금요일"));
    }

    /** 최상위가 JSON 객체·items 배열이 아니면 형식 오류(INVALID_OUTPUT, 다시 부름). 메시지에 응답 내용을 넣지 않는다. */
    @ParameterizedTest
    @ValueSource(strings = {"비밀 내용이 든 일반 문장", "[]", "{}", "{\"items\":\"비밀 내용\"}", "{\"items\":[", ""})
    void invalidJsonIsFormatError(String output) {
        assertThatThrownBy(() -> validator.validate(output, CHUNK))
                .isInstanceOf(InvalidSummaryOutputException.class)
                .message().doesNotContain("비밀");
    }

    @Test
    void dropsUnknownFields() {
        String output = "{\"items\":[{\"type\":\"AGENDA\",\"text\":\"교육 일정\",\"evidence\":[3],"
                + "\"assignee\":null,\"due\":null,\"note\":\"추가 필드\",\"score\":0.9}],\"extra\":true}";

        List<SummaryItem> result = validator.validate(output, CHUNK);

        assertThat(result).containsExactly(new SummaryItem(SummaryItemType.AGENDA, "교육 일정", List.of(3), null, null));
    }

    /** text는 500자(코드 포인트)까지. 넘거나 비어 있으면 항목을 버린다. */
    @Test
    void dropsOverlongOrBlankText() {
        String exactly500 = new String(Character.toChars(0x1F600)).repeat(500);
        String over500 = "가".repeat(501);

        List<SummaryItem> result = validator.validate(items(
                item("AGENDA", exactly500, "[3]", "null", "null"),
                item("AGENDA", over500, "[3]", "null", "null"),
                item("AGENDA", "   ", "[3]", "null", "null"),
                "{\"type\":\"AGENDA\",\"evidence\":[3],\"assignee\":null,\"due\":null}"), CHUNK);

        assertThat(result).extracting(SummaryItem::text).containsExactly(exactly500);
    }

    @Test
    void dropsUnknownType() {
        List<SummaryItem> result = validator.validate(items(
                item("SUMMARY", "모르는 종류", "[3]", "null", "null"),
                item("todo", "소문자", "[3]", "null", "null"),
                item("DECISION", "화요일로 결정", "[3]", "null", "null")), CHUNK);

        assertThat(result).extracting(SummaryItem::type).containsExactly(SummaryItemType.DECISION);
    }

    @Test
    void capsItemsPerChunk() {
        String many = IntStream.range(0, 101)
                .mapToObj(i -> item("DISCUSSION", "의견 " + i, "[3]", "null", "null"))
                .collect(Collectors.joining(","));

        List<SummaryItem> result = validator.validate("{\"items\":[" + many + "]}", CHUNK);

        assertThat(result).hasSize(100);
        assertThat(result.get(99).text()).isEqualTo("의견 99");
    }

    /** 상한 100개는 남긴 항목만 센다. 버린 항목은 세지 않는다. */
    @Test
    void droppedItemsDoNotCountTowardCap() {
        String invalid = IntStream.range(0, 30)
                .mapToObj(i -> item("UNKNOWN", "버릴 항목 " + i, "[3]", "null", "null"))
                .collect(Collectors.joining(","));
        String valid = IntStream.range(0, 101)
                .mapToObj(i -> item("DISCUSSION", "의견 " + i, "[3]", "null", "null"))
                .collect(Collectors.joining(","));

        List<SummaryItem> result = validator.validate("{\"items\":[" + invalid + "," + valid + "]}", CHUNK);

        assertThat(result).hasSize(100);
        assertThat(result).extracting(SummaryItem::text).doesNotContain("버릴 항목 0").contains("의견 99");
    }

    /** items 안에 객체가 아닌 원소가 있으면 그 원소만 버린다. */
    @Test
    void nonObjectItemsAreDropped() {
        List<SummaryItem> result = validator.validate(
                "{\"items\":[\"문자열\",5,null,[]," + item("AGENDA", "안건", "[3]", "null", "null") + "]}", CHUNK);

        assertThat(result).extracting(SummaryItem::text).containsExactly("안건");
    }

    /** text 앞뒤 공백은 F1 공백 기준(NBSP·전각 공백 포함)으로 지운다. */
    @Test
    void textIsStrippedWithF1WhitespaceRule() {
        String nbsp = Character.toString(0x00A0);
        String ideographicSpace = Character.toString(0x3000);

        List<SummaryItem> result = validator.validate(
                items(item("AGENDA", nbsp + ideographicSpace + "안건" + nbsp, "[3]", "null", "null")), CHUNK);

        assertThat(result).extracting(SummaryItem::text).containsExactly("안건");
    }

    /** 덩어리 밖·없는 번호, 정수가 아닌 값, 중복은 버린다. 순서는 처음 나온 순서. */
    @Test
    void dropsEvidenceOutsideChunk() {
        List<SummaryItem> result = validator.validate(
                items(item("DISCUSSION", "의견",
                        "[5,1,3,9,\"4\",4.5,3,-1,4294967299,99999999999999999999999,3.0,null]", "null", "null")), CHUNK);

        assertThat(result).singleElement().extracting(SummaryItem::evidence).isEqualTo(List.of(5, 3));
    }

    /** 근거가 비거나 모두 버려진 항목은 남기되 "근거 없음"이다. */
    @Test
    void itemWithoutEvidenceIsUnsupported() {
        List<SummaryItem> result = validator.validate(items(
                item("DECISION", "근거 없는 결정", "[]", "null", "null"),
                item("DECISION", "범위 밖 근거", "[1,2,99]", "null", "null"),
                "{\"type\":\"DECISION\",\"text\":\"근거 필드 없음\",\"assignee\":null,\"due\":null}"), CHUNK);

        assertThat(result).hasSize(3).allSatisfy(item -> {
            assertThat(item.evidence()).isEmpty();
            assertThat(item.hasEvidence()).isFalse();
        });
    }

    /** 근거가 남지 않은 TODO는 확인할 발언이 없으므로 담당자·기한도 비운다. */
    @Test
    void todoWithoutEvidenceHasNoAssigneeOrDue() {
        List<SummaryItem> result = validator.validate(items(
                item("TODO", "보고서", "[]", "\"참석자 A\"", "\"다음 주 금요일\""),
                item("TODO", "보고서", "[1,99]", "\"참석자 A\"", "\"다음 주 금요일\"")), CHUNK);

        assertThat(result).hasSize(2).allSatisfy(item -> {
            assertThat(item.assignee()).isNull();
            assertThat(item.due()).isNull();
        });
    }

    /** 공백을 뺀 2자 미만의 담당자·기한은 아무 데나 일치하므로 비운다 ("다"는 "습니다"에 일치). */
    @Test
    void shortAssigneeOrDueIsCleared() {
        List<SummaryItem> result = validator.validate(items(
                item("TODO", "맡기", "[4]", "\"A\"", "\"다\""),
                item("TODO", "맡기", "[4]", "\" A \"", "\"제 \"")), CHUNK);

        assertThat(result).hasSize(2).allSatisfy(item -> {
            assertThat(item.assignee()).isNull();
            assertThat(item.due()).isNull();
        });
    }

    /** 기한은 근거 발언 내용에서만 찾는다. 화자 이름과 일치해도 기한이 아니다. */
    @Test
    void dueIsComparedOnlyWithText() {
        List<SummaryItem> result = validator.validate(
                items(item("TODO", "맡기", "[4]", "null", "\"참석자 A\"")), CHUNK);

        assertThat(result).singleElement().extracting(SummaryItem::due).isNull();
    }

    /** 담당자는 근거 발언 화자 이름과 전체가 같거나 내용에 들어 있을 때만. 화자 이름의 일부("진행")는 아니다. */
    @Test
    void assigneeMatchesWholeSpeakerOrText() {
        List<SummaryItem> result = validator.validate(items(
                item("TODO", "보고서", "[3]", "\"진행\"", "null"),
                item("TODO", "보고서", "[3]", "\"진행자\"", "null"),
                item("TODO", "디자인 검토", "[5]", "\"참석자 C\"", "null")), CHUNK);

        assertThat(result).extracting(SummaryItem::assignee).containsExactly(null, "진행자", "참석자 C");
    }

    /** LLM 값 쪽의 연속·전각 공백도 정규화해서 비교하고, 정규화된 값으로 남긴다. */
    @Test
    void assigneeValueWhitespaceIsNormalized() {
        String ideographicSpace = Character.toString(0x3000);

        List<SummaryItem> result = validator.validate(items(
                item("TODO", "맡기", "[4]", "\"참석자" + ideographicSpace + ideographicSpace + "A\"", "null")), CHUNK);

        assertThat(result).singleElement().extracting(SummaryItem::assignee).isEqualTo("참석자 A");
    }

    /** 담당자·기한은 근거 발언의 화자 이름이나 내용에 그 표현이 있으면 남긴다. */
    @Test
    void keepsAssigneeFoundInSpeakerOrText() {
        List<SummaryItem> result = validator.validate(items(
                item("TODO", "보고서", "[3,4]", "\"참석자 A\"", "\"다음 주 금요일\""),
                item("TODO", "디자인 검토", "[5]", "\"참석자 C\"", "null")), CHUNK);

        assertThat(result).extracting(SummaryItem::assignee).containsExactly("참석자 A", "참석자 C");
        assertThat(result).extracting(SummaryItem::due).containsExactly("다음 주 금요일", null);
    }

    /** 근거 발언 어디에도 없는 담당자·기한은 비운다 (다른 발언에만 있어도 비운다). */
    @Test
    void clearsAssigneeAndDueNotInEvidence() {
        List<SummaryItem> result = validator.validate(items(
                item("TODO", "보고서", "[5]", "\"참석자 A\"", "\"다음 주 금요일\""),
                item("TODO", "보고서", "[3]", "\"김부장\"", "\"10월 31일\"")), CHUNK);

        assertThat(result).allSatisfy(item -> {
            assertThat(item.assignee()).isNull();
            assertThat(item.due()).isNull();
        });
    }

    /** 공백만 다른 표현(연속 공백, 전각 공백, NBSP)은 같은 것으로 본다. */
    @Test
    void whitespaceVariantsMatch() {
        String ideographicSpace = Character.toString(0x3000);
        String nbsp = Character.toString(0x00A0);
        List<Utterance> chunk = List.of(
                new Utterance(7, "참석자" + nbsp + "A", "다음" + ideographicSpace + "주  금요일까지 합니다."));

        List<SummaryItem> result = validator.validate(
                items(item("TODO", "제출", "[7]", "\"참석자 A\"", "\"다음 주 금요일\"")), chunk);

        assertThat(result).singleElement().satisfies(item -> {
            assertThat(item.assignee()).isEqualTo("참석자 A");
            assertThat(item.due()).isEqualTo("다음 주 금요일");
        });
    }

    /** 담당자·기한은 TODO에만 둔다. 비어 있거나 100자를 넘으면 비운다. */
    @Test
    void assigneeAndDueOnlyForTodoAndNotTooLong() {
        String longAssignee = "참석자 A".repeat(30);
        List<SummaryItem> result = validator.validate(items(
                item("DECISION", "결정", "[3,4]", "\"참석자 A\"", "\"다음 주 금요일\""),
                item("TODO", "할 일", "[4]", "\"" + longAssignee + "\"", "\"   \""),
                item("TODO", "할 일", "[4]", "123", "[\"다음 주\"]")), CHUNK);

        assertThat(result).allSatisfy(item -> {
            assertThat(item.assignee()).isNull();
            assertThat(item.due()).isNull();
        });
    }

    /** 항목을 로그에 찍어도 내용·담당자가 나오지 않는다. */
    @Test
    void itemToStringHidesContent() {
        SummaryItem item = new SummaryItem(SummaryItemType.TODO, "비밀 안건", List.of(3), "참석자 A", "금요일");

        assertThat(item.toString()).doesNotContain("비밀 안건").doesNotContain("참석자 A").doesNotContain("금요일");
    }
}
