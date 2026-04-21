package fr.enseeiht.jeux.config;

import org.h2.server.web.JakartaWebServlet;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Enregistre manuellement la console H2 (Jakarta EE compatible — Spring Boot 4).
 * Accessible sur : http://localhost:8080/h2-console/
 */
@Configuration
public class H2ConsoleConfig {

    @Bean
    public ServletRegistrationBean<JakartaWebServlet> h2servletRegistration() {
        ServletRegistrationBean<JakartaWebServlet> registrationBean =
                new ServletRegistrationBean<>(new JakartaWebServlet());
        registrationBean.addUrlMappings("/h2-console/*");
        registrationBean.addInitParameter("webAllowOthers", "true");
        return registrationBean;
    }
}
