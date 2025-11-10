package trusthub.ots.opentimestamp_poc;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class OpentimestampPocApplication {

	public static void main(String[] args) {
		SpringApplication.run(OpentimestampPocApplication.class, args);
	}

	//Solo utilizado en desarrollo, se puede eliminar
	@Bean
	@ConditionalOnBean(name = "requestMappingHandlerMapping")
	public CommandLineRunner printMappings(ApplicationContext ctx) {
		return args -> {
			System.out.println("=== Registered Request Mappings ===");
			if (ctx.containsBean("requestMappingHandlerMapping")) {
				Object mapping = ctx.getBean("requestMappingHandlerMapping");
				System.out.println("Found bean 'requestMappingHandlerMapping' of type: " + mapping.getClass().getName());
			} else {
				System.out.println("No requestMappingHandlerMapping bean found.");
			}
			System.out.println("=== End mappings ===");
		};
	}
	
}
