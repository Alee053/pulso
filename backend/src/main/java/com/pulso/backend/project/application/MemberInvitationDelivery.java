package com.pulso.backend.project.application;

public interface MemberInvitationDelivery {
    void send(String address, String token);
}