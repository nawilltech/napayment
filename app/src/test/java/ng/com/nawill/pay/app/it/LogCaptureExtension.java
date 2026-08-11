package ng.com.nawill.pay.app.it;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;
import org.slf4j.LoggerFactory;

/**
 * Attaches an in-memory appender to the root logback logger for the
 * duration of each test, so a test can assert what was actually logged -
 * used to verify requestId propagation into structured log output (doc 3
 * §7) without parsing the JSON console stream.
 */
public class LogCaptureExtension implements BeforeEachCallback, AfterEachCallback, ParameterResolver {

    private static final ExtensionContext.Namespace NAMESPACE = ExtensionContext.Namespace.create(LogCaptureExtension.class);

    @Override
    public void beforeEach(ExtensionContext context) {
        Logger rootLogger = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        rootLogger.addAppender(appender);
        context.getStore(NAMESPACE).put(ListAppender.class, appender);
    }

    @Override
    public void afterEach(ExtensionContext context) {
        Logger rootLogger = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        rootLogger.detachAppender(appender(context));
    }

    @Override
    public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext) {
        return parameterContext.getParameter().getType() == LogCapture.class;
    }

    @Override
    public Object resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext)
            throws ParameterResolutionException {
        return new LogCapture(appender(extensionContext));
    }

    @SuppressWarnings("unchecked")
    private ListAppender<ILoggingEvent> appender(ExtensionContext context) {
        return (ListAppender<ILoggingEvent>) context.getStore(NAMESPACE).get(ListAppender.class);
    }
}
