package com.pulso.backend.project.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.pulso.backend.project.application.GetPublicProjectDetail;

@RestController
@RequestMapping("/api/v1/projects")
public class PublicProjectController {

    private final GetPublicProjectDetail getPublicProjectDetail;

    public PublicProjectController(GetPublicProjectDetail getPublicProjectDetail) {
        this.getPublicProjectDetail = getPublicProjectDetail;
    }

    @GetMapping("/{projectRef}")
    public PublicProjectResponse get(@PathVariable String projectRef) {
        return PublicProjectResponse.from(getPublicProjectDetail.execute(projectRef));
    }
}
