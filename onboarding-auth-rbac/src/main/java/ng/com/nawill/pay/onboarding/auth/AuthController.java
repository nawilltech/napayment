package ng.com.nawill.pay.onboarding.auth;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Auth", description = "Signup, login, tokens, passwords and transaction PIN.")
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;
    private final TransactionPinService transactionPinService;

    public AuthController(AuthService authService, TransactionPinService transactionPinService) {
        this.authService = authService;
        this.transactionPinService = transactionPinService;
    }

    @Operation(summary = "Sign up as an individual or business owner")
    @PostMapping("/signup")
    public ResponseEntity<AuthResponse> signup(@Valid @RequestBody SignupRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.signup(request));
    }

    @Operation(summary = "Sign up by accepting a team invitation")
    @PostMapping("/signup/accept-invite")
    public ResponseEntity<AuthResponse> signupViaInvite(@Valid @RequestBody AcceptInviteRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.signupViaInvite(request));
    }

    @Operation(summary = "Log in and receive access and refresh tokens")
    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @Operation(summary = "Exchange a refresh token for a new token pair")
    @PostMapping("/refresh")
    public AuthResponse refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return authService.refresh(request);
    }

    @Operation(summary = "Revoke a refresh token")
    @PostMapping("/logout")
    public MessageResponse logout(@Valid @RequestBody RefreshTokenRequest request) {
        authService.logout(request);
        return new MessageResponse("Logged out successfully.");
    }

    @Operation(summary = "Request a password-reset email")
    @PostMapping("/forgot-password")
    public ForgotPasswordResponse forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        return authService.forgotPassword(request.email());
    }

    @Operation(summary = "Reset the password with a reset token")
    @PostMapping("/reset-password")
    public MessageResponse resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request);
        return new MessageResponse("Password has been reset successfully.");
    }

    @Operation(summary = "Change the signed-in user's password")
    @PostMapping("/change-password")
    public MessageResponse changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        authService.changePassword(request);
        return new MessageResponse("Password changed successfully.");
    }

    @Operation(summary = "Set or change the signed-in user's transaction PIN")
    @PostMapping("/transaction-pin")
    public MessageResponse setTransactionPin(@Valid @RequestBody SetTransactionPinRequest request) {
        transactionPinService.setOrChangePin(request);
        return new MessageResponse("Transaction PIN set successfully.");
    }
}
