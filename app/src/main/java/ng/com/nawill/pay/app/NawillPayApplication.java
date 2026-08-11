package ng.com.nawill.pay.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Composition root for the modular monolith. Component scan, entity scan,
 * and JPA repository scan all span every ng.com.nawill.pay.* module - this
 * is the one place that needs to know all of them exist.
 */
@SpringBootApplication(scanBasePackages = "ng.com.nawill.pay")
@EntityScan(basePackages = "ng.com.nawill.pay")
@EnableJpaRepositories(basePackages = "ng.com.nawill.pay")
public class NawillPayApplication {

    public static void main(String[] args) {
        SpringApplication.run(NawillPayApplication.class, args);
    }
}
