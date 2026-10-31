/**
 * Taking attendance (diagram: YoklamaKaydi): the teacher submits the full list of a session,
 * corrections are audited, approved excuses turn absences into excused ones, and absences are
 * announced to the notification service.
 */
@ApplicationModule(allowedDependencies = {"shared", "session::*", "roster::*", "excuse::*"})
package com.dershane.attendanceservice.attendance;

import org.springframework.modulith.ApplicationModule;
