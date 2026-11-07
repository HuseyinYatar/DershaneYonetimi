package com.dershane.taskservice.task.application.event;

import java.util.UUID;

/**
 * Published in-process while a task is being created, inside the creating transaction. The
 * assignment module hands out today's assignments. It never leaves the service: it is not an
 * integration event and is not sent to SNS.
 */
public record TaskCreated(UUID taskId) {}
