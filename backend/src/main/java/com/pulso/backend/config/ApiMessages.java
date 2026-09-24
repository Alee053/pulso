package com.pulso.backend.config;

public final class ApiMessages {

    private ApiMessages() {
    }

    public static final String INVALID_REQUEST_TITLE = "Invalid request";
    public static final String INVALID_PARAMETER_DETAIL =
            "Parameter '%s' has an invalid value.";
    public static final String UNREADABLE_BODY_DETAIL = "The request body is invalid.";

    public static final String INVALID_PROFILE_TITLE = "Invalid profile";
    public static final String INVALID_PREFERENCES_TITLE = "Invalid preferences";
    public static final String INVALID_PROJECT_TITLE = "Invalid project";
    public static final String PROJECT_CONFLICT_TITLE = "Project conflict";
    public static final String PUBLIC_PROJECT_NOT_FOUND_TITLE = "Public project not found";
    public static final String PUBLIC_PROJECT_NOT_FOUND_DETAIL =
            "The project does not exist or is not publicly available.";

    public static final String PROFILE_NOT_FOUND_TITLE = "Profile not found";
    public static final String PROFILE_NOT_FOUND_DETAIL =
            "The authenticated profile was not found.";

    public static final String UNAUTHORIZED_TITLE = "Unauthorized";
    public static final String UNAUTHORIZED_DETAIL =
            "The authenticated subject is invalid.";

    public static final String DATASTORE_UNAVAILABLE_TITLE = "Data store unavailable";
    public static final String DATASTORE_UNAVAILABLE_DETAIL =
            "The data store is temporarily unavailable.";

    public static final String JWT_AUDIENCE_REJECTED = "The JWT audience is not accepted.";
    public static final String JWT_ROLE_REJECTED =
            "The JWT role is not accepted for user API access.";

    public static final String INVALID_LIMIT_DETAIL =
            "limit must be between 1 and %d.";
    public static final String INVALID_OFFSET_DETAIL =
            "offset must be between 0 and %d.";

    public static final String PROFILE_BLANK = "%s must not be blank.";
    public static final String PROFILE_MAX_LENGTH =
            "%s must not exceed %d characters.";
    public static final String PROFILE_ENUM =
            "%s must be one of %s.";
    public static final String PROFILE_NOT_EDITABLE =
            "Field '%s' is not editable.";

    public static final String PREFERENCES_REQUIRED_PROFILE = "profileId is required.";
    public static final String PREFERENCES_MUST_BE_ARRAY = "%s must be an array.";
    public static final String PREFERENCES_MAX_ITEMS =
            "%s must not contain more than %d items.";
    public static final String PREFERENCES_STRINGS_ONLY =
            "%s must contain strings only.";
    public static final String PREFERENCES_DUPLICATE =
            "%s must not contain duplicates: '%s'.";
    public static final String PREFERENCES_BLANK_VALUES =
            "%s must not contain blank values.";
    public static final String PREFERENCES_INVALID_VALUE =
            "%s contains an invalid value.";
    public static final String PREFERENCES_UNKNOWN_VALUE =
            "%s contains unknown value '%s'.";
    public static final String PREFERENCES_SECTORS_BLANK =
            "preferredSectors must not contain blank values.";
    public static final String PREFERENCES_SECTORS_INVALID =
            "preferredSectors contains an invalid identifier.";
    public static final String PREFERENCES_SECTORS_INVALID_VALUE =
            "preferredSectors contains an invalid identifier '%s'.";

    public static final String PROJECT_SLUG_MIN_LENGTH =
            "slug must be at least %d characters.";
    public static final String PROJECT_SECTOR_REQUIRED = "sectorId is required.";
    public static final String PROJECT_FIELD_REQUIRED = "%s is required.";
    public static final String PROJECT_FIELD_BLANK = "%s must not be blank.";
    public static final String PROJECT_FIELD_MAX_LENGTH =
            "%s must not exceed %d characters.";
    public static final String PROJECT_FIELD_ENUM =
            "%s must be one of %s.";

    public static final String PROJECT_CONFLICT_DETAIL =
            "A project with the same slug already exists.";

    public static final String INVITATION_REQUIRED = "invitation is required.";
    public static final String INVITE_TOKEN_REQUIRED = "invite token is required.";
    public static final String SHA256_REQUIRED =
            "SHA-256 is required to digest invite tokens.";
    public static final String INVITE_TOKEN_DIGEST_REQUIRED =
            "inviteTokenDigest is required.";
    public static final String INVITE_TOKEN_DIGEST_INVALID =
            "inviteTokenDigest must be a SHA-256 hex digest.";
    public static final String PROJECT_ID_REQUIRED = "projectId is required.";
    public static final String CREATED_BY_REQUIRED = "createdByProfileId is required.";
    public static final String PROFILE_OR_EMAIL_REQUIRED =
            "profileId or inviteeEmail is required.";
    public static final String PROFILE_MUST_BE_EMPTY =
            "profileId must be empty until the invitation is accepted.";
    public static final String INVITEE_EMAIL_REQUIRED = "inviteeEmail is required.";
    public static final String ROLE_MUST_BE_MEMBER = "role must be MEMBER.";
    public static final String PROFILE_ID_REQUIRED = "profileId is required.";
    public static final String CANNOT_ACCEPT_STATUS =
            "cannot accept a membership with status %s.";
    public static final String INVITEE_EMAIL_MAX_LENGTH =
            "inviteeEmail must not exceed %d characters.";
    public static final String INVITEE_EMAIL_INVALID = "inviteeEmail is invalid.";
    public static final String INVITATION_PROJECT_NOT_FOUND = "project does not exist.";
    public static final String DUPLICATE_INVITATION =
            "an active invitation already exists for this project and email.";

    public static final String MEMBERSHIP_PERMISSION_TITLE = "Membership permission";
    public static final String MEMBERSHIP_PERMISSION_DETAIL =
            "active OWNER or ADMIN membership is required.";
    public static final String MEMBERSHIP_SELF_ELEVATION = "cannot elevate own role.";
    public static final String MEMBERSHIP_INVALID_TRANSITION_TITLE =
            "Invalid membership transition";
    public static final String MEMBERSHIP_OWNER_REQUIRED_TITLE =
            "Membership owner required";
    public static final String MEMBERSHIP_OWNER_REQUIRED_DETAIL =
            "project must keep at least one active OWNER.";

    public static final String CANNOT_ACTIVATE_NO_PROFILE =
            "cannot activate a membership without a profile.";
    public static final String CANNOT_ACTIVATE_STATUS =
            "cannot activate a membership with status %s.";
    public static final String CANNOT_CHANGE_ROLE_STATUS =
            "cannot change role of a membership with status %s.";
    public static final String CANNOT_WITHDRAW_STATUS =
            "cannot withdraw a membership with status %s.";
    public static final String MEMBERSHIP_REQUIRED = "membership is required.";

    public static final String CANNOT_REQUEST_PUBLICATION_FROM =
            "cannot request publication from %s.";
    public static final String CANNOT_ARCHIVE_FROM = "cannot archive from %s.";
    public static final String ARCHIVED_AT_REQUIRED = "archivedAt is required.";
    public static final String LIFECYCLE_STATE_REQUIRED =
            "project lifecycle state is required.";
    public static final String MEMBERSHIP_STATUS_MUST_BE_ACTIVE =
            "membershipStatus must be ACTIVE.";
    public static final String ROLE_MUST_BE_OWNER_OR_ADMIN =
            "role must be OWNER or ADMIN.";

    public static final String AUTHENTICATED_PROFILE_REQUIRED =
            "authenticatedProfileId is required.";
    public static final String PREFERENCES_REQUIRED = "preferences are required.";
    public static final String PREFERENCES_WRONG_PROFILE =
            "preferences do not belong to the authenticated profile.";
    public static final String SECTOR_UNKNOWN =
            "preferredSectors contains unknown or inactive sector '%s'.";
    public static final String SECTOR_DUPLICATE =
            "preferredSectors must not contain duplicates: '%s'.";
    public static final String PROJECT_DRAFT_REQUIRED = "project draft is required.";
    public static final String SECTOR_MUST_BE_ACTIVE =
            "sectorId must reference an active sector.";
    public static final String FIELD_NOT_ACCEPTED = "Field '%s' is not accepted.";

    public static final String STORED_PREFERENCES_INVALID_JSON =
            "Stored onboarding preferences contain invalid JSON.";
    public static final String PREFERENCES_SERIALIZE_FAILED =
            "Onboarding preferences could not be serialized.";

    public static final String KNOWN_VALUE_REQUIRED = "%s is required.";
    public static final String KNOWN_VALUE_UNKNOWN =
            "%s contains unknown value '%s'.";
}
