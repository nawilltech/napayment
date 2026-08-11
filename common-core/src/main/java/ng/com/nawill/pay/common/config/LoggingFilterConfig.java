package ng.com.nawill.pay.common.config;

import ng.com.nawill.pay.common.logging.RequestIdFilter;
import ng.com.nawill.pay.common.logging.UserContextMdcFilter;
import org.springframework.boot.autoconfigure.security.SecurityProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

@Configuration
public class LoggingFilterConfig {

    /** Runs before Spring Security so requestId is present even on auth failure. */
    @Bean
    public FilterRegistrationBean<RequestIdFilter> requestIdFilterRegistration() {
        FilterRegistrationBean<RequestIdFilter> registration = new FilterRegistrationBean<>(new RequestIdFilter());
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        registration.addUrlPatterns("/*");
        return registration;
    }

    /** Runs right after the Spring Security filter chain, before the controller. */
    @Bean
    public FilterRegistrationBean<UserContextMdcFilter> userContextMdcFilterRegistration() {
        FilterRegistrationBean<UserContextMdcFilter> registration =
                new FilterRegistrationBean<>(new UserContextMdcFilter());
        registration.setOrder(SecurityProperties.DEFAULT_FILTER_ORDER + 50);
        registration.addUrlPatterns("/*");
        return registration;
    }
}
