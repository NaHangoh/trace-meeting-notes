package tracenotes.minutes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ParameterizedPreparedStatementSetter;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/** 발언 저장이 실패하면 작업 행도 남지 않는다 (작업과 발언은 한 트랜잭션). */
@SpringBootTest
class JobServiceTransactionTest {

    @MockitoSpyBean
    JdbcTemplate jdbc;

    @Autowired
    JobService service;

    @Test
    void failedUtteranceInsertRollsBackJob() {
        int jobsBefore = jdbc.queryForObject("SELECT COUNT(*) FROM job", Integer.class);
        doThrow(new DataIntegrityViolationException("utterance insert failed"))
                .when(jdbc).batchUpdate(anyString(), anyCollection(), anyInt(), any(ParameterizedPreparedStatementSetter.class));

        assertThatThrownBy(() -> service.create("김민수: 안녕하세요"))
                .isInstanceOf(DataIntegrityViolationException.class);

        // 작업 INSERT가 실제로 실행된 뒤 실패했는지 확인한다. 저장 순서가 바뀌면 롤백 없이도 개수가 같을 수 있다
        verify(jdbc).update(startsWith("INSERT INTO job ("), ArgumentMatchers.<Object>any(), ArgumentMatchers.<Object>any());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM job", Integer.class)).isEqualTo(jobsBefore);
    }
}
