import { describe, expect, it } from 'vitest';
import { compare, type SortMode } from './suiviSort';
import type { CourseCoverage } from '../../lib/schemas';

/**
 * The sort rules are the feature: "where should I revise" is the whole reason the page
 * exists, and both orderings have a deliberate tie-break that is easy to regress into
 * something plausible-looking but wrong.
 */
function course(name: string, over: Partial<CourseCoverage> = {}): CourseCoverage {
  return {
    courseId: name,
    courseName: name,
    totalQuestions: 10,
    seenQuestions: 0,
    answeredQuestions: 0,
    correctQuestions: 0,
    neverSeenQuestions: 10,
    precisionPercent: 0,
    ...over,
  };
}

const names = (mode: SortMode, list: CourseCoverage[]) =>
  [...list].sort((a, b) => compare(mode, a, b)).map((c) => c.courseName);

describe('weakest first', () => {
  it('orders by précision ascending', () => {
    const list = [
      course('bon', { answeredQuestions: 10, correctQuestions: 9, precisionPercent: 90 }),
      course('faible', { answeredQuestions: 10, correctQuestions: 2, precisionPercent: 20 }),
      course('moyen', { answeredQuestions: 10, correctQuestions: 5, precisionPercent: 50 }),
    ];
    expect(names('weakest', list)).toEqual(['faible', 'moyen', 'bon']);
  });

  it('puts untouched courses last, not first', () => {
    // A course with nothing answered reports 0%. Sorting on that number alone would rank
    // it as the single weakest course in the year, which is exactly backwards: there is
    // no evidence of weakness, only of absence.
    const list = [
      course('jamais-touché'),
      course('faible', { answeredQuestions: 10, correctQuestions: 2, precisionPercent: 20 }),
    ];
    expect(names('weakest', list)).toEqual(['faible', 'jamais-touché']);
  });
});

describe('least covered first', () => {
  it('compares the fraction seen, not the raw count', () => {
    // 2/4 is worse coverage than 30/100 despite having seen fewer questions in total;
    // ranking on the absolute number would bury every large course.
    const list = [
      course('grand', { totalQuestions: 100, seenQuestions: 30, neverSeenQuestions: 70 }),
      course('petit', { totalQuestions: 4, seenQuestions: 2, neverSeenQuestions: 2 }),
    ];
    expect(names('leastCovered', list)).toEqual(['grand', 'petit']);
  });

  it('puts a course with no questions last rather than at 0% coverage', () => {
    const list = [
      course('vide', { totalQuestions: 0, seenQuestions: 0, neverSeenQuestions: 0 }),
      course('entamé', { seenQuestions: 5, neverSeenQuestions: 5 }),
    ];
    expect(names('leastCovered', list)).toEqual(['entamé', 'vide']);
  });
});
