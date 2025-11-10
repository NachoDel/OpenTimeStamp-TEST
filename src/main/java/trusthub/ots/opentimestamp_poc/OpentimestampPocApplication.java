package trusthub.ots.opentimestamp_poc;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

@SpringBootApplication
public class OpentimestampPocApplication {

	public static void main(String[] args) {
		SpringApplication.run(OpentimestampPocApplication.class, args);
	}

	@Bean
	@ConditionalOnBean(RequestMappingHandlerMapping.class)
	public CommandLineRunner printMappings(RequestMappingHandlerMapping mapping) {
		return args -> {
			System.out.println("=== Registered Request Mappings ===");
			mapping.getHandlerMethods().forEach((info, method) ->
				System.out.println(info + " -> " + method)
			);
			System.out.println("=== End mappings ===");
		};
	}
	
}
