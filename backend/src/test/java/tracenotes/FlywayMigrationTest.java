package tracenotes;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationState;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
class FlywayMigrationTest {

    @Autowired
    Flyway flyway;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    Environment environment;

    @Test
    void v1IsAppliedOnStartup() {
        List<MigrationInfo> applied = Arrays.asList(flyway.info().applied());

        assertThat(applied)
                .anySatisfy(info -> {
                    assertThat(info.getVersion().getVersion()).isEqualTo("1");
                    assertThat(info.getState()).isEqualTo(MigrationState.SUCCESS);
                });
    }

    @Test
    void tablesComeFromMigration() {
        List<String> tables = jdbcTemplate.queryForList(
                "SELECT TABLE_NAME FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA = 'PUBLIC'",
                String.class);

        assertThat(tables).contains("JOB", "JOB_UTTERANCE");
    }

    @Test
    void hibernateDoesNotRunDdl() {
        assertThat(environment.getProperty("spring.jpa.hibernate.ddl-auto")).isEqualTo("validate");
    }
}
