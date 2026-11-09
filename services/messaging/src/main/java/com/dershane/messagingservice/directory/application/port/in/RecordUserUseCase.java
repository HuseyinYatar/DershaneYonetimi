package com.dershane.messagingservice.directory.application.port.in;

import com.dershane.messagingservice.shared.application.Actor;

/** Upserts the local copy of a student or teacher; repeated and stale events change nothing. */
public interface RecordUserUseCase {

  void record(Actor actor, RecordUserCommand command);
}
