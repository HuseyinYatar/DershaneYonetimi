export interface paths {
    "/api/v1/class-sessions/{sessionId}/teacher": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        /** Let a substitute teacher take a planned session (admin of the branch) */
        put: operations["reassign"];
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/class-sessions/{sessionId}/records/{studentId}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        /** Correct one student's status after attendance was taken, or add a student who joined later (reason required, audited) */
        put: operations["correct"];
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/class-schedules/{scheduleId}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** Get a schedule */
        get: operations["get"];
        /** Replace the changeable fields; applies to sessions generated from now on */
        put: operations["update"];
        post?: never;
        /** Deactivate a schedule (deleting means deactivating; sessions stay) */
        delete: operations["deactivate"];
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/excuses": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** List excuses (admins: their branch; guardians: the ones they reported) */
        get: operations["list"];
        put?: never;
        /** Report an excuse for the guardian's own child */
        post: operations["request"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/excuses/{excuseId}/reject": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /** Reject an excuse (admin) */
        post: operations["reject"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/excuses/{excuseId}/approve": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /** Approve an excuse; absences in its range become excused (admin) */
        post: operations["approve"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/class-sessions": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** List sessions (admins: their branch; teachers: only their own sessions) */
        get: operations["list_1"];
        put?: never;
        /** Open a session outside any weekly schedule (admin of the branch) */
        post: operations["create"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/class-sessions/{sessionId}/cancel": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /** Cancel a planned session (admin of the branch) */
        post: operations["cancel"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/class-sessions/{sessionId}/attendance": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /** Take attendance with the full list (every student once); only once per session */
        post: operations["take"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/timetable-setting": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** Lesson-hour grid of a branch (defaults when never customized) */
        get: operations["getTimetableSetting"];
        /** Save the lesson-hour grid of a branch (admins of the branch) */
        put: operations["saveTimetableSetting"];
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/class-schedules": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** List schedules (branch defaults to the caller's branch) */
        get: operations["list_2"];
        put?: never;
        /** Create a weekly schedule; its sessions are generated by a daily job */
        post: operations["create_1"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/class-sessions/{sessionId}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** Get a session (its teacher and admins of its branch) */
        get: operations["get_1"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/class-sessions/{sessionId}/roster": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** The students of a session with their current status (teacher of the session, admin) */
        get: operations["roster"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/attendance-summaries": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** Counts per status for one student (same visibility as the records) */
        get: operations["summary"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/attendance-records": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** Attendance records, newest first (student: own; guardian: their child; teacher: own sessions; admin: their branch) */
        get: operations["list_3"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
}
export type webhooks = Record<string, never>;
export interface components {
    schemas: {
        ReassignTeacherRequest: {
            /** Format: uuid */
            teacherId: string;
        };
        SessionResponse: {
            /** Format: uuid */
            id?: string;
            /** Format: uuid */
            scheduleId?: string;
            /** Format: uuid */
            branchId?: string;
            /** Format: uuid */
            classroomId?: string;
            /** Format: uuid */
            teacherId?: string;
            /** Format: uuid */
            subjectId?: string;
            /** Format: date */
            sessionDate?: string;
            /** Format: time-local */
            startsAt?: string;
            /** Format: time-local */
            endsAt?: string;
            /** @enum {string} */
            status?: "PLANNED" | "COMPLETED" | "CANCELLED" | "EXPIRED";
            /** Format: date-time */
            takenAt?: string;
            /** Format: uuid */
            takenBy?: string;
            /** Format: date-time */
            createdAt?: string;
            /** Format: date-time */
            updatedAt?: string;
        };
        CorrectRecordRequest: {
            /** @enum {string} */
            status: "PRESENT" | "ABSENT" | "LATE" | "EXCUSED";
            reason: string;
        };
        AttendanceRecordResponse: {
            /** Format: uuid */
            id?: string;
            /** Format: uuid */
            sessionId?: string;
            /** Format: uuid */
            studentId?: string;
            /** Format: uuid */
            classroomId?: string;
            /** Format: uuid */
            subjectId?: string;
            /** Format: date */
            sessionDate?: string;
            /** Format: time-local */
            startsAt?: string;
            /** Format: time-local */
            endsAt?: string;
            /** @enum {string} */
            status?: "PRESENT" | "ABSENT" | "LATE" | "EXCUSED";
            /** Format: uuid */
            excuseId?: string;
            note?: string;
            /** Format: date-time */
            markedAt?: string;
            /** Format: uuid */
            markedBy?: string;
        };
        UpdateScheduleRequest: {
            /** Format: uuid */
            teacherId: string;
            /** Format: uuid */
            subjectId: string;
            /** @enum {string} */
            dayOfWeek: "MONDAY" | "TUESDAY" | "WEDNESDAY" | "THURSDAY" | "FRIDAY" | "SATURDAY" | "SUNDAY";
            /** Format: time-local */
            startsAt: string;
            /** Format: time-local */
            endsAt: string;
            /** Format: date */
            validFrom: string;
            /** Format: date */
            validTo?: string;
        };
        ScheduleResponse: {
            /** Format: uuid */
            id?: string;
            /** Format: uuid */
            branchId?: string;
            /** Format: uuid */
            classroomId?: string;
            /** Format: uuid */
            teacherId?: string;
            /** Format: uuid */
            subjectId?: string;
            /** @enum {string} */
            dayOfWeek?: "MONDAY" | "TUESDAY" | "WEDNESDAY" | "THURSDAY" | "FRIDAY" | "SATURDAY" | "SUNDAY";
            /** Format: time-local */
            startsAt?: string;
            /** Format: time-local */
            endsAt?: string;
            /** Format: date */
            validFrom?: string;
            /** Format: date */
            validTo?: string;
            active?: boolean;
            /** Format: date-time */
            createdAt?: string;
            /** Format: date-time */
            updatedAt?: string;
        };
        RequestExcuseRequest: {
            /** Format: uuid */
            studentId: string;
            /** Format: date */
            fromDate: string;
            /** Format: date */
            toDate: string;
            reason: string;
        };
        ExcuseResponse: {
            /** Format: uuid */
            id?: string;
            /** Format: uuid */
            studentId?: string;
            /** Format: uuid */
            branchId?: string;
            /** Format: date */
            fromDate?: string;
            /** Format: date */
            toDate?: string;
            reason?: string;
            /** @enum {string} */
            status?: "PENDING" | "APPROVED" | "REJECTED";
            /** Format: uuid */
            requestedBy?: string;
            /** Format: uuid */
            decidedBy?: string;
            /** Format: date-time */
            decidedAt?: string;
            decisionNote?: string;
            /** Format: date-time */
            createdAt?: string;
        };
        DecideExcuseRequest: {
            note?: string;
        };
        CreateSessionRequest: {
            /** Format: uuid */
            branchId: string;
            /** Format: uuid */
            classroomId: string;
            /** Format: uuid */
            teacherId: string;
            /** Format: uuid */
            subjectId: string;
            /** Format: date */
            sessionDate: string;
            /** Format: time-local */
            startsAt: string;
            /** Format: time-local */
            endsAt: string;
        };
        AttendanceEntryRequest: {
            /** Format: uuid */
            studentId: string;
            /** @enum {string} */
            status: "PRESENT" | "ABSENT" | "LATE" | "EXCUSED";
            note?: string;
        };
        TakeAttendanceRequest: {
            entries: components["schemas"]["AttendanceEntryRequest"][];
            reason?: string;
        };
        AttendanceSubmissionResponse: {
            /** Format: uuid */
            sessionId?: string;
            /** Format: date-time */
            takenAt?: string;
            /** Format: int32 */
            present?: number;
            /** Format: int32 */
            absent?: number;
            /** Format: int32 */
            late?: number;
            /** Format: int32 */
            excused?: number;
        };
        CreateScheduleRequest: {
            /** Format: uuid */
            branchId: string;
            /** Format: uuid */
            classroomId: string;
            /** Format: uuid */
            teacherId: string;
            /** Format: uuid */
            subjectId: string;
            /** @enum {string} */
            dayOfWeek: "MONDAY" | "TUESDAY" | "WEDNESDAY" | "THURSDAY" | "FRIDAY" | "SATURDAY" | "SUNDAY";
            /** Format: time-local */
            startsAt: string;
            /** Format: time-local */
            endsAt: string;
            /** Format: date */
            validFrom: string;
            /** Format: date */
            validTo?: string;
        };
        Pageable: {
            /** Format: int32 */
            page?: number;
            /** Format: int32 */
            size?: number;
            sort?: string[];
        };
        PageMetadata: {
            /** Format: int64 */
            size?: number;
            /** Format: int64 */
            number?: number;
            /** Format: int64 */
            totalElements?: number;
            /** Format: int64 */
            totalPages?: number;
        };
        PagedModelExcuseResponse: {
            content?: components["schemas"]["ExcuseResponse"][];
            page?: components["schemas"]["PageMetadata"];
        };
        PagedModelSessionResponse: {
            content?: components["schemas"]["SessionResponse"][];
            page?: components["schemas"]["PageMetadata"];
        };
        RosterRowResponse: {
            /** Format: uuid */
            studentId?: string;
            studentNumber?: string;
            fullName?: string;
            /** @enum {string} */
            status?: "PRESENT" | "ABSENT" | "LATE" | "EXCUSED";
            excused?: boolean;
            /** Format: uuid */
            excuseId?: string;
        };
        SessionRosterResponse: {
            /** Format: uuid */
            sessionId?: string;
            /** @enum {string} */
            sessionStatus?: "PLANNED" | "COMPLETED" | "CANCELLED" | "EXPIRED";
            students?: components["schemas"]["RosterRowResponse"][];
        };
        SaveTimetableSettingRequest: {
            /** Format: uuid */
            branchId?: string;
            /** Format: time-local */
            firstLessonStart: string;
            /** Format: int32 */
            lessonMinutes: number;
            /** Format: int32 */
            breakMinutes: number;
            /** Format: int32 */
            lessonCount: number;
            /** Format: int32 */
            lunchLesson?: number;
        };
        TimetableSettingResponse: {
            /** Format: uuid */
            branchId?: string;
            /** Format: time-local */
            firstLessonStart?: string;
            /** Format: int32 */
            lessonMinutes?: number;
            /** Format: int32 */
            breakMinutes?: number;
            /** Format: int32 */
            lessonCount?: number;
            /** Format: int32 */
            lunchLesson?: number;
            customized?: boolean;
        };
        PagedModelScheduleResponse: {
            content?: components["schemas"]["ScheduleResponse"][];
            page?: components["schemas"]["PageMetadata"];
        };
        AttendanceSummaryResponse: {
            /** Format: uuid */
            studentId?: string;
            /** Format: date */
            from?: string;
            /** Format: date */
            to?: string;
            /** Format: int64 */
            present?: number;
            /** Format: int64 */
            absent?: number;
            /** Format: int64 */
            late?: number;
            /** Format: int64 */
            excused?: number;
            /** Format: int64 */
            total?: number;
        };
        PagedModelAttendanceRecordResponse: {
            content?: components["schemas"]["AttendanceRecordResponse"][];
            page?: components["schemas"]["PageMetadata"];
        };
    };
    responses: never;
    parameters: never;
    requestBodies: never;
    headers: never;
    pathItems: never;
}
export type $defs = Record<string, never>;
export interface operations {
    getTimetableSetting: {
        parameters: {
            query?: {
                branchId?: string;
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["TimetableSettingResponse"];
                };
            };
        };
    };
    saveTimetableSetting: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["SaveTimetableSettingRequest"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["TimetableSettingResponse"];
                };
            };
        };
    };
    reassign: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                sessionId: string;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["ReassignTeacherRequest"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["SessionResponse"];
                };
            };
        };
    };
    correct: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                sessionId: string;
                studentId: string;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["CorrectRecordRequest"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["AttendanceRecordResponse"];
                };
            };
        };
    };
    get: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                scheduleId: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ScheduleResponse"];
                };
            };
        };
    };
    update: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                scheduleId: string;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["UpdateScheduleRequest"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ScheduleResponse"];
                };
            };
        };
    };
    deactivate: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                scheduleId: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ScheduleResponse"];
                };
            };
        };
    };
    list: {
        parameters: {
            query: {
                status?: "PENDING" | "APPROVED" | "REJECTED";
                studentId?: string;
                pageable: components["schemas"]["Pageable"];
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["PagedModelExcuseResponse"];
                };
            };
        };
    };
    request: {
        parameters: {
            query?: never;
            header?: {
                /** @description Optional. Retrying with the same key and body returns the first response instead of creating a duplicate. */
                "Idempotency-Key"?: string;
            };
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["RequestExcuseRequest"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ExcuseResponse"];
                };
            };
        };
    };
    reject: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                excuseId: string;
            };
            cookie?: never;
        };
        requestBody?: {
            content: {
                "application/json": components["schemas"]["DecideExcuseRequest"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ExcuseResponse"];
                };
            };
        };
    };
    approve: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                excuseId: string;
            };
            cookie?: never;
        };
        requestBody?: {
            content: {
                "application/json": components["schemas"]["DecideExcuseRequest"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ExcuseResponse"];
                };
            };
        };
    };
    list_1: {
        parameters: {
            query: {
                branchId?: string;
                classroomId?: string;
                teacherId?: string;
                from?: string;
                to?: string;
                status?: "PLANNED" | "COMPLETED" | "CANCELLED" | "EXPIRED";
                pageable: components["schemas"]["Pageable"];
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["PagedModelSessionResponse"];
                };
            };
        };
    };
    create: {
        parameters: {
            query?: never;
            header?: {
                /** @description Optional. Retrying with the same key and body returns the first response instead of creating a duplicate. */
                "Idempotency-Key"?: string;
            };
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["CreateSessionRequest"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["SessionResponse"];
                };
            };
        };
    };
    cancel: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                sessionId: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["SessionResponse"];
                };
            };
        };
    };
    take: {
        parameters: {
            query?: never;
            header?: {
                /** @description Optional. Retrying with the same key and body returns the first response instead of creating a duplicate. */
                "Idempotency-Key"?: string;
            };
            path: {
                sessionId: string;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["TakeAttendanceRequest"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["AttendanceSubmissionResponse"];
                };
            };
        };
    };
    list_2: {
        parameters: {
            query: {
                branchId?: string;
                classroomId?: string;
                teacherId?: string;
                active?: boolean;
                pageable: components["schemas"]["Pageable"];
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["PagedModelScheduleResponse"];
                };
            };
        };
    };
    create_1: {
        parameters: {
            query?: never;
            header?: {
                /** @description Optional. Retrying with the same key and body returns the first response instead of creating a duplicate. */
                "Idempotency-Key"?: string;
            };
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["CreateScheduleRequest"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ScheduleResponse"];
                };
            };
        };
    };
    get_1: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                sessionId: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["SessionResponse"];
                };
            };
        };
    };
    roster: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                sessionId: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["SessionRosterResponse"];
                };
            };
        };
    };
    summary: {
        parameters: {
            query?: {
                studentId?: string;
                from?: string;
                to?: string;
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["AttendanceSummaryResponse"];
                };
            };
        };
    };
    list_3: {
        parameters: {
            query: {
                studentId?: string;
                classroomId?: string;
                from?: string;
                to?: string;
                pageable: components["schemas"]["Pageable"];
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["PagedModelAttendanceRecordResponse"];
                };
            };
        };
    };
}
