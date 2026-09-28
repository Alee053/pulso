package com.pulso.backend.discovery.api;

import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.pulso.backend.config.ApiMessages;
import com.pulso.backend.preference.domain.OnboardingPreferencesValidationException;
import com.pulso.backend.profile.application.InvalidAuthenticatedSubjectException;
import com.pulso.backend.profile.application.ProfileNotFoundException;
import com.pulso.backend.profile.domain.ProfileValidationException;
import com.pulso.backend.project.application.MembershipConflictException;
import com.pulso.backend.project.application.ProjectConflictException;
import com.pulso.backend.project.application.ProjectLifecycleAuthorizationException;
import com.pulso.backend.project.application.ProjectLifecycleConflictException;
import com.pulso.backend.project.application.ProjectUpdateAuthorizationException;
import com.pulso.backend.project.application.PublicProjectNotFoundException;
import com.pulso.backend.project.domain.DuplicateMemberInvitationException;
import com.pulso.backend.project.domain.MembershipInvalidTransitionException;
import com.pulso.backend.project.domain.MembershipOwnerInvariantException;
import com.pulso.backend.project.domain.MembershipPermissionException;
import com.pulso.backend.project.domain.MemberInvitationPermissionException;
import com.pulso.backend.project.domain.MemberInvitationProjectNotFoundException;
import com.pulso.backend.project.domain.ProjectValidationException;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(InvalidRequestException.class)
    ProblemDetail invalidRequest(InvalidRequestException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                exception.getMessage());
        problem.setTitle(ApiMessages.INVALID_REQUEST_TITLE);
        return problem;
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ProblemDetail invalidParameter(MethodArgumentTypeMismatchException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                String.format(ApiMessages.INVALID_PARAMETER_DETAIL, exception.getName()));
        problem.setTitle(ApiMessages.INVALID_REQUEST_TITLE);
        return problem;
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ProblemDetail unreadableRequest() {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                ApiMessages.UNREADABLE_BODY_DETAIL);
        problem.setTitle(ApiMessages.INVALID_REQUEST_TITLE);
        return problem;
    }

    @ExceptionHandler(ProfileValidationException.class)
    ProblemDetail invalidProfile(ProfileValidationException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                exception.getMessage());
        problem.setTitle(ApiMessages.INVALID_PROFILE_TITLE);
        return problem;
    }

    @ExceptionHandler(OnboardingPreferencesValidationException.class)
    ProblemDetail invalidOnboardingPreferences(OnboardingPreferencesValidationException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                exception.getMessage());
        problem.setTitle(ApiMessages.INVALID_PREFERENCES_TITLE);
        return problem;
    }

    @ExceptionHandler(ProjectValidationException.class)
    ProblemDetail invalidProject(ProjectValidationException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                exception.getMessage());
        problem.setTitle(ApiMessages.INVALID_PROJECT_TITLE);
        return problem;
    }

    @ExceptionHandler(ProjectConflictException.class)
    ProblemDetail projectConflict(ProjectConflictException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT,
                exception.getMessage());
        problem.setTitle(ApiMessages.PROJECT_CONFLICT_TITLE);
        return problem;
    }

    @ExceptionHandler(MembershipPermissionException.class)
    ProblemDetail membershipPermission(MembershipPermissionException exception) {
        return membershipProblem(
                HttpStatus.FORBIDDEN,
                ApiMessages.MEMBERSHIP_PERMISSION_TITLE,
                ApiMessages.MEMBERSHIP_PERMISSION_CODE,
                exception.getMessage());
    }

    @ExceptionHandler(MembershipInvalidTransitionException.class)
    ProblemDetail membershipInvalidTransition(MembershipInvalidTransitionException exception) {
        return membershipProblem(
                HttpStatus.CONFLICT,
                ApiMessages.MEMBERSHIP_INVALID_TRANSITION_TITLE,
                ApiMessages.MEMBERSHIP_INVALID_TRANSITION_CODE,
                exception.getMessage());
    }

    @ExceptionHandler(MembershipOwnerInvariantException.class)
    ProblemDetail membershipOwnerRequired(MembershipOwnerInvariantException exception) {
        return membershipProblem(
                HttpStatus.CONFLICT,
                ApiMessages.MEMBERSHIP_OWNER_REQUIRED_TITLE,
                ApiMessages.MEMBERSHIP_OWNER_REQUIRED_CODE,
                exception.getMessage());
    }

    @ExceptionHandler(MembershipConflictException.class)
    ProblemDetail membershipConflict(MembershipConflictException exception) {
        return membershipProblem(
                HttpStatus.CONFLICT,
                ApiMessages.MEMBERSHIP_CONFLICT_TITLE,
                ApiMessages.MEMBERSHIP_CONFLICT_CODE,
                exception.getMessage());
    }

    @ExceptionHandler(MemberInvitationProjectNotFoundException.class)
    ProblemDetail memberInvitationProjectNotFound(
            MemberInvitationProjectNotFoundException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.NOT_FOUND,
                exception.getMessage());
        problem.setTitle(ApiMessages.INVITATION_PROJECT_NOT_FOUND_TITLE);
        return problem;
    }

    @ExceptionHandler(MemberInvitationPermissionException.class)
    ProblemDetail memberInvitationPermission(MemberInvitationPermissionException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.FORBIDDEN,
                exception.getMessage());
        problem.setTitle(ApiMessages.INVITATION_PERMISSION_TITLE);
        return problem;
    }

    @ExceptionHandler(DuplicateMemberInvitationException.class)
    ProblemDetail duplicateMemberInvitation(DuplicateMemberInvitationException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT,
                exception.getMessage());
        problem.setTitle(ApiMessages.INVITATION_CONFLICT_TITLE);
        problem.setProperty("code", ApiMessages.INVITATION_CONFLICT_CODE);
        return problem;
    }

    @ExceptionHandler(ProjectLifecycleAuthorizationException.class)
    ProblemDetail projectLifecycleAuthorization(ProjectLifecycleAuthorizationException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.FORBIDDEN,
                exception.getMessage());
        problem.setTitle(ApiMessages.MEMBERSHIP_PERMISSION_TITLE);
        return problem;
    }

    @ExceptionHandler(ProjectUpdateAuthorizationException.class)
    ProblemDetail projectUpdateAuthorization(ProjectUpdateAuthorizationException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.FORBIDDEN,
                exception.getMessage());
        problem.setTitle(ApiMessages.MEMBERSHIP_PERMISSION_TITLE);
        return problem;
    }

    @ExceptionHandler(ProjectLifecycleConflictException.class)
    ProblemDetail projectLifecycleConflict(ProjectLifecycleConflictException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT,
                exception.getMessage());
        problem.setTitle(ApiMessages.PROJECT_LIFECYCLE_CONFLICT_TITLE);
        return problem;
    }

    @ExceptionHandler(PublicProjectNotFoundException.class)
    ProblemDetail publicProjectNotFound(PublicProjectNotFoundException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.NOT_FOUND,
                exception.getMessage());
        problem.setTitle(ApiMessages.PUBLIC_PROJECT_NOT_FOUND_TITLE);
        return problem;
    }

    @ExceptionHandler(ProfileNotFoundException.class)
    ProblemDetail profileNotFound() {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.NOT_FOUND,
                ApiMessages.PROFILE_NOT_FOUND_DETAIL);
        problem.setTitle(ApiMessages.PROFILE_NOT_FOUND_TITLE);
        return problem;
    }

    @ExceptionHandler(InvalidAuthenticatedSubjectException.class)
    ProblemDetail invalidAuthenticatedSubject() {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.UNAUTHORIZED,
                ApiMessages.UNAUTHORIZED_DETAIL);
        problem.setTitle(ApiMessages.UNAUTHORIZED_TITLE);
        return problem;
    }

    @ExceptionHandler(DataAccessException.class)
    ProblemDetail dataStoreUnavailable() {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.SERVICE_UNAVAILABLE,
                ApiMessages.DATASTORE_UNAVAILABLE_DETAIL);
        problem.setTitle(ApiMessages.DATASTORE_UNAVAILABLE_TITLE);
        return problem;
    }

    private static ProblemDetail membershipProblem(
            HttpStatus status,
            String title,
            String code,
            String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        problem.setProperty("code", code);
        return problem;
    }
}
