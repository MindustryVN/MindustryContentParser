package mindustrytool.mindustrycontentparser;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class MindustryContentParserApplication {

    public static void main(String[] args) {
        SpringApplication.run(MindustryContentParserApplication.class, args);
    }

}
