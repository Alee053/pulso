package com.pulso.backend.notification.domain;

/**
 * Kinds of notice a person can receive. The title is a stable English label
 * stored with the notice; clients present the notice from its type.
 */
public enum NotificationType {
    NEW_MESSAGE("New message"),
    USER_INTERESTED("A person is interested"),
    MEMBERSHIP_UPDATE("Membership update"),
    PROJECT_APPROVED("Project approved"),
    PROJECT_VERIFIED("Project verified"),
    ADMIN_UPDATE("Administrative update"),
    CONNECTION_REQUEST("New connection request"),
    CONNECTION_RESPONSE("Connection request answered");

    private final String title;

    NotificationType(String title) {
        this.title = title;
    }

    public String title() {
        return title;
    }
}
