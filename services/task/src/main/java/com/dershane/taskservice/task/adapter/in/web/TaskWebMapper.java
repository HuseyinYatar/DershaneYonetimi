package com.dershane.taskservice.task.adapter.in.web;

import com.dershane.taskservice.task.adapter.in.web.dto.CreateTaskRequest;
import com.dershane.taskservice.task.adapter.in.web.dto.TaskResponse;
import com.dershane.taskservice.task.adapter.in.web.dto.UpdateTaskRequest;
import com.dershane.taskservice.task.application.port.in.CreateTaskCommand;
import com.dershane.taskservice.task.application.port.in.TaskView;
import com.dershane.taskservice.task.application.port.in.UpdateTaskCommand;
import org.mapstruct.Mapper;

@Mapper
interface TaskWebMapper {

  CreateTaskCommand toCommand(CreateTaskRequest request);

  UpdateTaskCommand toCommand(UpdateTaskRequest request);

  TaskResponse toResponse(TaskView view);
}
