package ng.com.nawill.pay.onboarding.rbac;

import jakarta.validation.Valid;
import java.net.URI;
import ng.com.nawill.pay.common.web.PageDefaults;
import ng.com.nawill.pay.common.web.PageResponse;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/roles")
public class RoleController {

    private final RoleService roleService;

    public RoleController(RoleService roleService) {
        this.roleService = roleService;
    }

    @PostMapping
    @PreAuthorize("@auth.can('roles:manage')")
    public ResponseEntity<RoleResponse> create(@Valid @RequestBody CreateRoleRequest request) {
        Role role = roleService.createBusinessScopedRole(request);
        return ResponseEntity.created(URI.create("/api/v1/roles/" + role.getId()))
                .body(RoleResponse.from(role, request.permissionNames()));
    }

    @GetMapping
    @PreAuthorize("@auth.can('roles:manage')")
    public PageResponse<RoleResponse> list(@RequestParam(required = false) String term,
                                            @RequestParam(defaultValue = PageDefaults.PAGE) int page,
                                            @RequestParam(defaultValue = PageDefaults.SIZE) int size) {
        return PageResponse.from(roleService.listForCallerBusiness(term, PageRequest.of(page, size))
                .map(role -> RoleResponse.from(role, roleService.permissionNamesOf(role))));
    }
}
