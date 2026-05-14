package com.web.crudandauth.util.handler;

import com.web.crudandauth.entities.User;
import com.web.crudandauth.enums.CreatedBy;
import com.web.crudandauth.enums.Roles;
import com.web.crudandauth.repositories.UserRepositories;
import com.web.crudandauth.service.EmailService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.security.SecureRandom;
import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
public class OAuth2SuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final EmailService emailService;
    private final UserRepositories userRepositories;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException {

        OAuth2User oauthUser = (OAuth2User) authentication.getPrincipal();
        String email    = oauthUser.getAttribute("email");
        String fullName = oauthUser.getAttribute("name");

        // ── Register or validate ──────────────────────────────────────────
        var existingUser = userRepositories.findByEmail(email);

        if (existingUser.isPresent()) {
            // Email exists but was registered locally → block, redirect with error
            if (existingUser.get().getCreatedBy() != CreatedBy.GOOGLE) {
                response.sendRedirect("/login?oauthConflict=true");
                return;                          // ← stop here, no OTP
            }
            // Existing OAuth user → fall through to OTP
        } else {
            // Brand new user → register
            String firstName = fullName != null ? fullName : "";
            String lastName  = "";
            if (fullName != null && fullName.contains(" ")) {
                int space = fullName.indexOf(' ');
                firstName = fullName.substring(0, space);
                lastName  = fullName.substring(space + 1);
            }

            userRepositories.save(User.builder()
                    .firstName(firstName)
                    .lastName(lastName)
                    .email(email)
                    .password("")
                    .createdBy(CreatedBy.GOOGLE)
                    .createdAt(LocalDateTime.now())
                    .role(Roles.USER)
                    .build());
        }

        // ── Generate & send OTP (only reached if not blocked above) ──────
        String otp = generateOtp();

        HttpSession session = request.getSession(true);
        session.setAttribute("googleEmail",   email);
        session.setAttribute("googleOtp",     otp);
        session.setAttribute("googleOtpTime", System.currentTimeMillis());

        emailService.sendOtp(email, otp);
        response.sendRedirect("/verify-google-otp");
    }

    private String generateOtp() {
        SecureRandom random = new SecureRandom();
        return String.valueOf(100000 + random.nextInt(900000));
    }
}