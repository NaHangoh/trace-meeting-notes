package tracenotes.summary;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 정리 작업 API. 요청·응답 변환만 한다. 작업 ID는 로그에 남기지 않는다. */
@RestController
public class SummaryJobController {

    private final SummaryRetryService retryService;
    private final SummaryStatusService statusService;

    public SummaryJobController(SummaryRetryService retryService, SummaryStatusService statusService) {
        this.retryService = retryService;
        this.statusService = statusService;
    }

    /** 작업 상태와 결과. 없는 작업은 404 (JobNotFoundException → ApiExceptionHandler). */
    @GetMapping("/api/jobs/{id}")
    JobStatusResponse status(@PathVariable String id) {
        return JobStatusResponse.of(statusService.find(id));
    }

    /** 실패한 작업을 다시 대기열에 넣는다. 결과는 작업 조회로 확인한다. */
    @PostMapping("/api/jobs/{id}/retry")
    @ResponseStatus(HttpStatus.ACCEPTED)
    void retry(@PathVariable String id) {
        retryService.retry(id);
    }

    /**
     * 작업 조회 응답 {status, failure?, items?} (SPEC F2). 발언 본문은 넣지 않는다.
     * failure는 FAILED일 때만, items는 DONE일 때만 나온다.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record JobStatusResponse(JobStatus status, SummaryFailure failure, List<ItemResponse> items) {

        static JobStatusResponse of(JobResult result) {
            List<ItemResponse> items = result.items() == null ? null : result.items().stream().map(ItemResponse::of).toList();
            return new JobStatusResponse(result.state().status(), result.state().failure(), items);
        }

        /** toString에 항목 내용을 넣지 않는다. Spring 웹 DEBUG 로그가 응답 객체를 toString으로 남긴다. */
        @Override
        public String toString() {
            return "JobStatusResponse[status=" + status + ", failure=" + failure
                    + ", items=" + (items == null ? "none" : items.size()) + "]";
        }
    }

    /** 항목 하나. evidence가 비어 있으면 화면이 "근거 없음"으로 표시한다. assignee·due는 없으면 null로 나온다. */
    record ItemResponse(SummaryItemType type, String text, List<Integer> evidence, String assignee, String due) {

        static ItemResponse of(SummaryItem item) {
            return new ItemResponse(item.type(), item.text(), item.evidence(), item.assignee(), item.due());
        }

        @Override
        public String toString() {
            return "ItemResponse[type=" + type + ", textLength=" + text.length() + ", evidence=" + evidence
                    + ", assignee=" + (assignee == null ? "none" : "set") + ", due=" + (due == null ? "none" : "set") + "]";
        }
    }
}
