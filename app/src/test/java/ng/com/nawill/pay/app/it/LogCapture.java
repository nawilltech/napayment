package ng.com.nawill.pay.app.it;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.util.List;

/** Read-only view over log events captured during a single test method. */
public final class LogCapture {

    private final ListAppender<ILoggingEvent> appender;

    LogCapture(ListAppender<ILoggingEvent> appender) {
        this.appender = appender;
    }

    public List<ILoggingEvent> events() {
        return appender.list;
    }

    public boolean anyEventHasMdcValue(String key, String value) {
        return events().stream().anyMatch(event -> value.equals(event.getMDCPropertyMap().get(key)));
    }
}
