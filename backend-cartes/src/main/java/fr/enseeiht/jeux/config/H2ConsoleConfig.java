package fr.enseeiht.jeux.config;

import org.h2.server.web.JakartaWebServlet;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// Enregistre la console H2 uniquement lorsqu'elle est explicitement activée.
@Configuration
@ConditionalOnProperty(name = "spring.h2.console.enabled", havingValue = "true")
public class H2ConsoleConfig {

    @Bean
    public ServletRegistrationBean<JakartaWebServlet> h2servletRegistration() {
        ServletRegistrationBean<JakartaWebServlet> registrationBean =
                new ServletRegistrationBean<>(new JakartaWebServlet());
        registrationBean.addUrlMappings("/h2-console/*");
        registrationBean.addInitParameter("webAllowOthers", "false");
        return registrationBean;
    }
}
