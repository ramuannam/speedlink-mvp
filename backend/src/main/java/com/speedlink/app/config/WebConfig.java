import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**") // Applies to all endpoints (including /api/auth/signup)
                .allowedOrigins("https://www.speedlink.in, https://speedlink.in") // Your frontend domain
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS") // OPTIONS is crucial for preflight
                .allowedHeaders("*") // Allow all headers (or specify if needed)
                .allowCredentials(true); // Required if you are sending cookies or auth headers
    }
}