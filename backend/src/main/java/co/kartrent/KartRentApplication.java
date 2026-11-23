package co.kartrent;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class KartRentApplication {
    public static void main(String[] args) {
        SpringApplication.run(KartRentApplication.class, args);
    }
}
