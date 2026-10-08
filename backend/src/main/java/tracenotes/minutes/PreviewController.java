package tracenotes.minutes;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import tracenotes.minutes.PreviewService.Preview;

/** 미리보기 API. 요청·응답 변환만 한다. */
@RestController
public class PreviewController {

    private final PreviewService service;

    public PreviewController(PreviewService service) {
        this.service = service;
    }

    @PostMapping("/api/preview")
    Preview preview(@RequestBody PreviewRequest request) {
        return service.preview(request.text());
    }

    /** toString에 본문을 넣지 않는다. Spring 웹 DEBUG 로그가 요청 객체를 toString으로 남긴다. */
    record PreviewRequest(String text) {

        @Override
        public String toString() {
            return "PreviewRequest[length=" + (text == null ? "null" : text.length()) + "]";
        }
    }
}
