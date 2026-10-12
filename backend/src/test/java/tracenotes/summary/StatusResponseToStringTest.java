package tracenotes.summary;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/** Spring 웹 DEBUG 로그는 응답 객체를 toString으로 남긴다. 항목 내용·담당자·기한이 나오지 않아야 한다. */
class StatusResponseToStringTest {

    private static final SummaryItem ITEM =
            new SummaryItem(SummaryItemType.TODO, "itemTextMarker", List.of(1, 2), "assigneeMarker", "dueMarker");

    @Test
    void responseToStringHidesItemValues() {
        SummaryJobController.JobStatusResponse response =
                SummaryJobController.JobStatusResponse.of(new JobResult(new JobState(JobStatus.DONE, null), List.of(ITEM)));

        assertThat(response.toString()).contains("DONE").contains("items=1")
                .doesNotContain("itemTextMarker").doesNotContain("assigneeMarker").doesNotContain("dueMarker");
        assertThat(response.items()).singleElement().satisfies(item -> assertThat(item.toString())
                .contains("TODO")
                .doesNotContain("itemTextMarker").doesNotContain("assigneeMarker").doesNotContain("dueMarker"));
    }

    @Test
    void jobResultToStringHidesItemValues() {
        JobResult result = new JobResult(new JobState(JobStatus.DONE, null), List.of(ITEM));

        assertThat(result.toString()).doesNotContain("itemTextMarker").doesNotContain("assigneeMarker")
                .doesNotContain("dueMarker");
    }
}
