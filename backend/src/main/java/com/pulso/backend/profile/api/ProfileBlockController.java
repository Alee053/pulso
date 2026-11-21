package com.pulso.backend.profile.api;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.pulso.backend.config.ApiMessages;
import com.pulso.backend.config.ProfileBlockProperties;
import com.pulso.backend.discovery.api.InvalidRequestException;
import com.pulso.backend.profile.application.ManageProfileBlocks;

@RestController
public class ProfileBlockController {

    private static final String BLOCK_PATH = "/api/v1/profiles/{targetId}/block";

    private final ManageProfileBlocks manageProfileBlocks;
    private final ProfileBlockProperties properties;

    public ProfileBlockController(ManageProfileBlocks manageProfileBlocks, ProfileBlockProperties properties) {
        this.manageProfileBlocks = manageProfileBlocks;
        this.properties = properties;
    }

    @PostMapping(BLOCK_PATH)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void blockProfile(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID targetId) {
        manageProfileBlocks.block(AuthenticatedProfileId.require(jwt), targetId);
    }

    @DeleteMapping(BLOCK_PATH)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unblockProfile(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID targetId) {
        manageProfileBlocks.unblock(AuthenticatedProfileId.require(jwt), targetId);
    }

    @GetMapping("/api/v1/me/profile-blocks")
    public PageResponse list(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "0") int offset,
            @RequestParam(required = false) Integer limit) {
        UUID profileId = AuthenticatedProfileId.require(jwt);
        int pageLimit = limit == null ? properties.defaultLimit() : limit;
        if (offset < 0 || offset > properties.maxOffset()) {
            throw new InvalidRequestException(
                    String.format(ApiMessages.INVALID_OFFSET_DETAIL, properties.maxOffset()));
        }
        if (pageLimit < 1 || pageLimit > properties.maxLimit()) {
            throw new InvalidRequestException(
                    String.format(ApiMessages.INVALID_LIMIT_DETAIL, properties.maxLimit()));
        }
        ManageProfileBlocks.Page page = manageProfileBlocks.list(profileId, offset, pageLimit);
        var data = page.profiles().stream()
                .map(profile -> new BlockedProfileResponse(profile.profileId(), profile.displayName()))
                .toList();
        return new PageResponse(data, new PageMetadata(offset, pageLimit, page.hasMore()));
    }

    public record PageResponse(List<BlockedProfileResponse> data, PageMetadata meta) {
    }

    public record BlockedProfileResponse(UUID profileId, String displayName) {
    }

    public record PageMetadata(int offset, int limit, boolean hasMore) {
    }
}
