package com.pulso.backend.discovery.api;

import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.pulso.backend.preference.domain.OnboardingPreferencesValidationException;
import com.pulso.backend.profile.application.InvalidAuthenticatedSubjectException;
import com.pulso.backend.profile.application.ProfileNotFoundException;
import com.pulso.backend.profile.domain.ProfileValidationException;
import com.pulso.backend.project.application.ProjectConflictException;
import com.pulso.backend.project.domain.ProjectValidationException;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(InvalidRequestException.class)
    ProblemDetail invalidRequest(InvalidRequestException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                exception.getMessage());
        problem.setTitle("Invalid request");
        return problem;
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ProblemDetail invalidParameter(MethodArgumentTypeMismatchException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                "Parameter '" + exception.getName() + "' has an invalid value.");
        problem.setTitle("Invalid request");
        return problem;
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ProblemDetail unreadableRequest() {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                "The request body is invalid.");
        problem.setTitle("Invalid request");
        return problem;
    }

    @ExceptionHandler(ProfileValidationException.class)
    ProblemDetail invalidProfile(ProfileValidationException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                exception.getMessage());
        problem.setTitle("Invalid profile");
        return problem;
    }

    @ExceptionHandler(OnboardingPreferencesValidationException.class)
    ProblemDetail invalidOnboardingPreferences(OnboardingPreferencesValidationException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                exception.getMessage());
        problem.setTitle("Invalid preferences");
        return problem;
    }

    @ExceptionHandler(ProjectValidationException.class)
    ProblemDetail invalidProject(ProjectValidationException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                exception.getMessage());
        problem.setTitle("Invalid project");
        return problem;
    }

    @ExceptionHandler(ProjectConflictException.class)
    ProblemDetail projectConflict(ProjectConflictException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT,
                exception.getMessage());
        problem.setTitle("Project conflict");
        return problem;
    }

    @ExceptionHandler(ProfileNotFoundException.class)
    ProblemDetail profileNotFound() {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.NOT_FOUND,
                "The authenticated profile was not found.");
        problem.setTitle("Profile not found");
        return problem;
    }

    @ExceptionHandler(InvalidAuthenticatedSubjectException.class)
    ProblemDetail invalidAuthenticatedSubject() {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.UNAUTHORIZED,
                "The authenticated subject is invalid.");
        problem.setTitle("Unauthorized");
        return problem;
    }

    @ExceptionHandler(DataAccessException.class)
    ProblemDetail dataStoreUnavailable() {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.SERVICE_UNAVAILABLE,
                "The data store is temporarily unavailable.");
        problem.setTitle("Data store unavailable");
        return problem;
    }
}
