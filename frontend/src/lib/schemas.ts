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
});

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
});
export type ApiError = z.infer<typeof apiErrorSchema>;

// ---------------------------------------------------------------------
// Catalogue
// ---------------------------------------------------------------------

export const moduleSchema = z.object({
  id: z.string().uuid(),
  schoolId: z.string().uuid(),
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
  schoolId: z.string().uuid(),
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
  schoolId: z.string().uuid(),
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
