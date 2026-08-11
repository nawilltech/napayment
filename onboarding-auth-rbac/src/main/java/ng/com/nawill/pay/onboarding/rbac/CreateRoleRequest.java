package ng.com.nawill.pay.onboarding.rbac;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record CreateRoleRequest(@NotBlank String name, @NotEmpty List<String> permissionNames) {
}
