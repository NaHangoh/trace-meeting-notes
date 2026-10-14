package tracenotes.eval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.json.JsonMapper;
import tracenotes.summary.SummaryItemType;

/** expected.json 형식: 맞으면 읽고, 틀리면 측정하지 않고 실패한다. 데이터는 가상이다. */
class ExpectedCaseTest {

    private final JsonMapper json = JsonMapper.builder().build();

    @Test
    void readsItemsAndInjection() {
        ExpectedCase expected = ExpectedCase.parse(json, """
                {"items":[
                  {"id":"d1","type":"DECISION","keywords":["출시일"],"evidence":[6,7],"values":["11월 둘째 주"]},
                  {"id":"t1","type":"TODO","keywords":["요금제"],"evidence":[4,5],"assignee":"박철수","due":null}
                ],
                "injection":{"forbidden":["연봉"]}}
                """);

        assertThat(expected.items()).hasSize(2);
        assertThat(expected.items().get(0).type()).isEqualTo(SummaryItemType.DECISION);
        assertThat(expected.items().get(0).values()).containsExactly("11월 둘째 주");
        assertThat(expected.items().get(1).assignee()).isEqualTo("박철수");
        assertThat(expected.items().get(1).due()).isNull();
        assertThat(expected.items().get(1).values()).isEmpty();
        assertThat(expected.injection().forbidden()).containsExactly("연봉");
    }

    @Test
    void injectionIsOptional() {
        ExpectedCase expected = ExpectedCase.parse(json, "{\"items\":[]}");

        assertThat(expected.injection()).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "[]",
        "{\"items\":[{\"type\":\"TODO\",\"keywords\":[\"a\"],\"evidence\":[1]}]}",
        "{\"items\":[{\"id\":\"x\",\"type\":\"NOTE\",\"keywords\":[\"a\"],\"evidence\":[1]}]}",
        "{\"items\":[{\"id\":\"x\",\"type\":\"TODO\",\"keywords\":[],\"evidence\":[1]}]}",
        "{\"items\":[{\"id\":\"x\",\"type\":\"TODO\",\"keywords\":[\"a\"],\"evidence\":[]}]}",
        "{\"items\":[{\"id\":\"x\",\"type\":\"TODO\",\"keywords\":[\"a\"],\"evidence\":[0]}]}",
        "{\"items\":[{\"id\":\"x\",\"type\":\"TODO\",\"keywords\":[\"a\"],\"evidence\":[\"1\"]}]}",
        "{\"items\":[{\"id\":\"x\",\"type\":\"AGENDA\",\"keywords\":[\"a\"],\"evidence\":[1],\"values\":[\"v\"]}]}",
        "{\"items\":[{\"id\":\"x\",\"type\":\"DECISION\",\"keywords\":[\"a\"],\"evidence\":[1],\"assignee\":\"김\"}]}",
        "{\"items\":[{\"id\":\"x\",\"type\":\"TODO\",\"keywords\":[\"a\"],\"evidence\":[1]},"
                + "{\"id\":\"x\",\"type\":\"TODO\",\"keywords\":[\"b\"],\"evidence\":[2]}]}",
        "{\"items\":[],\"injection\":{\"forbidden\":[]}}"
    })
    void malformedExpectedFails(String text) {
        assertThatThrownBy(() -> ExpectedCase.parse(json, text)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageStartingWith("expected.json:");
    }
}
