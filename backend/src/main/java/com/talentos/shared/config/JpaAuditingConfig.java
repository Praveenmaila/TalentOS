package com.talentos.shared.config;

import com.talentos.shared.security.UserPrincipal;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;
import java.util.UUID;

/**
 * Activates Spring Data JPA auditing for the entire application.
 *
 * <p>Enables automatic population of:
 * <ul>
 *   <li>{@code @CreatedDate}      → {@code created_at}</li>
 *   <li>{@code @LastModifiedDate} → {@code updated_at}</li>
 *   <li>{@code @CreatedBy}        → {@code created_by}</li>
 *   <li>{@code @LastModifiedBy}   → {@code updated_by}</li>
 * </ul>
 * on every entity that extends {@link com.talentos.shared.entity.AuditableEntity}.
 *
 * <p>The {@link AuditorAware} bean resolves the current actor's UUID by reading
 * the authenticated {@link UserPrincipal} from the Spring Security context.
 * Returns {@link Optional#empty()} for unauthenticated or anonymous operations
 * (e.g., the initial admin seed at startup) — resulting in a {@code NULL}
 * {@code created_by} / {@code updated_by}, which is valid per the ERD.
 */
@Configuration
@EnableJpaAuditing(auditorAwareRef = "auditorAware")
public class JpaAuditingConfig {

    /**
     * Resolves the UUID of the currently authenticated user from the
     * Spring Security {@link SecurityContextHolder}.
     *
     * <p>Resolution logic:
     * <ol>
     *   <li>Read the {@link Authentication} from the security context.</li>
     *   <li>If authentication is null, not authenticated, or the principal is not a
     *       {@link UserPrincipal}, return {@link Optional#empty()}.</li>
     *   <li>Otherwise return the user's UUID wrapped in {@link Optional}.</li>
     * </ol>
     *
     * <p>This bean is referenced by name {@code "auditorAware"} in the
     * {@link EnableJpaAuditing} annotation above.
     *
     * @return {@link AuditorAware} implementation typed to {@link UUID}
     */
    @Bean
    public AuditorAware<UUID> auditorAware() {
        return () -> {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

            if (authentication == null
                    || !authentication.isAuthenticated()
                    || !(authentication.getPrincipal() instanceof UserPrincipal principal)) {
                return Optional.empty();
            }

            return Optional.of(principal.getId());
        };
    }
}
