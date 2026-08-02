import { z } from 'zod';

export const themeSchema = z.object({
  primary: z.string().nullable(),
  secondary: z.string().nullable(),
  tertiary: z.string().nullable(),
});

export const activeSubscriptionSchema = z.object({
  packName: z.string(),
  endsAt: z.string(),
});

export const meSchema = z.object({
  id: z.string().uuid(),
  email: z.string(),
  username: z.string(),
  lastName: z.string(),
  firstName: z.string(),
  role: z.enum(['ADMIN', 'TEACHER', 'STUDENT']),
  status: z.enum(['ACTIVE', 'DISABLED']),
  schoolId: z.string().uuid().nullable(),
  studyYear: z.number().nullable(),
  photoUrl: z.string().nullable(),
  theme: themeSchema,
  activeSubscriptions: z.array(activeSubscriptionSchema),
});
export type Me = z.infer<typeof meSchema>;

export const loginResponseSchema = z.object({
  accessToken: z.string(),
  expiresInSeconds: z.number(),
});
export type LoginResponse = z.infer<typeof loginResponseSchema>;

export const registerResponseSchema = z.object({
  id: z.string().uuid(),
  email: z.string(),
  username: z.string(),
  role: z.string(),
  status: z.string(),
  // False when the account was created but the verification email could not be
  // handed to the mail server — the UI then points the user at "renvoyer"
  // instead of at an inbox that will stay empty.
  //
  // Optional rather than required: the frontend (Vercel) and the API (Railway)
  // deploy independently, so a build of this bundle can briefly talk to an API
  // that predates these fields. A required field would fail the parse and break
  // registration outright during that window; absent is read as "nothing to
  // warn about", which is the old, correct behaviour. Hence the explicit
  // `=== false` / `=== true` tests at the call site rather than falsy checks.
  //
  // emailVerified is true only under AUTO_VERIFY_EMAILS (dev/e2e) — then the
  // account needs no confirmation and login works immediately.
  emailVerified: z.boolean().optional(),
  verificationEmailSent: z.boolean().optional(),
});

export type RegisterResponse = z.infer<typeof registerResponseSchema>;

export const schoolSchema = z.object({
  id: z.string().uuid(),
  name: z.string(),
  slug: z.string(),
});
export type School = z.infer<typeof schoolSchema>;
export const schoolListSchema = z.array(schoolSchema);

export const apiErrorSchema = z.object({
  error: z.string(),
  message: z.string(),
  timestamp: z.string(),
  // Correlation id, mirrored from the X-Request-Id response header. Optional because a
  // request that never reached the API (offline, DNS, a proxy 502) has no id to carry —
  // and because parsing must not fail on an older backend that predates it.
  requestId: z.string().optional(),
});
export type ApiError = z.infer<typeof apiErrorSchema>;

// ---------------------------------------------------------------------
// Catalogue
// ---------------------------------------------------------------------

export const moduleSchema = z.object({
  id: z.string().uuid(),
  studyYear: z.number(),
  name: z.string(),
  position: z.number(),
  published: z.boolean(),
});
export type Module = z.infer<typeof moduleSchema>;

export const courseSchema = z.object({
  id: z.string().uuid(),
  moduleId: z.string().uuid(),
  name: z.string(),
  position: z.number(),
  published: z.boolean(),
  freePreview: z.boolean(),
});
export type Course = z.infer<typeof courseSchema>;

export const sourceExamSchema = z.object({
  id: z.string().uuid(),
  label: z.string(),
  year: z.number(),
  examType: z.enum(['ENTRAINEMENT', 'EXAMEN']),
});
export type SourceExam = z.infer<typeof sourceExamSchema>;

export const labelSchema = z.object({
  id: z.string().uuid(),
  name: z.string(),
  color: z.string(),
  questionCount: z.number(),
});
export type Label = z.infer<typeof labelSchema>;

export const countSchema = z.object({ count: z.number() });

// ---------------------------------------------------------------------
// Free study library
// ---------------------------------------------------------------------

export const fileTypeSchema = z.enum(['PDF', 'IMAGE']);
export type FileType = z.infer<typeof fileTypeSchema>;

export const resourceSchema = z.object({
  id: z.string().uuid(),
  moduleId: z.string().uuid(),
  moduleName: z.string(),
  studyYear: z.number(),
  title: z.string(),
  description: z.string().nullable(),
  fileUrl: z.string(),
  fileType: fileTypeSchema,
  fileSizeBytes: z.number(),
  position: z.number(),
  published: z.boolean(),
  createdAt: z.string(),
  updatedAt: z.string(),
});
export type Resource = z.infer<typeof resourceSchema>;

export const moduleResourcesSchema = z.object({
  moduleId: z.string().uuid(),
  moduleName: z.string(),
  studyYear: z.number(),
  modulePosition: z.number(),
  resources: z.array(resourceSchema),
});
export type ModuleResources = z.infer<typeof moduleResourcesSchema>;

export const moduleResourceSummarySchema = z.object({
  moduleId: z.string().uuid(),
  moduleName: z.string(),
  studyYear: z.number(),
  resourceCount: z.number(),
  totalBytes: z.number(),
});
export type ModuleResourceSummary = z.infer<typeof moduleResourceSummarySchema>;

// ---------------------------------------------------------------------
// Sessions
// ---------------------------------------------------------------------

export const sessionTypeSchema = z.enum(['ENTRAINEMENT', 'EXAMEN']);
export type SessionType = z.infer<typeof sessionTypeSchema>;

export const questionStateSchema = z.enum(['UNANSWERED', 'ANSWERED', 'CONSULTED']);
export type QuestionState = z.infer<typeof questionStateSchema>;

export const sessionSummarySchema = z.object({
  id: z.string().uuid(),
  title: z.string(),
  sessionType: sessionTypeSchema,
  status: z.enum(['ACTIVE', 'SUBMITTED']),
  questionCount: z.number(),
  answeredCount: z.number(),
  correctCount: z.number(),
  percentCorrectSoFar: z.number(),
  totalSeconds: z.number(),
  favorite: z.boolean(),
  rating: z.number().nullable(),
  score: z.number().nullable(),
  startedAt: z.string(),
  submittedAt: z.string().nullable(),
});
export type SessionSummary = z.infer<typeof sessionSummarySchema>;

export function pageSchema<T extends z.ZodTypeAny>(item: T) {
  return z.object({
    content: z.array(item),
    page: z.number(),
    size: z.number(),
    totalElements: z.number(),
    totalPages: z.number(),
  });
}

export const propositionPlaySchema = z.object({
  id: z.string().uuid(),
  letter: z.string(),
  text: z.string(),
  position: z.number(),
});

export const questionPlaySchema = z.object({
  id: z.string().uuid(),
  courseId: z.string().uuid(),
  statement: z.string(),
  statementImages: z.array(z.string()),
  difficulty: z.enum(['EASY', 'MEDIUM', 'HARD']).nullable(),
  propositions: z.array(propositionPlaySchema),
});
export type QuestionPlay = z.infer<typeof questionPlaySchema>;

export const sessionQuestionPlaySchema = z.object({
  question: questionPlaySchema,
  state: questionStateSchema,
  isCorrect: z.boolean().nullable(),
  secondsSpent: z.number(),
  selectedPropositionIds: z.array(z.string().uuid()),
  position: z.number(),
});
export type SessionQuestionPlay = z.infer<typeof sessionQuestionPlaySchema>;

export const sessionPlaySchema = z.object({
  id: z.string().uuid(),
  title: z.string(),
  sessionType: sessionTypeSchema,
  status: z.enum(['ACTIVE', 'SUBMITTED']),
  totalSeconds: z.number(),
  // Null while the session is ACTIVE; set once submitted. Nullish rather than required
  // so an older backend that predates the field still parses — a stricter schema would
  // break the whole player over a value only the end screen needs.
  score: z.number().nullish(),
  questions: z.array(sessionQuestionPlaySchema),
});
export type SessionPlay = z.infer<typeof sessionPlaySchema>;

export const propositionCorrectionSchema = z.object({
  id: z.string().uuid(),
  letter: z.string(),
  text: z.string(),
  isTrue: z.boolean(),
  explanationHtml: z.string().nullable(),
  explanationImages: z.array(z.string()),
  position: z.number(),
});

export const correctionSchema = z.object({
  questionId: z.string().uuid(),
  state: questionStateSchema,
  isCorrect: z.boolean().nullable(),
  selectedPropositionIds: z.array(z.string().uuid()),
  propositions: z.array(propositionCorrectionSchema),
});
export type Correction = z.infer<typeof correctionSchema>;

// ---------------------------------------------------------------------
// Stats
// ---------------------------------------------------------------------

export const sessionStatsSchema = z.object({
  id: z.string().uuid(),
  title: z.string(),
  sessionType: sessionTypeSchema,
  status: z.enum(['ACTIVE', 'SUBMITTED']),
  startedAt: z.string(),
  totalSeconds: z.number(),
  avgSecondsPerQuestion: z.number(),
  totalQuestions: z.number(),
  juste: z.number(),
  fausse: z.number(),
  consulte: z.number(),
  precisionPercent: z.number(),
});
export type SessionStats = z.infer<typeof sessionStatsSchema>;

export const overviewSchema = z.object({
  bank: z.object({
    questions: z.number(),
    sourceExams: z.number(),
    mindmaps: z.number(),
  }),
  lastSession: sessionStatsSchema.nullable(),
  activeSubscriptions: z.array(activeSubscriptionSchema),
});
export type Overview = z.infer<typeof overviewSchema>;

export const dailyStatsSchema = z.object({
  date: z.string(),
  juste: z.number(),
  fausse: z.number(),
  consultees: z.number(),
});
export type DailyStats = z.infer<typeof dailyStatsSchema>;

// ---------------------------------------------------------------------
// Accès / abonnement
// ---------------------------------------------------------------------

export const publicPackSchema = z.object({
  id: z.string().uuid(),
  studyYear: z.number().nullable(),
  name: z.string(),
  academicYear: z.string(),
  priceDa: z.number(),
  expiresAt: z.string(),
});
export type PublicPack = z.infer<typeof publicPackSchema>;

export const redeemResponseSchema = z.object({
  subscriptionId: z.string().uuid(),
  packName: z.string(),
  startsAt: z.string(),
  endsAt: z.string(),
});
export type RedeemResponse = z.infer<typeof redeemResponseSchema>;

// ---------------------------------------------------------------------
// Statistiques (par cours)
// ---------------------------------------------------------------------

export const courseStatsSchema = z.object({
  courseId: z.string().uuid(),
  courseName: z.string(),
  totalQuestions: z.number(),
  juste: z.number(),
  fausse: z.number(),
  consulte: z.number(),
  totalSeconds: z.number(),
  avgSecondsPerQuestion: z.number(),
  precisionPercent: z.number(),
});
export type CourseStats = z.infer<typeof courseStatsSchema>;

/**
 * Lifetime coverage of one course — everything counted per distinct question across all
 * of the student's sessions, not per attempt. `precisionPercent` is
 * correctQuestions/answeredQuestions, so it answers "how much of this course do I know",
 * which is a different question from the per-session score in `courseStatsSchema`.
 */
export const courseCoverageSchema = z.object({
  courseId: z.string().uuid(),
  courseName: z.string(),
  totalQuestions: z.number(),
  seenQuestions: z.number(),
  answeredQuestions: z.number(),
  correctQuestions: z.number(),
  neverSeenQuestions: z.number(),
  precisionPercent: z.number(),
});
export type CourseCoverage = z.infer<typeof courseCoverageSchema>;

export const moduleCoverageSchema = z.object({
  moduleId: z.string().uuid(),
  moduleName: z.string(),
  totalQuestions: z.number(),
  seenQuestions: z.number(),
  courses: z.array(courseCoverageSchema),
});
export type ModuleCoverage = z.infer<typeof moduleCoverageSchema>;

// ---------------------------------------------------------------------
// MindMaps
// ---------------------------------------------------------------------

export const mindmapSchema = z.object({
  id: z.string().uuid(),
  courseId: z.string().uuid(),
  title: z.string(),
  imageUrl: z.string(),
  published: z.boolean(),
  createdAt: z.string(),
  updatedAt: z.string(),
});
export type Mindmap = z.infer<typeof mindmapSchema>;

// ---------------------------------------------------------------------
// Notes
// ---------------------------------------------------------------------

export const noteSchema = z.object({
  id: z.string().uuid(),
  title: z.string(),
  contentHtml: z.string(),
  questionId: z.string().uuid().nullable(),
  courseId: z.string().uuid().nullable(),
  createdAt: z.string(),
  updatedAt: z.string(),
});
export type Note = z.infer<typeof noteSchema>;

// ---------------------------------------------------------------------
// Signalements
// ---------------------------------------------------------------------

export const signalStatusSchema = z.enum(['OPEN', 'RESOLVED', 'REJECTED']);
export type SignalStatus = z.infer<typeof signalStatusSchema>;

export const signalSchema = z.object({
  id: z.string().uuid(),
  questionId: z.string().uuid(),
  questionStatement: z.string(),
  message: z.string(),
  status: signalStatusSchema,
  adminReply: z.string().nullable(),
  createdAt: z.string(),
  updatedAt: z.string(),
});
export type Signal = z.infer<typeof signalSchema>;

// ---------------------------------------------------------------------
// Notifications
// ---------------------------------------------------------------------

export const notificationKindSchema = z.enum(['UPDATE', 'QUESTIONS', 'INFO']);
export type NotificationKind = z.infer<typeof notificationKindSchema>;

export const notificationSchema = z.object({
  id: z.string().uuid(),
  kind: notificationKindSchema,
  title: z.string(),
  body: z.string(),
  createdAt: z.string(),
  read: z.boolean(),
});
export type Notification = z.infer<typeof notificationSchema>;

// ---------------------------------------------------------------------
// Admin console
// ---------------------------------------------------------------------

export const difficultySchema = z.enum(['EASY', 'MEDIUM', 'HARD']);
export type Difficulty = z.infer<typeof difficultySchema>;

export const examTypeSchema = z.enum(['ENTRAINEMENT', 'EXAMEN']);
export type ExamType = z.infer<typeof examTypeSchema>;

export const userStatusSchema = z.enum(['ACTIVE', 'DISABLED']);
export type UserStatus = z.infer<typeof userStatusSchema>;

export const roleSchema = z.enum(['ADMIN', 'TEACHER', 'STUDENT']);
export type Role = z.infer<typeof roleSchema>;

// Écoles & modules & cours (admin views)
export const adminSchoolSchema = z.object({
  id: z.string().uuid(),
  name: z.string(),
  slug: z.string(),
});
export type AdminSchool = z.infer<typeof adminSchoolSchema>;

export const adminModuleSchema = z.object({
  id: z.string().uuid(),
  studyYear: z.number(),
  name: z.string(),
  position: z.number(),
  published: z.boolean(),
});
export type AdminModule = z.infer<typeof adminModuleSchema>;

export const adminCourseSchema = z.object({
  id: z.string().uuid(),
  moduleId: z.string().uuid(),
  name: z.string(),
  position: z.number(),
  published: z.boolean(),
  freePreview: z.boolean(),
});
export type AdminCourse = z.infer<typeof adminCourseSchema>;

export const adminSourceExamSchema = z.object({
  id: z.string().uuid(),
  label: z.string(),
  year: z.number(),
  examType: examTypeSchema,
});
export type AdminSourceExam = z.infer<typeof adminSourceExamSchema>;

export const adminMindmapSchema = z.object({
  id: z.string().uuid(),
  courseId: z.string().uuid(),
  title: z.string(),
  imageUrl: z.string(),
  published: z.boolean(),
  createdAt: z.string(),
  updatedAt: z.string(),
});
export type AdminMindmap = z.infer<typeof adminMindmapSchema>;

export const adminPackSchema = z.object({
  id: z.string().uuid(),
  studyYear: z.number().nullable(),
  name: z.string(),
  academicYear: z.string(),
  priceDa: z.number(),
  active: z.boolean(),
  expiresAt: z.string(),
});
export type AdminPack = z.infer<typeof adminPackSchema>;

// Codes
export const codeStatusSchema = z.enum(['ACTIVE', 'EXHAUSTED', 'REVOKED', 'EXPIRED']);
export type CodeStatus = z.infer<typeof codeStatusSchema>;

export const adminCodeSchema = z.object({
  id: z.string().uuid(),
  packId: z.string().uuid(),
  packName: z.string(),
  maxUses: z.number(),
  usedCount: z.number(),
  revoked: z.boolean(),
  status: codeStatusSchema,
  createdAt: z.string(),
});
export type AdminCode = z.infer<typeof adminCodeSchema>;

export const generateCodesResponseSchema = z.object({
  packId: z.string().uuid(),
  count: z.number(),
  codes: z.array(z.string()),
  csvToken: z.string(),
});
export type GenerateCodesResponse = z.infer<typeof generateCodesResponseSchema>;

// Questions (admin, full view with truth + explanations)
export const propositionAdminSchema = z.object({
  id: z.string().uuid(),
  letter: z.string(),
  text: z.string(),
  isTrue: z.boolean(),
  explanationHtml: z.string().nullable(),
  explanationImages: z.array(z.string()).nullable(),
  position: z.number(),
});
export type PropositionAdmin = z.infer<typeof propositionAdminSchema>;

export const questionAdminSchema = z.object({
  id: z.string().uuid(),
  courseId: z.string().uuid(),
  statement: z.string(),
  statementImages: z.array(z.string()).nullable(),
  sourceExamId: z.string().uuid().nullable(),
  difficulty: difficultySchema.nullable(),
  published: z.boolean(),
  createdAt: z.string(),
  updatedAt: z.string(),
  propositions: z.array(propositionAdminSchema),
});
export type QuestionAdmin = z.infer<typeof questionAdminSchema>;

export const importResultSchema = z.object({
  imported: z.number(),
  questionIds: z.array(z.string().uuid()),
});
export const importRowErrorSchema = z.object({
  row: z.number(),
  field: z.string(),
  message: z.string(),
});
export type ImportRowError = z.infer<typeof importRowErrorSchema>;

/** What one row of an import would resolve to — the dry run's readable output. */
export const importRowPreviewSchema = z.object({
  row: z.number(),
  statement: z.string(),
  courseId: z.string().uuid(),
  courseName: z.string(),
  moduleName: z.string(),
  studyYear: z.number(),
  sourceExamId: z.string().uuid().nullable(),
  sourceExamLabel: z.string().nullable(),
  difficulty: z.enum(['EASY', 'MEDIUM', 'HARD']).nullable(),
  published: z.boolean(),
  propositionCount: z.number(),
});
export type ImportRowPreview = z.infer<typeof importRowPreviewSchema>;

export const importDryRunSchema = z.object({
  rowsSubmitted: z.number(),
  wouldImport: z.number(),
  resolved: z.array(importRowPreviewSchema),
  errors: z.array(importRowErrorSchema),
});
export type ImportDryRun = z.infer<typeof importDryRunSchema>;

// Overview
export const registrationSchema = z.object({
  id: z.string().uuid(),
  username: z.string(),
  email: z.string(),
  fullName: z.string(),
  schoolName: z.string().nullable(),
  studyYear: z.number().nullable(),
  createdAt: z.string(),
});
export const schoolBreakdownSchema = z.object({
  // Null for the "no école recorded" bucket.
  schoolId: z.string().uuid().nullable(),
  schoolName: z.string().nullable(),
  students: z.number(),
});
export type SchoolBreakdown = z.infer<typeof schoolBreakdownSchema>;

export const adminOverviewSchema = z.object({
  students: z.number(),
  questions: z.number(),
  sessionsToday: z.number(),
  activeSubscriptions: z.number(),
  openSignals: z.number(),
  latestRegistrations: z.array(registrationSchema),
  // Optional, not required: content went national in the same release, and the SPA and
  // API deploy independently — an API without the field yet must not blank the whole
  // screen. (`.default([])` would be nicer but apiGet's z.ZodType<T> signature infers
  // the input type, which makes the field optional at the call site anyway.)
  studentsBySchool: z.array(schoolBreakdownSchema).optional(),
});
export type AdminOverview = z.infer<typeof adminOverviewSchema>;

// Abonnés (users)
export const adminUserSchema = z.object({
  id: z.string().uuid(),
  username: z.string(),
  email: z.string(),
  fullName: z.string(),
  role: roleSchema,
  status: userStatusSchema,
  schoolName: z.string().nullable(),
  studyYear: z.number().nullable(),
  activeSubscriptions: z.number(),
  createdAt: z.string(),
  /**
   * The account-sharing signal, over the last 7 days. `logins7d` counts distinct
   * refresh-token families (one per login), not tokens — a family rotates every 15
   * minutes, so counting tokens would report one studious afternoon as ~30 logins.
   * Many logins from many addresses is the pattern worth looking at.
   */
  logins7d: z.number(),
  distinctIps7d: z.number(),
  activeSessions: z.number(),
  lastDeviceLabel: z.string().nullable(),
  lastSeenAt: z.string().nullable(),
});
export type AdminUser = z.infer<typeof adminUserSchema>;

export const subscriptionAuditSchema = z.object({
  id: z.string().uuid(),
  userEmail: z.string(),
  username: z.string(),
  packId: z.string().uuid(),
  packName: z.string(),
  startsAt: z.string(),
  endsAt: z.string(),
  activationCodeId: z.string().uuid(),
});
export type SubscriptionAudit = z.infer<typeof subscriptionAuditSchema>;

// Signalements (admin)
export const signalAdminSchema = z.object({
  id: z.string().uuid(),
  questionId: z.string().uuid(),
  questionStatement: z.string(),
  userEmail: z.string(),
  message: z.string(),
  status: signalStatusSchema,
  adminReply: z.string().nullable(),
  createdAt: z.string(),
  updatedAt: z.string(),
});
export type SignalAdmin = z.infer<typeof signalAdminSchema>;

// Notifications (admin history)
export const notificationAdminSchema = z.object({
  id: z.string().uuid(),
  kind: notificationKindSchema,
  title: z.string(),
  body: z.string(),
  schoolId: z.string().uuid().nullable(),
  schoolName: z.string().nullable(),
  studyYear: z.number().nullable(),
  createdAt: z.string(),
});
export type NotificationAdmin = z.infer<typeof notificationAdminSchema>;

// Support inbox
export const supportMessageSchema = z.object({
  id: z.string().uuid(),
  userEmail: z.string(),
  username: z.string(),
  fullName: z.string(),
  subject: z.string(),
  body: z.string(),
  createdAt: z.string(),
});
export type SupportMessage = z.infer<typeof supportMessageSchema>;

// Public marketing stats
export const publicStatsSchema = z.object({
  questions: z.number(),
  examens: z.number(),
  mindmaps: z.number(),
});
export type PublicStats = z.infer<typeof publicStatsSchema>;
