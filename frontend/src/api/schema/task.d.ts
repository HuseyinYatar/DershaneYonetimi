export interface paths {
    "/api/v1/tasks/{taskId}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** Get a task (its teacher or a branch admin) */
        get: operations["get"];
        /** Edit the texts and attachments of a task */
        put: operations["update"];
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/task-assignments/{assignmentId}/submission": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        /** Submit (or replace the submission of) an assignment (the student) */
        put: operations["submit"];
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/task-assignments/{assignmentId}/evaluation": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        /** Evaluate (or re-evaluate) a submission (the task's teacher) */
        put: operations["evaluate"];
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/tasks": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** List tasks (teachers: their own; admins: their branch) */
        get: operations["list"];
        put?: never;
        /** Give a task to a student or a classroom (teacher) */
        post: operations["create"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/tasks/{taskId}/deactivate": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /** Stop handing out new assignments of a task */
        post: operations["deactivate"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/tasks/{taskId}/assignments": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** List the assignments of one task (its teacher or a branch admin) */
        get: operations["listOfTask"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/task-assignments": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** List assignments (students: their own; guardians: their child's, studentId required; teachers: their tasks'; admins: their branch) */
        get: operations["list_1"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/task-assignments/{assignmentId}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** Get an assignment with its submission and evaluation */
        get: operations["get_1"];
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
        UpdateTaskRequest: {
            title: string;
            description?: string;
            attachmentFileIds?: string[];
        };
        TaskResponse: {
            /** Format: uuid */
            id?: string;
            /** Format: uuid */
            branchId?: string;
            /** Format: uuid */
            teacherId?: string;
            title?: string;
            description?: string;
            /** @enum {string} */
            targetType?: "STUDENT" | "CLASSROOM";
            /** Format: uuid */
            targetId?: string;
            /** @enum {string} */
            recurrence?: "ONCE" | "DAILY" | "WEEKLY";
            recurrenceDays?: ("MONDAY" | "TUESDAY" | "WEDNESDAY" | "THURSDAY" | "FRIDAY" | "SATURDAY" | "SUNDAY")[];
            /** Format: date */
            startDate?: string;
            /** Format: date */
            endDate?: string;
            /** Format: time-local */
            dueTime?: string;
            attachmentFileIds?: string[];
            active?: boolean;
            /** Format: date-time */
            createdAt?: string;
        };
        SubmitAssignmentRequest: {
            description?: string;
            fileIds?: string[];
        };
        AssignmentDetailResponse: {
            /** Format: uuid */
            id?: string;
            /** Format: uuid */
            taskId?: string;
            taskTitle?: string;
            taskDescription?: string;
            taskAttachmentFileIds?: string[];
            /** Format: uuid */
            teacherId?: string;
            /** Format: uuid */
            branchId?: string;
            /** Format: uuid */
            studentId?: string;
            /** Format: date */
            occurrenceDate?: string;
            /** Format: date-time */
            dueAt?: string;
            /** @enum {string} */
            status?: "ASSIGNED" | "SUBMITTED" | "OVERDUE" | "GRADED" | "CANCELLED";
            /** Format: date-time */
            assignedAt?: string;
            submission?: components["schemas"]["SubmissionResponse"];
            evaluation?: components["schemas"]["EvaluationResponse"];
        };
        EvaluationResponse: {
            /** Format: uuid */
            evaluatorId?: string;
            /** Format: int32 */
            score?: number;
            feedback?: string;
            /** Format: date-time */
            evaluatedAt?: string;
        };
        SubmissionResponse: {
            description?: string;
            fileIds?: string[];
            /** Format: date-time */
            submittedAt?: string;
            late?: boolean;
        };
        EvaluateAssignmentRequest: {
            /** Format: int32 */
            score?: number;
            feedback?: string;
        };
        CreateTaskRequest: {
            title: string;
            description?: string;
            /** @enum {string} */
            targetType: "STUDENT" | "CLASSROOM";
            /** Format: uuid */
            targetId: string;
            /** @enum {string} */
            recurrence: "ONCE" | "DAILY" | "WEEKLY";
            recurrenceDays?: ("MONDAY" | "TUESDAY" | "WEDNESDAY" | "THURSDAY" | "FRIDAY" | "SATURDAY" | "SUNDAY")[];
            /** Format: date */
            startDate: string;
            /** Format: date */
            endDate?: string;
            /** Format: time-local */
            dueTime: string;
            attachmentFileIds?: string[];
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
        PagedModelTaskResponse: {
            content?: components["schemas"]["TaskResponse"][];
            page?: components["schemas"]["PageMetadata"];
        };
        AssignmentSummaryResponse: {
            /** Format: uuid */
            id?: string;
            /** Format: uuid */
            taskId?: string;
            taskTitle?: string;
            /** Format: uuid */
            studentId?: string;
            /** Format: date */
            occurrenceDate?: string;
            /** Format: date-time */
            dueAt?: string;
            /** @enum {string} */
            status?: "ASSIGNED" | "SUBMITTED" | "OVERDUE" | "GRADED" | "CANCELLED";
        };
        PagedModelAssignmentSummaryResponse: {
            content?: components["schemas"]["AssignmentSummaryResponse"][];
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
    get: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                taskId: string;
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
                    "*/*": components["schemas"]["TaskResponse"];
                };
            };
        };
    };
    update: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                taskId: string;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["UpdateTaskRequest"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["TaskResponse"];
                };
            };
        };
    };
    submit: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                assignmentId: string;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["SubmitAssignmentRequest"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["AssignmentDetailResponse"];
                };
            };
        };
    };
    evaluate: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                assignmentId: string;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["EvaluateAssignmentRequest"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["AssignmentDetailResponse"];
                };
            };
        };
    };
    list: {
        parameters: {
            query: {
                teacherId?: string;
                branchId?: string;
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
                    "*/*": components["schemas"]["PagedModelTaskResponse"];
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
                "application/json": components["schemas"]["CreateTaskRequest"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["TaskResponse"];
                };
            };
        };
    };
    deactivate: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                taskId: string;
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
                    "*/*": components["schemas"]["TaskResponse"];
                };
            };
        };
    };
    listOfTask: {
        parameters: {
            query: {
                occurrenceDate?: string;
                status?: "ASSIGNED" | "SUBMITTED" | "OVERDUE" | "GRADED" | "CANCELLED";
                pageable: components["schemas"]["Pageable"];
            };
            header?: never;
            path: {
                taskId: string;
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
                    "*/*": components["schemas"]["PagedModelAssignmentSummaryResponse"];
                };
            };
        };
    };
    list_1: {
        parameters: {
            query: {
                studentId?: string;
                status?: "ASSIGNED" | "SUBMITTED" | "OVERDUE" | "GRADED" | "CANCELLED";
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
                    "*/*": components["schemas"]["PagedModelAssignmentSummaryResponse"];
                };
            };
        };
    };
    get_1: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                assignmentId: string;
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
                    "*/*": components["schemas"]["AssignmentDetailResponse"];
                };
            };
        };
    };
}
