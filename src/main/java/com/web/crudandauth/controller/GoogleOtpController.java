package com.web.crudandauth.controller;

import com.web.crudandauth.dtos.requests.OtpRequestDto;
import com.web.crudandauth.dtos.response.LoginResponseDto;
import com.web.crudandauth.entities.User;
import com.web.crudandauth.enums.Roles;
import com.web.crudandauth.service.EmailService;
import com.web.crudandauth.service.UserService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;

import java.security.SecureRandom;

@Controller
@RequiredArgsConstructor
public class GoogleOtpController {
    private final UserService  userService;
    private final EmailService emailService;

    // -------------------show google otp page---------------------------------
    @GetMapping("/verify-google-otp")
    public String showGoogleOtp(HttpServletRequest request, Model model) {
        String email = (String) request.getSession().getAttribute("googleEmail");
        if (email == null) {
            return "redirect:/login";
        }
        model.addAttribute("dto", new OtpRequestDto());
        model.addAttribute("maskedEmail", maskEmail(email));
        return "google-otp";
    }
    // ----- verify google otp -----------------------------
    @PostMapping("/verify-google-otp")
    public String verifyGoogleOtp(@ModelAttribute("dto") OtpRequestDto dto, HttpServletRequest request, HttpServletResponse response, Model model) {
        String sessionOtp = (String) request.getSession().getAttribute("googleOtp");
        String email = (String) request.getSession().getAttribute("googleEmail");
        Long otpTime = (Long) request.getSession().getAttribute("googleOtpTime");

        // guard: session expire or missing
        if(sessionOtp==null || email==null || otpTime==null){
            return "redirect:/login";
        }

        // guard: otp expired
        if(System.currentTimeMillis()-otpTime>60_000){
            request.getSession().removeAttribute("googleOtp");
            request.getSession().removeAttribute("googleOtpTime");
            model.addAttribute("error","OTP expired. Please request a new one.");
            model.addAttribute("maskedEmail", maskEmail(email));
            model.addAttribute("dto", new OtpRequestDto());
            return "google-otp";
        }

        // guard: wrong otp
        if(!sessionOtp.equals(dto.getOtp())){
            model.addAttribute("error","Invalid OTP. Please try again. ");
            model.addAttribute("maskedEmail", maskEmail(email));
            model.addAttribute("dto", new OtpRequestDto());
            return "google-otp";
        }

        // ----------- Otp correct -> generate JWT and set Cookie ---------------------------
        User user = userService.findByEmail(email);
        LoginResponseDto loginResult = userService.generateLoginToken(user);

        Cookie cookie = new Cookie("jwt_token", loginResult.getToken());
        cookie.setHttpOnly(true);
        cookie.setPath("/");
        cookie.setMaxAge((int) loginResult.getExpiresInMs() / 1000);
        response.addCookie(cookie);

        request.getSession().invalidate();

        return user.getRole() == Roles.ADMIN
                ? "redirect:/admin/dashboard"
                : "redirect:/user/dashboard";
    }

    // --------------------------------- Resend Google OTP --------------------------------
    @PostMapping("/verify-google-otp/resend")
    public String resendGoogleOtp(HttpServletRequest request) {
        String email = (String) request.getSession().getAttribute("googleEmail");
        if (email == null) {
            return "redirect:/login";
        }
        String otp = generateOtp();
        request.getSession().setAttribute("googleOtp", otp);
        request.getSession().setAttribute("googleOtpTime", System.currentTimeMillis());
        emailService.sendOtp(email, otp);
        return "redirect:/verify-google-otp";
    }

    // ------------------------- Helpers ----------------------------------------------
    private String generateOtp() {
        SecureRandom random = new SecureRandom();
        return String.valueOf(100000 + random.nextInt(900000));
    }

    // Turns "johndoe@gmail.com" → "jo****@gmail.com"
    private String maskEmail(String email) {
        if (email == null || !email.contains("@")) return email;
        String[] parts = email.split("@");
        String local = parts[0];
        String visible = local.length() > 2 ? local.substring(0, 2) : local;
        return visible + "****@" + parts[1];
    }
}
