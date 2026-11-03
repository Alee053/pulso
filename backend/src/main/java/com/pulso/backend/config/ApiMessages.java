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
    public static final String INVALID_OPPORTUNITY_TITLE = "Invalid opportunity";
    public static final String OPPORTUNITY_NOT_FOUND_TITLE = "Project opportunity not found";
    public static final String OPPORTUNITY_CONFLICT_TITLE = "Project opportunity conflict";
    public static final String OPPORTUNITY_STATE_CONFLICT_TITLE = "Invalid opportunity transition";
    public static final String OPPORTUNITY_SKILL_CONFLICT_TITLE = "Opportunity skill conflict";
    public static final String OPPORTUNITY_SKILL_NOT_FOUND_TITLE = "Opportunity skill not found";
    public static final String PROJECT_CONFLICT_TITLE = "Project conflict";
    public static final String CONNECTION_INTENT_FORBIDDEN_TITLE = "Connection request not permitted";
    public static final String CONNECTION_INTENT_FORBIDDEN_DETAIL =
            "The authenticated profile cannot create connection requests.";
    public static final String CONNECTION_INTENT_FORBIDDEN_CODE = "connection_intent_forbidden";
    public static final String CONNECTION_CONTEXT_UNAVAILABLE_TITLE = "Connection context unavailable";
    public static final String CONNECTION_CONTEXT_UNAVAILABLE_DETAIL =
            "The project or opportunity does not exist or cannot receive requests.";
    public static final String CONNECTION_CONTEXT_UNAVAILABLE_CODE = "connection_context_unavailable";
    public static final String CONNECTION_INTENT_DUPLICATE_TITLE =
            "Pending connection request already exists";
    public static final String CONNECTION_INTENT_DUPLICATE_DETAIL =
            "A pending connection request already exists for this context.";
    public static final String CONNECTION_INTENT_DUPLICATE_CODE = "connection_intent_duplicate";

    public static final String FEATURE_FLAG_ADMIN_REQUIRED_DETAIL =
            "A platform administrator is required to change feature flags.";
    public static final String FEATURE_FLAG_ADMIN_REQUIRED_TITLE = "Feature flag access denied";
    public static final String FEATURE_FLAG_ADMIN_REQUIRED_CODE = "feature_flag_admin_required";
    public static final String FEATURE_FLAG_NOT_FOUND_DETAIL = "Feature flag not found.";
    public static final String FEATURE_FLAG_NOT_FOUND_TITLE = "Feature flag not found";
    public static final String FEATURE_FLAG_NOT_FOUND_CODE = "feature_flag_not_found";
    public static final String FEATURE_FLAG_BOOLEAN_REQUIRED = "enabled must be a boolean value.";
    public static final String FEATURE_FLAG_INVALID_TITLE = "Invalid feature flag";
    public static final String FEATURE_FLAG_INVALID_CODE = "invalid_feature_flag";
    public static final String FEATURE_DISABLED_TITLE = "Feature unavailable";
    public static final String FEATURE_DISABLED_DETAIL = "The %s feature is disabled.";
    public static final String FEATURE_DISABLED_CODE = "feature_disabled";
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
    public static final String INVALID_DISCOVERY_CURSOR = "cursor is invalid.";

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
    public static final String PREFERENCES_CATALOG_ID_INVALID =
            "%s must contain valid catalog UUIDs.";
    public static final String PREFERENCES_WEIGHTS_INVALID =
            "recommendationWeights contains an invalid value.";
    public static final String PREFERENCES_WEIGHTS_TOO_MANY =
            "recommendationWeights contains too many entries.";

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

    public static final String CONNECTION_INTENT_NOT_FOUND_TITLE = "Connection request not found";
    public static final String CONNECTION_INTENT_NOT_FOUND_DETAIL =
            "The connection request does not exist.";
    public static final String CONNECTION_INTENT_PERMISSION_TITLE = "Connection request access denied";
    public static final String CONNECTION_INTENT_MEMBERSHIP_REQUIRED =
            "Active project membership is required to accept the connection request.";
    public static final String CONNECTION_INTENT_PROFILES_BLOCKED =
            "Blocked profiles cannot start a new conversation.";
    public static final String CONNECTION_INTENT_PERMISSION_CODE = "connection_intent_forbidden";
    public static final String CONNECTION_INTENT_CONFLICT_TITLE = "Connection request conflict";
    public static final String CONNECTION_INTENT_CONFLICT_DETAIL =
            "The connection request cannot be accepted in its current state.";
    public static final String CONNECTION_INTENT_CONFLICT_CODE = "connection_intent_conflict";

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
    public static final String INVITATION_PROJECT_NOT_FOUND_TITLE = "Project not found";
    public static final String INVITATION_NOT_FOUND_TITLE = "Member invitation";
    public static final String INVITATION_NOT_FOUND_DETAIL =
            "invitation not found or already used.";
    public static final String INVITATION_PERMISSION_TITLE = "Membership permission";
    public static final String INVITATION_CONFLICT_TITLE = "Invitation conflict";
    public static final String INVITATION_CONFLICT_CODE = "invitation_duplicate";

    public static final String MEMBERSHIP_PERMISSION_TITLE = "Membership permission";
    public static final String MEMBERSHIP_READ_PERMISSION_DETAIL = "active project membership is required.";
    public static final String MEMBERSHIP_PERMISSION_DETAIL =
            "active OWNER or ADMIN membership is required.";
    public static final String MEMBERSHIP_SELF_ELEVATION = "cannot elevate own role.";
    public static final String MEMBERSHIP_INVALID_TRANSITION_TITLE =
            "Invalid membership transition";
    public static final String MEMBERSHIP_OWNER_REQUIRED_TITLE =
            "Membership owner required";
    public static final String MEMBERSHIP_OWNER_REQUIRED_DETAIL =
            "project must keep at least one active OWNER.";
    public static final String MEMBERSHIP_PERMISSION_CODE = "membership_permission";
    public static final String MEMBERSHIP_INVALID_TRANSITION_CODE =
            "membership_invalid_transition";
    public static final String MEMBERSHIP_OWNER_REQUIRED_CODE = "membership_owner_required";
    public static final String MEMBERSHIP_CONFLICT_TITLE = "Membership conflict";
    public static final String MEMBERSHIP_CONFLICT_CODE = "membership_conflict";
    public static final String MEMBERSHIP_CONFLICT_DETAIL =
            "The membership changed before the action completed.";

    public static final String CANNOT_ACTIVATE_NO_PROFILE =
            "cannot activate a membership without a profile.";
    public static final String CANNOT_ACTIVATE_STATUS =
            "cannot activate a membership with status %s.";
    public static final String CANNOT_CHANGE_ROLE_STATUS =
            "cannot change role of a membership with status %s.";
    public static final String CANNOT_WITHDRAW_STATUS =
            "cannot withdraw a membership with status %s.";
    public static final String MEMBERSHIP_REQUIRED = "membership is required.";
    public static final String MEMBERSHIP_ID_REQUIRED = "memberId is required.";

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
    public static final String PROJECT_LIFECYCLE_CONFLICT_TITLE =
            "Project state conflict";
    public static final String PROJECT_LIFECYCLE_CONFLICT_DETAIL =
            "The project state changed before the action completed.";

    public static final String AUTHENTICATED_PROFILE_REQUIRED =
            "authenticatedProfileId is required.";
    public static final String PREFERENCES_REQUIRED = "preferences are required.";
    public static final String PREFERENCES_WRONG_PROFILE =
            "preferences do not belong to the authenticated profile.";
    public static final String SECTOR_UNKNOWN =
            "preferredSectors contains unknown or inactive sector '%s'.";
    public static final String SECTOR_DUPLICATE =
            "preferredSectors must not contain duplicates: '%s'.";
    public static final String TAG_UNKNOWN =
            "preferredTags contains an unknown or inactive tag.";
    public static final String SKILL_UNKNOWN =
            "preferredSkills contains an unknown or inactive skill.";
    public static final String SKILL_PROFICIENCY_INVALID =
            "skills proficiency must be BEGINNER, INTERMEDIATE, ADVANCED or EXPERT.";
    public static final String SKILL_PROFICIENCY_REQUIRED =
            "Every selected skill must have a proficiency level.";
    public static final String SKILL_SELECTION_REQUIRED = "skills must contain skill selections.";
    public static final String SKILL_SELECTION_AMBIGUOUS = "Use either skills or preferredSkills, not both.";
    public static final String ONBOARDING_INTENTION_REQUIRED =
            "At least one intention is required to complete onboarding.";
    public static final String PROJECT_DRAFT_REQUIRED = "project draft is required.";
    public static final String PROJECT_UPDATE_REQUIRED = "project update is required.";
    public static final String SECTOR_MUST_BE_ACTIVE =
            "sectorId must reference an active sector.";
    public static final String FIELD_NOT_ACCEPTED = "Field '%s' is not accepted.";
    public static final String REQUEST_BODY_REQUIRED = "request body is required.";

    public static final String STORED_PREFERENCES_INVALID_JSON =
            "Stored onboarding preferences contain invalid JSON.";
    public static final String PREFERENCES_SERIALIZE_FAILED =
            "Onboarding preferences could not be serialized.";

    public static final String KNOWN_VALUE_REQUIRED = "%s is required.";
    public static final String KNOWN_VALUE_UNKNOWN =
            "%s contains unknown value '%s'.";
}
