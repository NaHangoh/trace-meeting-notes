package tracenotes;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class TraceNotesApplication {

    public static void main(String[] args) {
        SpringApplication.run(TraceNotesApplication.class, args);
    }
}
