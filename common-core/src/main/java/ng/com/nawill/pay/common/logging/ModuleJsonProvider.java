package ng.com.nawill.pay.common.logging;

import ch.qos.logback.classic.spi.ILoggingEvent;
import com.fasterxml.jackson.core.JsonGenerator;
import java.io.IOException;
import net.logstash.logback.composite.AbstractJsonProvider;

/**
 * Derives a "module" field for every JSON log line from the logger's
 * package, e.g. "ng.com.nawill.pay.payments.transaction.TransactionService"
 * -> "payments". Wired in logback-spring.xml so every module gets this field
 * for free, without per-call-site bookkeeping.
 */
public class ModuleJsonProvider extends AbstractJsonProvider<ILoggingEvent> {

    private static final String BASE_PACKAGE = "ng.com.nawill.pay.";
    private static final String FIELD_NAME = "module";

    @Override
    public void writeTo(JsonGenerator generator, ILoggingEvent event) throws IOException {
        generator.writeStringField(FIELD_NAME, resolveModule(event.getLoggerName()));
    }

    private String resolveModule(String loggerName) {
        if (loggerName == null || !loggerName.startsWith(BASE_PACKAGE)) {
            return "root";
        }
        String remainder = loggerName.substring(BASE_PACKAGE.length());
        int dotIndex = remainder.indexOf('.');
        return dotIndex > 0 ? remainder.substring(0, dotIndex) : remainder;
    }
}
