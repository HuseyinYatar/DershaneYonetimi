export interface paths {
    "/api/v1/students/{studentId}/guardian": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** The student's guardian */
        get: operations["get"];
        /**
         * Link a guardian to a student
         * @description A student has one guardian; linking another guardian replaces the old link.
         */
        put: operations["link"];
        post?: never;
        /** Remove the student's guardian link */
        delete: operations["unlink"];
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/students/{studentId}/classroom": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        /**
         * Put a student into a classroom
         * @description If the student is in another classroom, that membership is closed first. Repeating the call with the same classroom changes nothing.
         */
        put: operations["assign"];
        post?: never;
        /** Remove a student from their current classroom */
        delete: operations["remove"];
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/users/{userId}/temporary-password": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /** Issue a new temporary password (users without e-mail) */
        post: operations["resetTemporaryPassword"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/users/{userId}/password-setup-email": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /** Resend the password setup e-mail (users with e-mail) */
        post: operations["sendPasswordSetupEmail"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/users/{userId}/deactivate": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /**
         * Deactivate a user
         * @description Disables the login, marks the profile inactive and, for students, closes the active classroom membership. Records are kept.
         */
        post: operations["deactivate"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/teachers": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** List teachers (admins) */
        get: operations["list"];
        put?: never;
        /** Register a teacher (e-mail required) and open their login account */
        post: operations["create"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/teachers/{teacherId}/assignments": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** List a teacher's assignments (admins or the teacher) */
        get: operations["list_1"];
        put?: never;
        /** Assign a teacher to teach a subject in a classroom */
        post: operations["create_1"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/subjects": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** List subjects (any authenticated user) */
        get: operations["list_2"];
        put?: never;
        /** Create a subject (super admin) */
        post: operations["create_2"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/subjects/{subjectId}/deactivate": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /** Deactivate a subject (super admin) */
        post: operations["deactivate_1"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/students": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** List students (admins; filter by branch and active) */
        get: operations["list_3"];
        put?: never;
        /**
         * Register a student and open their login account
         * @description With e-mail, Keycloak sends a password setup e-mail. Without e-mail, the username is branchcode-studentnumber and a temporary password is returned once.
         */
        post: operations["create_3"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/guardians": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** List guardians (admins) */
        get: operations["list_4"];
        put?: never;
        /**
         * Register a guardian (phone required) and open their login account
         * @description Without e-mail, the username is the phone number and a temporary password is returned once.
         */
        post: operations["create_4"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/classrooms": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** List classrooms (admins; branch defaults to the caller's branch) */
        get: operations["list_5"];
        put?: never;
        /** Create a classroom in a branch (admin of that branch) */
        post: operations["create_5"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/classrooms/{classroomId}/deactivate": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /** Deactivate a classroom; fails while students are enrolled */
        post: operations["deactivate_2"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/branches": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** List branches (super admin: all, admin: own branch) */
        get: operations["list_6"];
        put?: never;
        /** Create a branch (super admin) */
        post: operations["create_6"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/branches/{branchId}/deactivate": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /** Deactivate a branch (super admin); fails while it has active classrooms */
        post: operations["deactivate_3"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/admins": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** List admins (super admin) */
        get: operations["list_7"];
        put?: never;
        /** Create a branch admin (super admin) */
        post: operations["create_7"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/teachers/{teacherId}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** Get a teacher */
        get: operations["get_1"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        /** Update a teacher; null fields are left unchanged */
        patch: operations["update"];
        trace?: never;
    };
    "/api/v1/subjects/{subjectId}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** Get a subject */
        get: operations["get_2"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        /** Rename a subject (super admin) */
        patch: operations["update_1"];
        trace?: never;
    };
    "/api/v1/students/{studentId}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** Get a student (admins, the student, their teachers and guardian) */
        get: operations["get_3"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        /** Update a student; null fields are left unchanged */
        patch: operations["update_2"];
        trace?: never;
    };
    "/api/v1/guardians/{guardianId}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** Get a guardian */
        get: operations["get_4"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        /** Update a guardian; null fields are left unchanged */
        patch: operations["update_3"];
        trace?: never;
    };
    "/api/v1/classrooms/{classroomId}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** Get a classroom (admins, its teachers and students) */
        get: operations["get_5"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        /** Rename a classroom or change its term */
        patch: operations["update_4"];
        trace?: never;
    };
    "/api/v1/branches/{branchId}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** Get a branch */
        get: operations["get_6"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        /** Rename a branch (super admin) */
        patch: operations["update_5"];
        trace?: never;
    };
    "/api/v1/admins/{adminId}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        /** Update an admin (super admin) */
        patch: operations["update_6"];
        trace?: never;
    };
    "/api/v1/users/{userId}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** Get any user's common profile */
        get: operations["get_7"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/me": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** The caller's profile */
        get: operations["me"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/me/classrooms": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** Teachers: classrooms they teach. Students: their current classroom */
        get: operations["classrooms"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/me/children": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** The caller's children (guardians) */
        get: operations["children"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/guardians/{guardianId}/children": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** Children of a guardian (admins or the guardian) */
        get: operations["children_1"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/classrooms/{classroomId}/teachers": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** Teachers and subjects of a classroom */
        get: operations["teachers"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/classrooms/{classroomId}/students": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** Current students of a classroom (admins and its teachers) */
        get: operations["students"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/teachers/{teacherId}/assignments/{assignmentId}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        post?: never;
        /** Remove an assignment */
        delete: operations["delete"];
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
}
export type webhooks = Record<string, never>;
export interface components {
    schemas: {
        LinkGuardianRequest: {
            /** Format: uuid */
            guardianId: string;
            /** @enum {string} */
            relationship: "MOTHER" | "FATHER" | "LEGAL_GUARDIAN" | "OTHER";
        };
        StudentGuardianResponse: {
            /** Format: uuid */
            guardianId?: string;
            /** Format: uuid */
            studentId?: string;
            firstName?: string;
            lastName?: string;
            email?: string;
            phone?: string;
            /** @enum {string} */
            relationship?: "MOTHER" | "FATHER" | "LEGAL_GUARDIAN" | "OTHER";
        };
        AssignClassroomRequest: {
            /** Format: uuid */
            classroomId: string;
            /** Format: date */
            startDate?: string;
        };
        ClassroomMembershipResponse: {
            /** Format: uuid */
            membershipId?: string;
            /** Format: uuid */
            studentId?: string;
            /** Format: uuid */
            classroomId?: string;
            /** Format: date */
            startDate?: string;
            /** Format: date */
            endDate?: string;
            /** Format: uuid */
            closedMembershipId?: string;
        };
        TemporaryPasswordResponse: {
            temporaryPassword?: string;
        };
        UserResponse: {
            /** Format: uuid */
            id?: string;
            /** Format: uuid */
            branchId?: string;
            /** @enum {string} */
            role?: "STUDENT" | "TEACHER" | "GUARDIAN" | "ADMIN";
            firstName?: string;
            lastName?: string;
            email?: string;
            phone?: string;
            username?: string;
            active?: boolean;
            /** Format: date-time */
            createdAt?: string;
            /** Format: date-time */
            updatedAt?: string;
        };
        CreateTeacherRequest: {
            /** Format: uuid */
            branchId: string;
            firstName: string;
            lastName: string;
            /** Format: email */
            email: string;
            phone?: string;
            /** Format: uuid */
            specialtySubjectId: string;
        };
        TeacherResponse: {
            /** Format: uuid */
            id?: string;
            /** Format: uuid */
            branchId?: string;
            firstName?: string;
            lastName?: string;
            email?: string;
            phone?: string;
            username?: string;
            active?: boolean;
            /** Format: uuid */
            specialtySubjectId?: string;
            /** Format: date-time */
            createdAt?: string;
            /** Format: date-time */
            updatedAt?: string;
        };
        UserCreatedResponseTeacherResponse: {
            user?: components["schemas"]["TeacherResponse"];
            passwordSetupEmailSent?: boolean;
            temporaryPassword?: string;
            temporaryPasswordAlreadyIssued?: boolean;
        };
        CreateTeacherAssignmentRequest: {
            /** Format: uuid */
            classroomId: string;
            /** Format: uuid */
            subjectId: string;
        };
        TeacherAssignmentResponse: {
            /** Format: uuid */
            id?: string;
            /** Format: uuid */
            teacherId?: string;
            /** Format: uuid */
            classroomId?: string;
            classroomName?: string;
            /** Format: uuid */
            subjectId?: string;
            subjectName?: string;
            /** Format: date-time */
            createdAt?: string;
        };
        CreateSubjectRequest: {
            name: string;
            code: string;
        };
        SubjectResponse: {
            /** Format: uuid */
            id?: string;
            name?: string;
            code?: string;
            active?: boolean;
            /** Format: date-time */
            createdAt?: string;
            /** Format: date-time */
            updatedAt?: string;
        };
        CreateStudentRequest: {
            /** Format: uuid */
            branchId: string;
            firstName: string;
            lastName: string;
            /** Format: email */
            email?: string;
            phone?: string;
            studentNumber: string;
            /** Format: int32 */
            gradeLevel: number;
            /** @enum {string} */
            targetExam: "YKS" | "LGS";
        };
        StudentResponse: {
            /** Format: uuid */
            id?: string;
            /** Format: uuid */
            branchId?: string;
            firstName?: string;
            lastName?: string;
            email?: string;
            phone?: string;
            username?: string;
            active?: boolean;
            studentNumber?: string;
            /** Format: int32 */
            gradeLevel?: number;
            /** @enum {string} */
            targetExam?: "YKS" | "LGS";
            /** Format: date-time */
            createdAt?: string;
            /** Format: date-time */
            updatedAt?: string;
        };
        UserCreatedResponseStudentResponse: {
            user?: components["schemas"]["StudentResponse"];
            passwordSetupEmailSent?: boolean;
            temporaryPassword?: string;
            temporaryPasswordAlreadyIssued?: boolean;
        };
        CreateGuardianRequest: {
            /** Format: uuid */
            branchId: string;
            firstName: string;
            lastName: string;
            /** Format: email */
            email?: string;
            phone: string;
        };
        GuardianResponse: {
            /** Format: uuid */
            id?: string;
            /** Format: uuid */
            branchId?: string;
            firstName?: string;
            lastName?: string;
            email?: string;
            phone?: string;
            username?: string;
            active?: boolean;
            /** Format: date-time */
            createdAt?: string;
            /** Format: date-time */
            updatedAt?: string;
        };
        UserCreatedResponseGuardianResponse: {
            user?: components["schemas"]["GuardianResponse"];
            passwordSetupEmailSent?: boolean;
            temporaryPassword?: string;
            temporaryPasswordAlreadyIssued?: boolean;
        };
        CreateClassroomRequest: {
            /** Format: uuid */
            branchId: string;
            name: string;
            /** Format: int32 */
            gradeLevel: number;
            term: string;
        };
        ClassroomResponse: {
            /** Format: uuid */
            id?: string;
            /** Format: uuid */
            branchId?: string;
            name?: string;
            /** Format: int32 */
            gradeLevel?: number;
            term?: string;
            active?: boolean;
            /** Format: int64 */
            activeStudentCount?: number;
            /** Format: date-time */
            createdAt?: string;
            /** Format: date-time */
            updatedAt?: string;
        };
        CreateBranchRequest: {
            name: string;
            code: string;
        };
        BranchResponse: {
            /** Format: uuid */
            id?: string;
            name?: string;
            code?: string;
            active?: boolean;
            /** Format: date-time */
            createdAt?: string;
            /** Format: date-time */
            updatedAt?: string;
        };
        CreateAdminRequest: {
            /** Format: uuid */
            branchId: string;
            firstName: string;
            lastName: string;
            /** Format: email */
            email: string;
            phone?: string;
        };
        UserCreatedResponseUserResponse: {
            user?: components["schemas"]["UserResponse"];
            passwordSetupEmailSent?: boolean;
            temporaryPassword?: string;
            temporaryPasswordAlreadyIssued?: boolean;
        };
        UpdateTeacherRequest: {
            firstName?: string;
            lastName?: string;
            /** Format: email */
            email?: string;
            phone?: string;
            /** Format: uuid */
            specialtySubjectId?: string;
        };
        UpdateSubjectRequest: {
            name: string;
        };
        UpdateStudentRequest: {
            firstName?: string;
            lastName?: string;
            /** Format: email */
            email?: string;
            phone?: string;
            studentNumber?: string;
            /** Format: int32 */
            gradeLevel?: number;
            /** @enum {string} */
            targetExam?: "YKS" | "LGS";
        };
        UpdateGuardianRequest: {
            firstName?: string;
            lastName?: string;
            /** Format: email */
            email?: string;
            phone?: string;
        };
        UpdateClassroomRequest: {
            name?: string;
            term?: string;
        };
        UpdateBranchRequest: {
            name: string;
        };
        UpdateAdminRequest: {
            firstName?: string;
            lastName?: string;
            /** Format: email */
            email?: string;
            phone?: string;
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
        PagedModelTeacherResponse: {
            content?: components["schemas"]["TeacherResponse"][];
            page?: components["schemas"]["PageMetadata"];
        };
        PagedModelSubjectResponse: {
            content?: components["schemas"]["SubjectResponse"][];
            page?: components["schemas"]["PageMetadata"];
        };
        PagedModelStudentResponse: {
            content?: components["schemas"]["StudentResponse"][];
            page?: components["schemas"]["PageMetadata"];
        };
        GuardianChildResponse: {
            /** Format: uuid */
            studentId?: string;
            firstName?: string;
            lastName?: string;
            studentNumber?: string;
            /** Format: int32 */
            gradeLevel?: number;
            /** @enum {string} */
            relationship?: "MOTHER" | "FATHER" | "LEGAL_GUARDIAN" | "OTHER";
            active?: boolean;
        };
        PagedModelGuardianResponse: {
            content?: components["schemas"]["GuardianResponse"][];
            page?: components["schemas"]["PageMetadata"];
        };
        PagedModelClassroomResponse: {
            content?: components["schemas"]["ClassroomResponse"][];
            page?: components["schemas"]["PageMetadata"];
        };
        ClassroomTeacherResponse: {
            /** Format: uuid */
            assignmentId?: string;
            /** Format: uuid */
            teacherId?: string;
            firstName?: string;
            lastName?: string;
            /** Format: uuid */
            subjectId?: string;
            subjectName?: string;
        };
        ClassroomStudentResponse: {
            /** Format: uuid */
            studentId?: string;
            firstName?: string;
            lastName?: string;
            studentNumber?: string;
            /** Format: uuid */
            membershipId?: string;
            /** Format: date */
            startDate?: string;
        };
        PagedModelBranchResponse: {
            content?: components["schemas"]["BranchResponse"][];
            page?: components["schemas"]["PageMetadata"];
        };
        PagedModelUserResponse: {
            content?: components["schemas"]["UserResponse"][];
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
                studentId: string;
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
                    "*/*": components["schemas"]["StudentGuardianResponse"];
                };
            };
        };
    };
    link: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                studentId: string;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["LinkGuardianRequest"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["StudentGuardianResponse"];
                };
            };
        };
    };
    unlink: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                studentId: string;
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
                content?: never;
            };
        };
    };
    assign: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                studentId: string;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["AssignClassroomRequest"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ClassroomMembershipResponse"];
                };
            };
        };
    };
    remove: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                studentId: string;
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
                    "*/*": components["schemas"]["ClassroomMembershipResponse"];
                };
            };
        };
    };
    resetTemporaryPassword: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                userId: string;
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
                    "*/*": components["schemas"]["TemporaryPasswordResponse"];
                };
            };
        };
    };
    sendPasswordSetupEmail: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                userId: string;
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
                content?: never;
            };
        };
    };
    deactivate: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                userId: string;
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
                    "*/*": components["schemas"]["UserResponse"];
                };
            };
        };
    };
    list: {
        parameters: {
            query: {
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
                    "*/*": components["schemas"]["PagedModelTeacherResponse"];
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
                "application/json": components["schemas"]["CreateTeacherRequest"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["UserCreatedResponseTeacherResponse"];
                };
            };
        };
    };
    list_1: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                teacherId: string;
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
                    "*/*": components["schemas"]["TeacherAssignmentResponse"][];
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
            path: {
                teacherId: string;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["CreateTeacherAssignmentRequest"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["TeacherAssignmentResponse"];
                };
            };
        };
    };
    list_2: {
        parameters: {
            query: {
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
                    "*/*": components["schemas"]["PagedModelSubjectResponse"];
                };
            };
        };
    };
    create_2: {
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
                "application/json": components["schemas"]["CreateSubjectRequest"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["SubjectResponse"];
                };
            };
        };
    };
    deactivate_1: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                subjectId: string;
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
                    "*/*": components["schemas"]["SubjectResponse"];
                };
            };
        };
    };
    list_3: {
        parameters: {
            query: {
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
                    "*/*": components["schemas"]["PagedModelStudentResponse"];
                };
            };
        };
    };
    create_3: {
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
                "application/json": components["schemas"]["CreateStudentRequest"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["UserCreatedResponseStudentResponse"];
                };
            };
        };
    };
    list_4: {
        parameters: {
            query: {
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
                    "*/*": components["schemas"]["PagedModelGuardianResponse"];
                };
            };
        };
    };
    create_4: {
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
                "application/json": components["schemas"]["CreateGuardianRequest"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["UserCreatedResponseGuardianResponse"];
                };
            };
        };
    };
    list_5: {
        parameters: {
            query: {
                branchId?: string;
                term?: string;
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
                    "*/*": components["schemas"]["PagedModelClassroomResponse"];
                };
            };
        };
    };
    create_5: {
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
                "application/json": components["schemas"]["CreateClassroomRequest"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ClassroomResponse"];
                };
            };
        };
    };
    deactivate_2: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                classroomId: string;
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
                    "*/*": components["schemas"]["ClassroomResponse"];
                };
            };
        };
    };
    list_6: {
        parameters: {
            query: {
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
                    "*/*": components["schemas"]["PagedModelBranchResponse"];
                };
            };
        };
    };
    create_6: {
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
                "application/json": components["schemas"]["CreateBranchRequest"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["BranchResponse"];
                };
            };
        };
    };
    deactivate_3: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                branchId: string;
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
                    "*/*": components["schemas"]["BranchResponse"];
                };
            };
        };
    };
    list_7: {
        parameters: {
            query: {
                branchId?: string;
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
                    "*/*": components["schemas"]["PagedModelUserResponse"];
                };
            };
        };
    };
    create_7: {
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
                "application/json": components["schemas"]["CreateAdminRequest"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["UserCreatedResponseUserResponse"];
                };
            };
        };
    };
    get_1: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                teacherId: string;
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
                    "*/*": components["schemas"]["TeacherResponse"];
                };
            };
        };
    };
    update: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                teacherId: string;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["UpdateTeacherRequest"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["TeacherResponse"];
                };
            };
        };
    };
    get_2: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                subjectId: string;
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
                    "*/*": components["schemas"]["SubjectResponse"];
                };
            };
        };
    };
    update_1: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                subjectId: string;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["UpdateSubjectRequest"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["SubjectResponse"];
                };
            };
        };
    };
    get_3: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                studentId: string;
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
                    "*/*": components["schemas"]["StudentResponse"];
                };
            };
        };
    };
    update_2: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                studentId: string;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["UpdateStudentRequest"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["StudentResponse"];
                };
            };
        };
    };
    get_4: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                guardianId: string;
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
                    "*/*": components["schemas"]["GuardianResponse"];
                };
            };
        };
    };
    update_3: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                guardianId: string;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["UpdateGuardianRequest"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["GuardianResponse"];
                };
            };
        };
    };
    get_5: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                classroomId: string;
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
                    "*/*": components["schemas"]["ClassroomResponse"];
                };
            };
        };
    };
    update_4: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                classroomId: string;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["UpdateClassroomRequest"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["ClassroomResponse"];
                };
            };
        };
    };
    get_6: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                branchId: string;
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
                    "*/*": components["schemas"]["BranchResponse"];
                };
            };
        };
    };
    update_5: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                branchId: string;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["UpdateBranchRequest"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["BranchResponse"];
                };
            };
        };
    };
    update_6: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                adminId: string;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["UpdateAdminRequest"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": components["schemas"]["UserResponse"];
                };
            };
        };
    };
    get_7: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                userId: string;
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
                    "*/*": components["schemas"]["UserResponse"];
                };
            };
        };
    };
    me: {
        parameters: {
            query?: never;
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
                    "*/*": components["schemas"]["UserResponse"];
                };
            };
        };
    };
    classrooms: {
        parameters: {
            query?: never;
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
                    "*/*": components["schemas"]["ClassroomResponse"][];
                };
            };
        };
    };
    children: {
        parameters: {
            query?: never;
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
                    "*/*": components["schemas"]["GuardianChildResponse"][];
                };
            };
        };
    };
    children_1: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                guardianId: string;
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
                    "*/*": components["schemas"]["GuardianChildResponse"][];
                };
            };
        };
    };
    teachers: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                classroomId: string;
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
                    "*/*": components["schemas"]["ClassroomTeacherResponse"][];
                };
            };
        };
    };
    students: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                classroomId: string;
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
                    "*/*": components["schemas"]["ClassroomStudentResponse"][];
                };
            };
        };
    };
    delete: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                teacherId: string;
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
                content?: never;
            };
        };
    };
}
