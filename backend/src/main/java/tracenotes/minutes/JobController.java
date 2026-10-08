package tracenotes.minutes;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 작업 생성 API. 요청·응답 변환만 한다. */
@RestController
public class JobController {

    private final JobService service;

    public JobController(JobService service) {
        this.service = service;
    }

    @PostMapping("/api/jobs")
    @ResponseStatus(HttpStatus.CREATED)
    JobCreated create(@RequestBody JobRequest request) {
        return new JobCreated(service.create(request.text()));
    }

    /** toString에 본문을 넣지 않는다. Spring 웹 DEBUG 로그가 요청 객체를 toString으로 남긴다. */
    record JobRequest(String text) {

        @Override
        public String toString() {
            return "JobRequest[length=" + (text == null ? "null" : text.length()) + "]";
        }
    }

    /** toString에 작업 ID를 넣지 않는다. 작업 ID가 곧 열람 권한이다. */
    record JobCreated(String jobId) {

        @Override
        public String toString() {
            return "JobCreated[jobId=hidden]";
        }
    }
}
