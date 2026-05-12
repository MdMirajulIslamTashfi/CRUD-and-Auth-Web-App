package com.web.crudandauth.controller;

import com.web.crudandauth.dtos.requests.LoginRequestDto;
import com.web.crudandauth.dtos.requests.OtpRequestDto;
import com.web.crudandauth.dtos.requests.RegisterRequestDto;
import com.web.crudandauth.dtos.response.LoginResponseDto;
import com.web.crudandauth.dtos.response.PaginationResponseDto;
import com.web.crudandauth.entities.User;
import com.web.crudandauth.enums.Roles;
import com.web.crudandauth.server.WeatherServerClient;
import com.web.crudandauth.service.EmailService;
import com.web.crudandauth.service.UserService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.security.SecureRandom;
import java.util.Arrays;

@Slf4j
@Controller
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final WeatherServerClient weatherServerClient;
    private final EmailService emailService;

    // ── ROOT ──────────────────────────────────────────────────────────────────
    @GetMapping("/index")
    public String root() {
        return "index";
    }

    // ── REGISTER — show form ──────────────────────────────────────────────────
    @GetMapping("/register")
    public String showRegister(Model model) {
        model.addAttribute("dto", new RegisterRequestDto());
        return "register";
    }

    // ── REGISTER — handle submit ──────────────────────────────────────────────
    @PostMapping("/register")
    public String handleRegister(@ModelAttribute("dto") RegisterRequestDto dto, Model model) {
        try {
            if (!dto.getPassword().equals(dto.getConfirmPassword())) {
                model.addAttribute("error", "Passwords don't match");
                model.addAttribute("dto", dto);
                return "register";
            }
            userService.register(dto);
            return "redirect:/login?registered=true";
        } catch (Exception ex) {
            model.addAttribute("error", ex.getMessage());
            model.addAttribute("dto", dto);
            return "register";
        }
    }

    // ── LOGIN — show form ─────────────────────────────────────────────────────
    @GetMapping("/login")
    public String showLogin(Model model) {
        model.addAttribute("dto", new LoginRequestDto());
        return "login";
    }

    // ── LOGIN — handle submit ─────────────────────────────────────────────────
    @PostMapping("/login")
    public String handleLogin(@ModelAttribute("dto") LoginRequestDto dto,
                              HttpServletRequest request,
                              Model model) {

        try {
            LoginResponseDto result = userService.login(dto);

            // store email temporarily in session
            request.getSession().setAttribute("email", result.getEmail());

            // dynamic otp
            String otp = generateOtp();
            request.getSession().setAttribute("otp", otp);
            request.getSession().setAttribute("otpTime", System.currentTimeMillis());
            emailService.sendOtp(result.getEmail(), otp);

            return "redirect:/otp";

        } catch (Exception ex) {
            model.addAttribute("error", ex.getMessage());
            model.addAttribute("dto", dto);
            return "login";
        }
    }

    // -------------------OTP Page -------------------------
    @GetMapping("/otp")
    public String showOtpPage(Model model, HttpServletRequest request) {
        String email = (String) request.getSession().getAttribute("email");
        if (email == null) {
            return "redirect:/login";
        }
        model.addAttribute("dto", new OtpRequestDto());
        return "otp";
    }

    // ------------------ OTP Validation ---------------------------
    @PostMapping("/otp")
    public String verifyOtp(@ModelAttribute("dto") OtpRequestDto dto,
                            HttpServletRequest request,
                            HttpServletResponse response,
                            Model model) {

        String sessionOtp = (String) request.getSession().getAttribute("otp");
        String email = (String) request.getSession().getAttribute("email");
        Long otpTime = (Long) request.getSession().getAttribute("otpTime");

        if (sessionOtp == null || email == null || otpTime == null) {
            return "redirect:/login";
        }

        long currentTime = System.currentTimeMillis();

        if (currentTime - otpTime > 60000) {

            request.getSession().removeAttribute("otp");
            request.getSession().removeAttribute("otpTime");

            model.addAttribute("error", "OTP expired. Please request a new OTP.");
            return "otp";
        }

        if (!sessionOtp.equals(dto.getOtp())) {
            model.addAttribute("error", "Invalid OTP");
            return "otp";
        }

        // OTP verified successfully

        User user = userService.findByEmail(email);

        LoginResponseDto loginResult = userService.generateLoginToken(user);

        Cookie cookie = new Cookie("jwt_token", loginResult.getToken());

        cookie.setHttpOnly(true);
        cookie.setPath("/");
        cookie.setMaxAge((int) (loginResult.getExpiresInMs() / 1000));

        response.addCookie(cookie);

        // clear session otp
        request.getSession().invalidate();

        if (user.getRole() == Roles.ADMIN) {
            return "redirect:/admin/dashboard";
        }

        return "redirect:/user/dashboard";
    }

    @PostMapping("/otp/resend")
    public String resendOtp(HttpServletRequest request) {
        String email = (String) request.getSession().getAttribute("email");
        if (email == null) {
            return "redirect:/login";
        }

        String otp = generateOtp();
        request.getSession().setAttribute("otp", otp);
        request.getSession().setAttribute("otpTime", System.currentTimeMillis());
        emailService.sendOtp(email, otp);

        return "redirect:/otp";
    }

    // ── LOGOUT ────────────────────────────────────────────────────────────────
    @PostMapping("/logout")
    public String logout(HttpServletResponse response) {
        Cookie cookie = new Cookie("jwt_token", "");
        cookie.setHttpOnly(true);
        cookie.setPath("/");
        cookie.setMaxAge(0);
        response.addCookie(cookie);
        SecurityContextHolder.clearContext();
        return "redirect:/login?logout=true";
    }

    // ── USER DASHBOARD ────────────────────────────────────────────────────────
    @GetMapping("/user/dashboard")
    public String userDashboard(@RequestParam(value = "city", defaultValue = "Dhaka") String city,
                                Model model) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        User user = userService.findByEmail(auth.getName());
        model.addAttribute("user", user);
        model.addAttribute("city", city);
        try {
            WeatherServerClient.WeatherResult weather = weatherServerClient.getWeather(city);
            model.addAttribute("weather", weather);
            model.addAttribute("weatherCondition", deriveCondition(weather.temperature()));
        } catch (Exception ex) {
            log.warn("Weather fetch failed: {}", ex.getMessage());
            model.addAttribute("weatherError", "Weather service unavailable");
        }
        return "user-dashboard";
    }

    // ── USER PROFILE ──────────────────────────────────────────────────────────
    @GetMapping("/user/profile")
    public String viewProfile(Model model) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        model.addAttribute("user", userService.findByEmail(auth.getName()));
        return "profile";
    }

    // ── EDIT PROFILE — show form ──────────────────────────────────────────────
    @GetMapping("/user/edit-profile")
    public String showEditProfile(Model model) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        User user = userService.findByEmail(auth.getName());
        RegisterRequestDto dto = new RegisterRequestDto();
        dto.setHonor(user.getHonor());
        dto.setFirstName(user.getFirstName());
        dto.setLastName(user.getLastName());
        dto.setGender(user.getGender());
        dto.setEmail(user.getEmail());
        model.addAttribute("dto", dto);
        return "edit-profile";
    }

    // ── EDIT PROFILE — handle submit ──────────────────────────────────────────
    @PostMapping("/user/edit-profile")
    public String handleEditProfile(@ModelAttribute("dto") RegisterRequestDto dto, Model model) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        try {
            userService.updateProfile(auth.getName(), dto);
            return "redirect:/user/profile?updated=true";
        } catch (Exception ex) {
            model.addAttribute("error", ex.getMessage());
            model.addAttribute("dto", dto);
            return "edit-profile";
        }
    }

    // ── ADMIN DASHBOARD ───────────────────────────────────────────────────────
    @GetMapping("/admin/dashboard")
    public String adminDashboard(
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "")   String q,
            Model model) {

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        model.addAttribute("admin", userService.findByEmail(auth.getName()));

        PaginationResponseDto usersPage = userService.getUsers(page, size, q);
        model.addAttribute("usersPage",  usersPage);
        model.addAttribute("q",          q);
        model.addAttribute("size",       size);
        model.addAttribute("adminCount", userService.countByRole(Roles.ADMIN));
        model.addAttribute("userCount",  userService.countByRole(Roles.USER));

        return "admin-dashboard";
    }

    // ── ADMIN REGISTER USER — show form ──────────────────────────────────────
    @GetMapping("/admin/register")
    public String showAdminRegister(Model model) {
        model.addAttribute("dto", new RegisterRequestDto());
        model.addAttribute("roles", Roles.values());
        return "admin-register-user";
    }

    // ── ADMIN REGISTER USER — handle submit ───────────────────────────────────
    @PostMapping("/admin/register")
    public String handleAdminRegister(@ModelAttribute("dto") RegisterRequestDto dto, Model model) {
        try {
            if (!dto.getPassword().equals(dto.getConfirmPassword())) {
                model.addAttribute("error", "Passwords don't match");
                model.addAttribute("dto", dto);
                model.addAttribute("roles", Roles.values());
                return "admin-register-user";
            }
            userService.adminRegisterUser(dto);
            return "redirect:/admin/dashboard?registered=true";
        } catch (Exception ex) {
            model.addAttribute("error", ex.getMessage());
            model.addAttribute("dto", dto);
            model.addAttribute("roles", Roles.values());
            return "admin-register-user";
        }
    }

    // ── ADMIN EDIT USER — show form ───────────────────────────────────────────
    @GetMapping("/admin/edit/{id}")
    public String showAdminEdit(@PathVariable String id, Model model) {
        User user = userService.findById(id);
        RegisterRequestDto dto = new RegisterRequestDto();
        dto.setId(user.getId());
        dto.setHonor(user.getHonor());
        dto.setFirstName(user.getFirstName());
        dto.setLastName(user.getLastName());
        dto.setGender(user.getGender());
        dto.setEmail(user.getEmail());
        dto.setRole(user.getRole());
        model.addAttribute("dto", dto);
        model.addAttribute("roles", Roles.values());
        return "admin-edit-user";
    }

    // ── ADMIN EDIT USER — handle submit ───────────────────────────────────────
    @PostMapping("/admin/edit/{id}")
    public String handleAdminEdit(@PathVariable String id,
                                  @ModelAttribute("dto") RegisterRequestDto dto,
                                  Model model) {
        try {
            userService.adminUpdateUser(id, dto);
            return "redirect:/admin/dashboard?updated=true";
        } catch (Exception ex) {
            model.addAttribute("error", ex.getMessage());
            model.addAttribute("dto", dto);
            model.addAttribute("roles", Roles.values());
            return "admin-edit-user";
        }
    }

    // -------------------------- ADMIN DELETE USER ----------------------------------------
    @PostMapping("/admin/delete/{id}")
    public String deleteUser(@PathVariable String id) {
        userService.deleteById(id);
        return "redirect:/admin/dashboard?deleted=true";
    }

    // -------------------- HELPERS ----------------------------------------
    private String generateOtp() {
            SecureRandom random = new SecureRandom();
            int otp = 100000 + random.nextInt(900000);
            return String.valueOf(otp);
    }

    private String deriveCondition(double temp) {
        if (temp >= 35) return "Scorching";
        if (temp >= 30) return "Hot";
        if (temp >= 25) return "Warm";
        if (temp >= 20) return "Sunny";
        if (temp >= 15) return "Cloudy";
        if (temp >= 10) return "Overcast";
        return "Cold";
    }

    public static String extractTokenFromCookies(HttpServletRequest request) {
        if (request.getCookies() == null) return null;
        return Arrays.stream(request.getCookies())
                .filter(c -> "jwt_token".equals(c.getName()))
                .map(Cookie::getValue)
                .findFirst()
                .orElse(null);
    }
}