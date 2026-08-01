import { z } from 'zod';

/**
 * The file behind "Télécharger un exemple".
 *
 * <p>Written as data rather than a string blob so the shape can be checked by the same
 * schema the API accepts — see `questionImportSample.test.ts`. A sample that has silently
 * drifted out of date is worse than no sample: it teaches the wrong format and the author
 * blames their own file.
 *
 * <p>The three rows are deliberately different, because each teaches one thing:
 * row 0 the name form with full narrowing, row 1 the shortest form that works when a
 * course name is unique, row 2 the original UUID form which still works.
 */

/** Mirrors the server's `QuestionImportRow`. Exported so the test can assert against it. */
export const importRowSchema = z
  .object({
    courseId: z.string().uuid().optional(),
    courseName: z.string().min(1).optional(),
    moduleName: z.string().min(1).optional(),
    studyYear: z.number().int().min(1).max(10).optional(),
    statement: z.string().min(1),
    statementImages: z.array(z.string()).optional(),
    sourceExamId: z.string().uuid().optional(),
    sourceExamLabel: z.string().min(1).optional(),
    difficulty: z.enum(['EASY', 'MEDIUM', 'HARD']).optional(),
    published: z.boolean(),
    propositions: z
      .array(
        z.object({
          letter: z.enum(['A', 'B', 'C', 'D', 'E']),
          text: z.string().min(1),
          isTrue: z.boolean(),
          explanationHtml: z.string().optional(),
          explanationImages: z.array(z.string()).optional(),
        }),
      )
      .min(2)
      .max(5),
  })
  // The server rejects both forms of the same identity rather than picking one; the
  // sample must not demonstrate something that would 400.
  .refine((r) => !(r.courseId && (r.courseName ?? r.moduleName ?? r.studyYear)), {
    message: 'courseId and courseName/moduleName/studyYear are mutually exclusive',
  })
  .refine((r) => !(r.sourceExamId && r.sourceExamLabel), {
    message: 'sourceExamId and sourceExamLabel are mutually exclusive',
  })
  .refine((r) => r.propositions.some((p) => p.isTrue) && r.propositions.some((p) => !p.isTrue), {
    message: 'at least one true and one false proposition',
  });

export const QUESTION_IMPORT_SAMPLE = [
  {
    courseName: 'Parvovirose',
    moduleName: 'Maladies infectieuses',
    studyYear: 3,
    statement: 'Concernant la transmission du parvovirus canin :',
    sourceExamLabel: 'Session normale 2025',
    difficulty: 'MEDIUM',
    published: true,
    propositions: [
      {
        letter: 'A',
        text: 'La transmission est principalement féco-orale.',
        isTrue: true,
        explanationHtml: '<p>Le virus est excrété en grande quantité dans les fèces.</p>',
      },
      { letter: 'B', text: 'La transmission est exclusivement vectorielle.', isTrue: false },
      { letter: 'C', text: 'Le virus résiste plusieurs mois dans le milieu extérieur.', isTrue: true },
    ],
  },
  {
    // Le minimum : si le nom du cours est unique, module et année sont facultatifs.
    courseName: 'Leptospirose',
    statement: 'La leptospirose canine :',
    published: false,
    propositions: [
      { letter: 'A', text: 'Est une zoonose.', isTrue: true },
      { letter: 'B', text: "N'existe qu'en zone tropicale.", isTrue: false },
    ],
  },
  {
    // L'ancienne forme reste valable : remplacez cet UUID par celui de votre cours.
    courseId: '00000000-0000-0000-0000-000000000000',
    statement: 'Exemple avec identifiant de cours (remplacez le courseId) :',
    difficulty: 'EASY',
    published: false,
    propositions: [
      { letter: 'A', text: 'Proposition vraie.', isTrue: true },
      { letter: 'B', text: 'Proposition fausse.', isTrue: false },
    ],
  },
] as const;

/** Triggers a download of the sample without a round trip to the server. */
export function downloadImportSample(): void {
  const blob = new Blob([JSON.stringify(QUESTION_IMPORT_SAMPLE, null, 2)], {
    type: 'application/json',
  });
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = 'vetspace-import-exemple.json';
  document.body.appendChild(link);
  link.click();
  link.remove();
  // Without this the blob stays alive for the lifetime of the document.
  URL.revokeObjectURL(url);
}
