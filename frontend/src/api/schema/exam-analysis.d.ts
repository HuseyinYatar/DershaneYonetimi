export interface paths {
    "/health/live": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** Live */
        get: operations["live_health_live_get"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/health/ready": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Ready
         * @description PostgreSQL is required; Redis is optional (the service falls back to the database).
         */
        get: operations["ready_health_ready_get"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/exam-types": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** List Exam Types */
        get: operations["list_exam_types_api_v1_exam_types_get"];
        put?: never;
        /** Create Exam Type */
        post: operations["create_exam_type_api_v1_exam_types_post"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/exam-types/{exam_type_id}/subjects": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** List Subjects */
        get: operations["list_subjects_api_v1_exam_types__exam_type_id__subjects_get"];
        put?: never;
        /** Create Subject */
        post: operations["create_subject_api_v1_exam_types__exam_type_id__subjects_post"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/subjects/{subject_id}/topics": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** List Topics */
        get: operations["list_topics_api_v1_subjects__subject_id__topics_get"];
        put?: never;
        /** Create Topic */
        post: operations["create_topic_api_v1_subjects__subject_id__topics_post"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/exam-types/{exam_type_id}/score-types": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** List Score Types */
        get: operations["list_score_types_api_v1_exam_types__exam_type_id__score_types_get"];
        put?: never;
        /** Create Score Type */
        post: operations["create_score_type_api_v1_exam_types__exam_type_id__score_types_post"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/score-types/{score_type_id}/weights": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        /** Replace Score Type Weights */
        put: operations["replace_score_type_weights_api_v1_score_types__score_type_id__weights_put"];
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/optical-formats": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** List Optical Formats */
        get: operations["list_optical_formats_api_v1_optical_formats_get"];
        put?: never;
        /** Create Optical Format */
        post: operations["create_optical_format_api_v1_optical_formats_post"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/mock-exams": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** List Mock Exams */
        get: operations["list_mock_exams_api_v1_mock_exams_get"];
        put?: never;
        /** Create Mock Exam */
        post: operations["create_mock_exam_api_v1_mock_exams_post"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/mock-exams/{mock_exam_id}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** Get Mock Exam */
        get: operations["get_mock_exam_api_v1_mock_exams__mock_exam_id__get"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/mock-exams/{mock_exam_id}/answer-key": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** Get Answer Key */
        get: operations["get_answer_key_api_v1_mock_exams__mock_exam_id__answer_key_get"];
        /** Replace Answer Key */
        put: operations["replace_answer_key_api_v1_mock_exams__mock_exam_id__answer_key_put"];
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/mock-exams/{mock_exam_id}/answer-key/{item_id}/cancel": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /** Cancel Question */
        post: operations["cancel_question_api_v1_mock_exams__mock_exam_id__answer_key__item_id__cancel_post"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/mock-exams/{mock_exam_id}/import-jobs": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /** Upload Optical File */
        post: operations["upload_optical_file_api_v1_mock_exams__mock_exam_id__import_jobs_post"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/mock-exams/{mock_exam_id}/publish": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /** Publish Results */
        post: operations["publish_results_api_v1_mock_exams__mock_exam_id__publish_post"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/mock-exams/{mock_exam_id}/statistics": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** List Statistics */
        get: operations["list_statistics_api_v1_mock_exams__mock_exam_id__statistics_get"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/mock-exams/{mock_exam_id}/statistics/national": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        /** Set National Statistics */
        put: operations["set_national_statistics_api_v1_mock_exams__mock_exam_id__statistics_national_put"];
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/mock-exams/{mock_exam_id}/results": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** Exam Results */
        get: operations["exam_results_api_v1_mock_exams__mock_exam_id__results_get"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/mock-exams/{mock_exam_id}/topic-analysis": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** Topic Analysis */
        get: operations["topic_analysis_api_v1_mock_exams__mock_exam_id__topic_analysis_get"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/import-jobs/{job_id}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** Get Import Job */
        get: operations["get_import_job_api_v1_import_jobs__job_id__get"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/import-jobs/{job_id}/unmatched-rows": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** List Unmatched Rows */
        get: operations["list_unmatched_rows_api_v1_import_jobs__job_id__unmatched_rows_get"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/unmatched-rows/{row_id}/match": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /** Match Unmatched Row */
        post: operations["match_unmatched_row_api_v1_unmatched_rows__row_id__match_post"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/unmatched-rows/{row_id}/ignore": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        /** Ignore Unmatched Row */
        post: operations["ignore_unmatched_row_api_v1_unmatched_rows__row_id__ignore_post"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/students/{student_id}/results": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** Student Results */
        get: operations["student_results_api_v1_students__student_id__results_get"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/students/{student_id}/results/{mock_exam_id}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** Student Result Detail */
        get: operations["student_result_detail_api_v1_students__student_id__results__mock_exam_id__get"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/students/{student_id}/progress": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** Student Progress */
        get: operations["student_progress_api_v1_students__student_id__progress_get"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/students/{student_id}/weak-topics": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** Weak Topics */
        get: operations["weak_topics_api_v1_students__student_id__weak_topics_get"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/branches/{branch_id}/weak-topic-setting": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** Get Weak Topic Setting */
        get: operations["get_weak_topic_setting_api_v1_branches__branch_id__weak_topic_setting_get"];
        /** Put Weak Topic Setting */
        put: operations["put_weak_topic_setting_api_v1_branches__branch_id__weak_topic_setting_put"];
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/admin/student-replicas": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        /** Temporary: load students until the StudentAddedToClass consumer exists (week 4) */
        put: operations["upsert_student_replicas_api_v1_admin_student_replicas_put"];
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
        /** AnswerKeyItemIn */
        AnswerKeyItemIn: {
            /** Booklet */
            booklet: string;
            /** Questionno */
            questionNo: number;
            /**
             * Subjectid
             * Format: uuid
             */
            subjectId: string;
            /**
             * Topicid
             * Format: uuid
             */
            topicId: string;
            /** Correctanswer */
            correctAnswer: string;
        };
        /** AnswerKeyItemRead */
        AnswerKeyItemRead: {
            /**
             * Id
             * Format: uuid
             */
            id: string;
            /** Booklet */
            booklet: string;
            /** Questionno */
            questionNo: number;
            /**
             * Subjectid
             * Format: uuid
             */
            subjectId: string;
            /**
             * Topicid
             * Format: uuid
             */
            topicId: string;
            /** Correctanswer */
            correctAnswer: string;
            /** Iscancelled */
            isCancelled: boolean;
        };
        /** AnswerKeyReplace */
        AnswerKeyReplace: {
            /** Items */
            items: components["schemas"]["AnswerKeyItemIn"][];
        };
        /** Body_upload_optical_file_api_v1_mock_exams__mock_exam_id__import_jobs_post */
        Body_upload_optical_file_api_v1_mock_exams__mock_exam_id__import_jobs_post: {
            /**
             * File
             * @description Optical reader export (fixed-width text)
             */
            file: string;
            /**
             * Opticalformatid
             * Format: uuid
             */
            opticalFormatId: string;
        };
        /** BulkUpsertResult */
        BulkUpsertResult: {
            /** Upserted */
            upserted: number;
        };
        /** ExamResultRow */
        ExamResultRow: {
            /**
             * Studentid
             * Format: uuid
             */
            studentId: string;
            /** Fullname */
            fullName: string | null;
            /** Booklet */
            booklet: string;
            /** Correct */
            correct: number;
            /** Wrong */
            wrong: number;
            /** Blank */
            blank: number;
            /** Net */
            net: string;
            /** Branchrank */
            branchRank: number | null;
            /** Scores */
            scores: components["schemas"]["ScoreRead"][];
        };
        /**
         * ExamStatus
         * @enum {string}
         */
        ExamStatus: "DRAFT" | "KEY_READY" | "PROCESSED" | "PUBLISHED";
        /** ExamTypeCreate */
        ExamTypeCreate: {
            /** Code */
            code: string;
            /** Name */
            name: string;
            /** Wrongdivisor */
            wrongDivisor: number | string;
        };
        /** ExamTypeRead */
        ExamTypeRead: {
            /**
             * Id
             * Format: uuid
             */
            id: string;
            /** Code */
            code: string;
            /** Name */
            name: string;
            /** Wrongdivisor */
            wrongDivisor: string;
        };
        /**
         * FieldSlice
         * @description A fixed-width column range; start is a 0-based character offset.
         */
        FieldSlice: {
            /** Start */
            start: number;
            /** Length */
            length: number;
        };
        /** HTTPValidationError */
        HTTPValidationError: {
            /** Detail */
            detail?: components["schemas"]["ValidationError"][];
        };
        /** ImportJobRead */
        ImportJobRead: {
            /**
             * Id
             * Format: uuid
             */
            id: string;
            /**
             * Mockexamid
             * Format: uuid
             */
            mockExamId: string;
            /**
             * Opticalformatid
             * Format: uuid
             */
            opticalFormatId: string;
            /**
             * Fileid
             * Format: uuid
             */
            fileId: string;
            source: components["schemas"]["ImportSource"];
            /** Sourceref */
            sourceRef: string | null;
            status: components["schemas"]["JobStatus"];
            /** Totalrows */
            totalRows: number;
            /** Succeededrows */
            succeededRows: number;
            /** Failedrows */
            failedRows: number;
            /** Errormessage */
            errorMessage: string | null;
            /**
             * Requestedby
             * Format: uuid
             */
            requestedBy: string;
            /**
             * Createdat
             * Format: date-time
             */
            createdAt: string;
            /** Startedat */
            startedAt: string | null;
            /** Finishedat */
            finishedAt: string | null;
        };
        /**
         * ImportSource
         * @enum {string}
         */
        ImportSource: "FILE_UPLOAD" | "OMR";
        /**
         * JobStatus
         * @enum {string}
         */
        JobStatus: "QUEUED" | "PROCESSING" | "COMPLETED" | "FAILED";
        /** MatchRowRequest */
        MatchRowRequest: {
            /**
             * Studentid
             * Format: uuid
             */
            studentId: string;
        };
        /**
         * MatchStatus
         * @enum {string}
         */
        MatchStatus: "PENDING" | "MATCHED" | "IGNORED";
        /** MockExamCreate */
        MockExamCreate: {
            /**
             * Examtypeid
             * Format: uuid
             */
            examTypeId: string;
            /** Name */
            name: string;
            /** Publisher */
            publisher?: string | null;
            /**
             * Examdate
             * Format: date
             */
            examDate: string;
            /**
             * Branchid
             * Format: uuid
             */
            branchId: string;
            scoringSource: components["schemas"]["ScoringSource"];
        };
        /** MockExamRead */
        MockExamRead: {
            /**
             * Id
             * Format: uuid
             */
            id: string;
            /**
             * Examtypeid
             * Format: uuid
             */
            examTypeId: string;
            /** Name */
            name: string;
            /** Publisher */
            publisher: string | null;
            /**
             * Examdate
             * Format: date
             */
            examDate: string;
            /**
             * Branchid
             * Format: uuid
             */
            branchId: string;
            status: components["schemas"]["ExamStatus"];
            scoringSource: components["schemas"]["ScoringSource"];
            /**
             * Createdat
             * Format: date-time
             */
            createdAt: string;
        };
        /** NationalStatisticItem */
        NationalStatisticItem: {
            /**
             * Subjectid
             * Format: uuid
             */
            subjectId: string;
            /** Mean */
            mean: number | string;
            /** Stddev */
            stdDev: number | string;
            /** Participantcount */
            participantCount: number;
        };
        /** NationalStatisticsUpdate */
        NationalStatisticsUpdate: {
            /** Items */
            items: components["schemas"]["NationalStatisticItem"][];
        };
        /**
         * OpticalFieldDefinition
         * @description Layout of one optical reader export line (stored in optical_format.field_definition).
         *
         *     The answer block may be split into segments (e.g. one per test on the form); segments are
         *     concatenated in order and question numbers run 1..question_count across them.
         *     Stored as camelCase JSON, like the rest of the API.
         */
        OpticalFieldDefinition: {
            /**
             * Encoding
             * @default utf-8
             */
            encoding: string;
            studentNo: components["schemas"]["FieldSlice"];
            booklet?: components["schemas"]["FieldSlice"] | null;
            /**
             * Defaultbooklet
             * @default A
             */
            defaultBooklet: string;
            /** Answersegments */
            answerSegments: components["schemas"]["FieldSlice"][];
            sheetRef?: components["schemas"]["FieldSlice"] | null;
            /**
             * Blankchars
             * @default
             */
            blankChars: string;
        };
        /** OpticalFormatCreate */
        OpticalFormatCreate: {
            /** Code */
            code: string;
            /** Name */
            name: string;
            fieldDefinition: components["schemas"]["OpticalFieldDefinition"];
        };
        /** OpticalFormatRead */
        OpticalFormatRead: {
            /**
             * Id
             * Format: uuid
             */
            id: string;
            /** Code */
            code: string;
            /** Name */
            name: string;
            /** Fielddefinition */
            fieldDefinition: {
                [key: string]: unknown;
            };
        };
        /** ProgressPoint */
        ProgressPoint: {
            /**
             * Mockexamid
             * Format: uuid
             */
            mockExamId: string;
            /** Examname */
            examName: string;
            /**
             * Examdate
             * Format: date
             */
            examDate: string;
            /** Net */
            net: string;
            /** Subjects */
            subjects: components["schemas"]["ProgressSubjectNet"][];
        };
        /** ProgressSubjectNet */
        ProgressSubjectNet: {
            /**
             * Subjectid
             * Format: uuid
             */
            subjectId: string;
            /** Net */
            net: string;
        };
        /**
         * ScoreRead
         * @description Always an estimate. With coefficient_year the UI labels it "tahmini (2026 katsayılarıyla)";
         *     otherwise (standard-score model) "tahmini (kurum içi)" or "tahmini (Türkiye geneli)".
         */
        ScoreRead: {
            /**
             * Scoretypeid
             * Format: uuid
             */
            scoreTypeId: string;
            /** Scoretypecode */
            scoreTypeCode: string;
            /** Estimatedscore */
            estimatedScore: string | null;
            /**
             * Isestimated
             * @default true
             * @constant
             */
            isEstimated: true;
            source: components["schemas"]["ScoringSource"] | null;
            /** Branchrank */
            branchRank: number | null;
            /** Coefficientyear */
            coefficientYear?: number | null;
            /** Notcomputedreason */
            notComputedReason?: string | null;
            /** Linkedexamname */
            linkedExamName?: string | null;
        };
        /** ScoreTypeCreate */
        ScoreTypeCreate: {
            /** Code */
            code: string;
            /** Name */
            name: string;
            /** Basescore */
            baseScore?: number | string | null;
        };
        /** ScoreTypeRead */
        ScoreTypeRead: {
            /**
             * Id
             * Format: uuid
             */
            id: string;
            /**
             * Examtypeid
             * Format: uuid
             */
            examTypeId: string;
            /** Code */
            code: string;
            /** Name */
            name: string;
            /** Basescore */
            baseScore: string | null;
            /** Weights */
            weights: components["schemas"]["ScoreTypeWeightItem-Output"][];
        };
        /** ScoreTypeWeightItem */
        "ScoreTypeWeightItem-Input": {
            /**
             * Subjectid
             * Format: uuid
             */
            subjectId: string;
            /** Weight */
            weight: number | string;
        };
        /** ScoreTypeWeightItem */
        "ScoreTypeWeightItem-Output": {
            /**
             * Subjectid
             * Format: uuid
             */
            subjectId: string;
            /** Weight */
            weight: string;
        };
        /** ScoreTypeWeightsUpdate */
        ScoreTypeWeightsUpdate: {
            /** Weights */
            weights: components["schemas"]["ScoreTypeWeightItem-Input"][];
        };
        /**
         * ScoringSource
         * @enum {string}
         */
        ScoringSource: "INSTITUTIONAL" | "NATIONAL";
        /** StatisticRead */
        StatisticRead: {
            /**
             * Subjectid
             * Format: uuid
             */
            subjectId: string;
            source: components["schemas"]["ScoringSource"];
            /** Mean */
            mean: string;
            /** Stddev */
            stdDev: string;
            /** Participantcount */
            participantCount: number;
            /**
             * Updatedat
             * Format: date-time
             */
            updatedAt: string;
        };
        /** StudentExamSummary */
        StudentExamSummary: {
            /**
             * Mockexamid
             * Format: uuid
             */
            mockExamId: string;
            /** Examname */
            examName: string;
            /**
             * Examtypeid
             * Format: uuid
             */
            examTypeId: string;
            /**
             * Examdate
             * Format: date
             */
            examDate: string;
            /** Net */
            net: string;
            /** Branchrank */
            branchRank: number | null;
            /** Scores */
            scores: components["schemas"]["ScoreRead"][];
        };
        /** StudentProgress */
        StudentProgress: {
            /**
             * Studentid
             * Format: uuid
             */
            studentId: string;
            /**
             * Examtypeid
             * Format: uuid
             */
            examTypeId: string;
            /** Points */
            points: components["schemas"]["ProgressPoint"][];
        };
        /** StudentReplicaBulkUpsert */
        StudentReplicaBulkUpsert: {
            /** Students */
            students: components["schemas"]["StudentReplicaIn"][];
        };
        /** StudentReplicaIn */
        StudentReplicaIn: {
            /**
             * Studentid
             * Format: uuid
             */
            studentId: string;
            /** Studentno */
            studentNo: string;
            /**
             * Branchid
             * Format: uuid
             */
            branchId: string;
            /**
             * Classid
             * Format: uuid
             */
            classId: string;
            /** Fullname */
            fullName: string;
        };
        /** StudentResultDetail */
        StudentResultDetail: {
            /**
             * Mockexamid
             * Format: uuid
             */
            mockExamId: string;
            /** Examname */
            examName: string;
            /**
             * Examtypeid
             * Format: uuid
             */
            examTypeId: string;
            /**
             * Examdate
             * Format: date
             */
            examDate: string;
            /** Net */
            net: string;
            /** Branchrank */
            branchRank: number | null;
            /** Scores */
            scores: components["schemas"]["ScoreRead"][];
            /** Booklet */
            booklet: string;
            /** Correct */
            correct: number;
            /** Wrong */
            wrong: number;
            /** Blank */
            blank: number;
            /** Subjects */
            subjects: components["schemas"]["SubjectResultRead"][];
            /** Topics */
            topics: components["schemas"]["TopicResultRead"][];
        };
        /** SubjectCreate */
        SubjectCreate: {
            /** Code */
            code: string;
            /** Name */
            name: string;
        };
        /** SubjectRead */
        SubjectRead: {
            /**
             * Id
             * Format: uuid
             */
            id: string;
            /**
             * Examtypeid
             * Format: uuid
             */
            examTypeId: string;
            /** Code */
            code: string;
            /** Name */
            name: string;
        };
        /** SubjectResultRead */
        SubjectResultRead: {
            /**
             * Subjectid
             * Format: uuid
             */
            subjectId: string;
            /** Subjectname */
            subjectName: string;
            /** Correct */
            correct: number;
            /** Wrong */
            wrong: number;
            /** Blank */
            blank: number;
            /** Net */
            net: string;
        };
        /** TopicAnalysisItem */
        TopicAnalysisItem: {
            /**
             * Topicid
             * Format: uuid
             */
            topicId: string;
            /** Topicname */
            topicName: string;
            /**
             * Subjectid
             * Format: uuid
             */
            subjectId: string;
            /** Studentcount */
            studentCount: number;
            /** Questioncount */
            questionCount: number;
            /** Correct */
            correct: number;
            /** Successrate */
            successRate: number;
        };
        /** TopicCreate */
        TopicCreate: {
            /** Name */
            name: string;
            /** Parenttopicid */
            parentTopicId?: string | null;
        };
        /** TopicRead */
        TopicRead: {
            /**
             * Id
             * Format: uuid
             */
            id: string;
            /**
             * Subjectid
             * Format: uuid
             */
            subjectId: string;
            /** Name */
            name: string;
            /** Parenttopicid */
            parentTopicId: string | null;
        };
        /** TopicResultRead */
        TopicResultRead: {
            /**
             * Topicid
             * Format: uuid
             */
            topicId: string;
            /** Topicname */
            topicName: string;
            /** Questioncount */
            questionCount: number;
            /** Correct */
            correct: number;
            /** Successrate */
            successRate: string;
        };
        /**
         * UnmatchedReason
         * @enum {string}
         */
        UnmatchedReason: "STUDENT_NOT_FOUND" | "DUPLICATE_STUDENT" | "INVALID_BOOKLET" | "MALFORMED_LINE";
        /** UnmatchedRowRead */
        UnmatchedRowRead: {
            /**
             * Id
             * Format: uuid
             */
            id: string;
            /**
             * Importjobid
             * Format: uuid
             */
            importJobId: string;
            /** Lineno */
            lineNo: number;
            /** Rawstudentno */
            rawStudentNo: string;
            /** Rawline */
            rawLine: string;
            reason: components["schemas"]["UnmatchedReason"];
            status: components["schemas"]["MatchStatus"];
            /** Sheetimagefileid */
            sheetImageFileId: string | null;
            /** Matchedstudentid */
            matchedStudentId: string | null;
            /** Resolvedby */
            resolvedBy: string | null;
            /** Resolvedat */
            resolvedAt: string | null;
        };
        /** ValidationError */
        ValidationError: {
            /** Location */
            loc: (string | number)[];
            /** Message */
            msg: string;
            /** Error Type */
            type: string;
            /** Input */
            input?: unknown;
            /** Context */
            ctx?: Record<string, never>;
        };
        /** WeakTopicRead */
        WeakTopicRead: {
            /**
             * Topicid
             * Format: uuid
             */
            topicId: string;
            /** Topicname */
            topicName: string;
            /**
             * Subjectid
             * Format: uuid
             */
            subjectId: string;
            /** Parenttopicid */
            parentTopicId: string | null;
            /** Totalquestions */
            totalQuestions: number;
            /** Totalcorrect */
            totalCorrect: number;
            /** Measuredexamcount */
            measuredExamCount: number;
            /** Successrate */
            successRate: string;
        };
        /**
         * WeakTopicReport
         * @description Weak topics, weakest first: the "study these topics" recommendation for students and teachers.
         */
        WeakTopicReport: {
            /**
             * Studentid
             * Format: uuid
             */
            studentId: string;
            /** Windowsize */
            windowSize: number;
            /** Successthreshold */
            successThreshold: string;
            /** Weaktopics */
            weakTopics: components["schemas"]["WeakTopicRead"][];
        };
        /** WeakTopicSettingRead */
        WeakTopicSettingRead: {
            /**
             * Branchid
             * Format: uuid
             */
            branchId: string;
            /** Windowsize */
            windowSize: number;
            /** Successthreshold */
            successThreshold: string;
            /**
             * Updatedby
             * Format: uuid
             */
            updatedBy: string;
            /**
             * Updatedat
             * Format: date-time
             */
            updatedAt: string;
        };
        /** WeakTopicSettingUpdate */
        WeakTopicSettingUpdate: {
            /** Windowsize */
            windowSize: number;
            /** Successthreshold */
            successThreshold: number | string;
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
    live_health_live_get: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description Successful Response */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": {
                        [key: string]: string;
                    };
                };
            };
        };
    };
    ready_health_ready_get: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description Successful Response */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": unknown;
                };
            };
        };
    };
    list_exam_types_api_v1_exam_types_get: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description Successful Response */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ExamTypeRead"][];
                };
            };
        };
    };
    create_exam_type_api_v1_exam_types_post: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["ExamTypeCreate"];
            };
        };
        responses: {
            /** @description Successful Response */
            201: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ExamTypeRead"];
                };
            };
            /** @description Validation Error */
            422: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["HTTPValidationError"];
                };
            };
        };
    };
    list_subjects_api_v1_exam_types__exam_type_id__subjects_get: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                exam_type_id: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description Successful Response */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["SubjectRead"][];
                };
            };
            /** @description Validation Error */
            422: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["HTTPValidationError"];
                };
            };
        };
    };
    create_subject_api_v1_exam_types__exam_type_id__subjects_post: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                exam_type_id: string;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["SubjectCreate"];
            };
        };
        responses: {
            /** @description Successful Response */
            201: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["SubjectRead"];
                };
            };
            /** @description Validation Error */
            422: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["HTTPValidationError"];
                };
            };
        };
    };
    list_topics_api_v1_subjects__subject_id__topics_get: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                subject_id: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description Successful Response */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["TopicRead"][];
                };
            };
            /** @description Validation Error */
            422: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["HTTPValidationError"];
                };
            };
        };
    };
    create_topic_api_v1_subjects__subject_id__topics_post: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                subject_id: string;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["TopicCreate"];
            };
        };
        responses: {
            /** @description Successful Response */
            201: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["TopicRead"];
                };
            };
            /** @description Validation Error */
            422: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["HTTPValidationError"];
                };
            };
        };
    };
    list_score_types_api_v1_exam_types__exam_type_id__score_types_get: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                exam_type_id: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description Successful Response */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ScoreTypeRead"][];
                };
            };
            /** @description Validation Error */
            422: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["HTTPValidationError"];
                };
            };
        };
    };
    create_score_type_api_v1_exam_types__exam_type_id__score_types_post: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                exam_type_id: string;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["ScoreTypeCreate"];
            };
        };
        responses: {
            /** @description Successful Response */
            201: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ScoreTypeRead"];
                };
            };
            /** @description Validation Error */
            422: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["HTTPValidationError"];
                };
            };
        };
    };
    replace_score_type_weights_api_v1_score_types__score_type_id__weights_put: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                score_type_id: string;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["ScoreTypeWeightsUpdate"];
            };
        };
        responses: {
            /** @description Successful Response */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ScoreTypeRead"];
                };
            };
            /** @description Validation Error */
            422: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["HTTPValidationError"];
                };
            };
        };
    };
    list_optical_formats_api_v1_optical_formats_get: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description Successful Response */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["OpticalFormatRead"][];
                };
            };
        };
    };
    create_optical_format_api_v1_optical_formats_post: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["OpticalFormatCreate"];
            };
        };
        responses: {
            /** @description Successful Response */
            201: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["OpticalFormatRead"];
                };
            };
            /** @description Validation Error */
            422: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["HTTPValidationError"];
                };
            };
        };
    };
    list_mock_exams_api_v1_mock_exams_get: {
        parameters: {
            query: {
                branchId: string;
                status?: components["schemas"]["ExamStatus"] | null;
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description Successful Response */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["MockExamRead"][];
                };
            };
            /** @description Validation Error */
            422: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["HTTPValidationError"];
                };
            };
        };
    };
    create_mock_exam_api_v1_mock_exams_post: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["MockExamCreate"];
            };
        };
        responses: {
            /** @description Successful Response */
            201: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["MockExamRead"];
                };
            };
            /** @description Validation Error */
            422: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["HTTPValidationError"];
                };
            };
        };
    };
    get_mock_exam_api_v1_mock_exams__mock_exam_id__get: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                mock_exam_id: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description Successful Response */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["MockExamRead"];
                };
            };
            /** @description Validation Error */
            422: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["HTTPValidationError"];
                };
            };
        };
    };
    get_answer_key_api_v1_mock_exams__mock_exam_id__answer_key_get: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                mock_exam_id: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description Successful Response */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["AnswerKeyItemRead"][];
                };
            };
            /** @description Validation Error */
            422: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["HTTPValidationError"];
                };
            };
        };
    };
    replace_answer_key_api_v1_mock_exams__mock_exam_id__answer_key_put: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                mock_exam_id: string;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["AnswerKeyReplace"];
            };
        };
        responses: {
            /** @description Successful Response */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["AnswerKeyItemRead"][];
                };
            };
            /** @description Validation Error */
            422: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["HTTPValidationError"];
                };
            };
        };
    };
    cancel_question_api_v1_mock_exams__mock_exam_id__answer_key__item_id__cancel_post: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                mock_exam_id: string;
                item_id: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description Successful Response */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["AnswerKeyItemRead"];
                };
            };
            /** @description Validation Error */
            422: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["HTTPValidationError"];
                };
            };
        };
    };
    upload_optical_file_api_v1_mock_exams__mock_exam_id__import_jobs_post: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                mock_exam_id: string;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "multipart/form-data": components["schemas"]["Body_upload_optical_file_api_v1_mock_exams__mock_exam_id__import_jobs_post"];
            };
        };
        responses: {
            /** @description Successful Response */
            202: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ImportJobRead"];
                };
            };
            /** @description Validation Error */
            422: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["HTTPValidationError"];
                };
            };
        };
    };
    publish_results_api_v1_mock_exams__mock_exam_id__publish_post: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                mock_exam_id: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description Successful Response */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["MockExamRead"];
                };
            };
            /** @description Validation Error */
            422: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["HTTPValidationError"];
                };
            };
        };
    };
    list_statistics_api_v1_mock_exams__mock_exam_id__statistics_get: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                mock_exam_id: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description Successful Response */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["StatisticRead"][];
                };
            };
            /** @description Validation Error */
            422: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["HTTPValidationError"];
                };
            };
        };
    };
    set_national_statistics_api_v1_mock_exams__mock_exam_id__statistics_national_put: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                mock_exam_id: string;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["NationalStatisticsUpdate"];
            };
        };
        responses: {
            /** @description Successful Response */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["StatisticRead"][];
                };
            };
            /** @description Validation Error */
            422: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["HTTPValidationError"];
                };
            };
        };
    };
    exam_results_api_v1_mock_exams__mock_exam_id__results_get: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                mock_exam_id: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description Successful Response */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ExamResultRow"][];
                };
            };
            /** @description Validation Error */
            422: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["HTTPValidationError"];
                };
            };
        };
    };
    topic_analysis_api_v1_mock_exams__mock_exam_id__topic_analysis_get: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                mock_exam_id: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description Successful Response */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["TopicAnalysisItem"][];
                };
            };
            /** @description Validation Error */
            422: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["HTTPValidationError"];
                };
            };
        };
    };
    get_import_job_api_v1_import_jobs__job_id__get: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                job_id: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description Successful Response */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ImportJobRead"];
                };
            };
            /** @description Validation Error */
            422: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["HTTPValidationError"];
                };
            };
        };
    };
    list_unmatched_rows_api_v1_import_jobs__job_id__unmatched_rows_get: {
        parameters: {
            query?: {
                status?: components["schemas"]["MatchStatus"] | null;
            };
            header?: never;
            path: {
                job_id: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description Successful Response */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["UnmatchedRowRead"][];
                };
            };
            /** @description Validation Error */
            422: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["HTTPValidationError"];
                };
            };
        };
    };
    match_unmatched_row_api_v1_unmatched_rows__row_id__match_post: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                row_id: string;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["MatchRowRequest"];
            };
        };
        responses: {
            /** @description Successful Response */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["UnmatchedRowRead"];
                };
            };
            /** @description Validation Error */
            422: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["HTTPValidationError"];
                };
            };
        };
    };
    ignore_unmatched_row_api_v1_unmatched_rows__row_id__ignore_post: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                row_id: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description Successful Response */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["UnmatchedRowRead"];
                };
            };
            /** @description Validation Error */
            422: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["HTTPValidationError"];
                };
            };
        };
    };
    student_results_api_v1_students__student_id__results_get: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                student_id: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description Successful Response */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["StudentExamSummary"][];
                };
            };
            /** @description Validation Error */
            422: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["HTTPValidationError"];
                };
            };
        };
    };
    student_result_detail_api_v1_students__student_id__results__mock_exam_id__get: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                student_id: string;
                mock_exam_id: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description Successful Response */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["StudentResultDetail"];
                };
            };
            /** @description Validation Error */
            422: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["HTTPValidationError"];
                };
            };
        };
    };
    student_progress_api_v1_students__student_id__progress_get: {
        parameters: {
            query: {
                examTypeId: string;
            };
            header?: never;
            path: {
                student_id: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description Successful Response */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["StudentProgress"];
                };
            };
            /** @description Validation Error */
            422: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["HTTPValidationError"];
                };
            };
        };
    };
    weak_topics_api_v1_students__student_id__weak_topics_get: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                student_id: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description Successful Response */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["WeakTopicReport"];
                };
            };
            /** @description Validation Error */
            422: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["HTTPValidationError"];
                };
            };
        };
    };
    get_weak_topic_setting_api_v1_branches__branch_id__weak_topic_setting_get: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                branch_id: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description Successful Response */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["WeakTopicSettingRead"];
                };
            };
            /** @description Validation Error */
            422: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["HTTPValidationError"];
                };
            };
        };
    };
    put_weak_topic_setting_api_v1_branches__branch_id__weak_topic_setting_put: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                branch_id: string;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["WeakTopicSettingUpdate"];
            };
        };
        responses: {
            /** @description Successful Response */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["WeakTopicSettingRead"];
                };
            };
            /** @description Validation Error */
            422: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["HTTPValidationError"];
                };
            };
        };
    };
    upsert_student_replicas_api_v1_admin_student_replicas_put: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["StudentReplicaBulkUpsert"];
            };
        };
        responses: {
            /** @description Successful Response */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["BulkUpsertResult"];
                };
            };
            /** @description Validation Error */
            422: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["HTTPValidationError"];
                };
            };
        };
    };
}
