package ng.com.nawill.pay.common.web;

/** Shared {@code @RequestParam(defaultValue = ...)} values for every paginated list endpoint. */
public final class PageDefaults {

    public static final String PAGE = "0";
    public static final String SIZE = "20";

    private PageDefaults() {
    }
}
