package com.dershane.userservice.branch.application.port.in;

public interface CreateBranchUseCase {

  BranchView create(CreateBranchCommand command);
}
