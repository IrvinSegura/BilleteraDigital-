package com.irvinsegura.wallet.shared.web;

import java.io.IOException;
import java.io.Writer;
import org.apache.catalina.Valve;
import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;
import org.apache.catalina.core.StandardHost;
import org.apache.catalina.valves.ErrorReportValve;
import org.springframework.boot.tomcat.ConfigurableTomcatWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

/**
 * Requests Tomcat refuses before Spring sees them (an encoded NUL or another invalid character in the path) get a
 * small Problem Details body instead of Tomcat's HTML error page.
 */
@Configuration(proxyBeanMethods = false)
class TomcatErrorReportConfiguration {

    @Bean
    ProblemErrorReportCustomizer problemErrorReportCustomizer() {
        return new ProblemErrorReportCustomizer();
    }

    static class ProblemErrorReportCustomizer
            implements WebServerFactoryCustomizer<ConfigurableTomcatWebServerFactory>, Ordered {

        @Override
        public void customize(ConfigurableTomcatWebServerFactory factory) {
            // Runs after Spring Boot's own customizer, so its HTML valve is already there to be replaced
            factory.addContextCustomizers(context -> {
                for (Valve valve : context.getParent().getPipeline().getValves()) {
                    if (valve instanceof ErrorReportValve) {
                        context.getParent().getPipeline().removeValve(valve);
                    }
                }
                context.getParent().getPipeline().addValve(new ProblemErrorReportValve());
                // On start the host adds its own HTML valve unless it finds one of the class named here
                if (context.getParent() instanceof StandardHost host) {
                    host.setErrorReportValveClass(ProblemErrorReportValve.class.getName());
                }
            });
        }

        @Override
        public int getOrder() {
            return Ordered.LOWEST_PRECEDENCE;
        }
    }

    static class ProblemErrorReportValve extends ErrorReportValve {

        @Override
        protected void report(Request request, Response response, Throwable throwable) {
            int statusCode = response.getStatus();
            // Same conditions as Tomcat's own report: an error nobody has written a body for yet
            if (statusCode < 400 || response.getContentWritten() > 0 || !response.setErrorReported()) {
                return;
            }
            HttpStatus status = HttpStatus.resolve(statusCode);
            String title = status != null ? status.getReasonPhrase() : "Error";
            String code = status == null ? "HTTP_" + statusCode : status.is5xxServerError() ? "INTERNAL_ERROR"
                    : status.name();
            try {
                response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
                response.setCharacterEncoding("UTF-8");
                Writer writer = response.getReporter();
                if (writer != null) {
                    writer.write("{\"title\":\"%s\",\"status\":%d,\"code\":\"%s\"}".formatted(title, statusCode, code));
                    response.finishResponse();
                }
            } catch (IOException | IllegalStateException e) {
                // The client is gone or the response can't take a body any more; there is nobody left to tell
            }
        }
    }
}
