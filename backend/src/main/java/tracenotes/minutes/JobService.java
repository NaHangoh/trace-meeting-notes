package tracenotes.minutes;

import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 작업 생성 (SPEC F1, S1). 입력을 다시 검사·분할해 저장하고 작업 ID를 돌려준다. 정리(LLM) 시작은 F2. */
@Service
public class JobService {

    private final ScriptParser parser;
    private final JobRepository repository;
    private final Clock clock;

    public JobService(ScriptParser parser, JobRepository repository, Clock clock) {
        this.parser = parser;
        this.repository = repository;
        this.clock = clock;
    }

    /** 작업 ID는 UUID v4(SecureRandom 기반, UUID.randomUUID). 작업 ID가 곧 열람 권한이라 로그에 남기지 않는다. */
    @Transactional
    public String create(String script) {
        List<Utterance> utterances = parser.parse(script);
        String jobId = UUID.randomUUID().toString();
        repository.insert(jobId, clock.instant(), utterances);
        return jobId;
    }
}
