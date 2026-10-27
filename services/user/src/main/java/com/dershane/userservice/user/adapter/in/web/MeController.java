package com.dershane.userservice.user.adapter.in.web;

import com.dershane.userservice.shared.adapter.in.web.CurrentActor;
import com.dershane.userservice.shared.application.Actor;
import com.dershane.userservice.user.adapter.in.web.dto.GuardianChildResponse;
import com.dershane.userservice.user.adapter.in.web.dto.UserResponse;
import com.dershane.userservice.user.application.port.in.GetMyProfileQuery;
import com.dershane.userservice.user.application.port.in.ListMyChildrenQuery;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Me")
@RestController
@RequestMapping("/api/v1/me")
@RequiredArgsConstructor
class MeController {

  private final GetMyProfileQuery getMyProfile;
  private final ListMyChildrenQuery listMyChildren;
  private final UserWebMapper mapper;

  @Operation(summary = "The caller's profile")
  @GetMapping
  public UserResponse me(@CurrentActor Actor actor) {
    return mapper.toResponse(getMyProfile.me(actor));
  }

  @Operation(summary = "The caller's children (guardians)")
  @GetMapping("/children")
  public List<GuardianChildResponse> children(@CurrentActor Actor actor) {
    return listMyChildren.myChildren(actor).stream().map(mapper::toResponse).toList();
  }
}
