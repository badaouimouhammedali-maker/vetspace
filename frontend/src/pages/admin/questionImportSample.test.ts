import { describe, expect, it } from 'vitest';
import { QUESTION_IMPORT_SAMPLE, importRowSchema } from './questionImportSample';

/**
 * "Télécharger un exemple" is only useful if the example still imports. A sample that has
 * drifted teaches the wrong format and the author blames their own file, so its validity
 * is asserted rather than assumed.
 */
describe('question import sample', () => {
  it('every row satisfies the import row schema', () => {
    for (const row of QUESTION_IMPORT_SAMPLE) {
      const result = importRowSchema.safeParse(row);
      expect(result.success, `row "${row.statement}": ${JSON.stringify(result)}`).toBe(true);
    }
  });

  it('demonstrates both identity forms, which is the point of the sample', () => {
    expect(QUESTION_IMPORT_SAMPLE.some((r) => 'courseName' in r)).toBe(true);
    expect(QUESTION_IMPORT_SAMPLE.some((r) => 'courseId' in r)).toBe(true);
    expect(QUESTION_IMPORT_SAMPLE.some((r) => 'sourceExamLabel' in r)).toBe(true);
  });

  it('never mixes the two identity forms in one row', () => {
    // A sample doing this would 400 on the very first import an admin tried.
    for (const row of QUESTION_IMPORT_SAMPLE) {
      const named = 'courseName' in row || 'moduleName' in row || 'studyYear' in row;
      expect(named && 'courseId' in row).toBe(false);
    }
  });

  it('serialises to parseable JSON', () => {
    expect(() => JSON.parse(JSON.stringify(QUESTION_IMPORT_SAMPLE, null, 2))).not.toThrow();
  });
});
