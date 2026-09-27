package com.pulso.backend.project.infrastructure;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;
import com.pulso.backend.project.application.MemberInvitationDelivery;

@Component
public class SmtpMemberInvitationDelivery implements MemberInvitationDelivery {
    private final JavaMailSender mail;
    private final String frontendUrl;
    private final String from;

    public SmtpMemberInvitationDelivery(JavaMailSender mail,
            @Value("${pulso.invitation.frontend-url}") String frontendUrl,
            @Value("${pulso.invitation.from}") String from) {
        this.mail = mail;
        this.frontendUrl = frontendUrl.replaceAll("/+$", "");
        this.from = from;
    }

    @Override public void send(String address, String token) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(address);
        message.setSubject("Invitación a un proyecto en Pulso");
        message.setText("Te invitaron a un proyecto. Inicia sesión con este correo y abre: "
                + frontendUrl + "/#invite=" + token + "\nSi no esperabas este correo, ignóralo.");
        mail.send(message);
    }
}
