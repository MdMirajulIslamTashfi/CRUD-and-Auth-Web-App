package com.web.crudandauth.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {
    private final JavaMailSender javaMailSender;
    public void sendOtp(String toEmail, String otp){
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(toEmail);
        message.setSubject("OTP Code: " + otp);
        message.setText("Your verification code is: " + otp + "\n\nThis OTP will expire in 1 minute." );
        log.info("OTP sent to {}: {}", toEmail, otp);
        javaMailSender.send(message);
    }
}
