package ng.com.nawill.pay.onboarding.team;

public record OkResponse(boolean ok) {

    public static final OkResponse OK = new OkResponse(true);
}
