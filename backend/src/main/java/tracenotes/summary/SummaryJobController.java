package tracenotes.summary;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 정리 작업 API. 요청·응답 변환만 한다. 작업 ID는 로그에 남기지 않는다. */
@RestController
public class SummaryJobController {

    private final SummaryRetryService retryService;

    public SummaryJobController(SummaryRetryService retryService) {
        this.retryService = retryService;
    }

    /** 실패한 작업을 다시 대기열에 넣는다. 결과는 작업 조회로 확인한다. */
    @PostMapping("/api/jobs/{id}/retry")
    @ResponseStatus(HttpStatus.ACCEPTED)
    void retry(@PathVariable String id) {
        retryService.retry(id);
    }
}
