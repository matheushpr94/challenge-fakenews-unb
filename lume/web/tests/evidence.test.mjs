import test from 'node:test';
import assert from 'node:assert/strict';
import { criteria, evaluateEvidence } from '../public/evidence.mjs';
import { articles } from '../public/scenarios.mjs';

test('demo distinguishes incomplete evidence, unavailable evidence and supported evidence', () => {
  assert.deepEqual(articles.map(a => evaluateEvidence(a.assessment).score), [50, null, 90]);
  assert.deepEqual(articles.map(a => evaluateEvidence(a.assessment).tone), ['partial', 'unknown', 'strong']);
});

test('missing evidence is never presented as zero percent', () => {
  for (const assessment of [undefined, {}, { assessable: false }, { assessable: true, claim: '' }, { assessable: true, claim: 7 }]) {
    assert.equal(evaluateEvidence(assessment).score, null);
  }
});

test('an incomplete checklist does not produce a reassuring score', () => {
  for (const { id } of criteria) {
    const assessment = structuredClone(articles[2].assessment);
    delete assessment.criteria[id];
    assert.equal(evaluateEvidence(assessment).score, null);
  }
});

test('invalid ratings or missing explanations fail closed', () => {
  for (const value of [-1, 0.25, 2, NaN, Infinity, '1', null]) {
    const assessment = structuredClone(articles[2].assessment);
    assessment.criteria.support.level = value;
    assert.equal(evaluateEvidence(assessment).score, null);
  }
  const assessment = structuredClone(articles[2].assessment);
  assessment.criteria.support.reason = ' ';
  assert.equal(evaluateEvidence(assessment).score, null);
});

test('traceability and actual support are required even when other criteria score highly', () => {
  for (const id of ['original', 'support']) {
    const assessment = structuredClone(articles[2].assessment);
    assessment.criteria[id].level = 0;
    assert.equal(evaluateEvidence(assessment).score, null);
  }
});

test('all 81 valid checklist combinations stay within range and improving evidence never lowers the score', () => {
  assert.equal(criteria.reduce((sum, c) => sum + c.weight, 0), 100);
  for (let combination = 0; combination < 81; combination++) {
    const assessment = structuredClone(articles[2].assessment);
    let digits = combination;
    for (const { id } of criteria) {
      assessment.criteria[id].level = [0, 0.5, 1][digits % 3];
      digits = Math.floor(digits / 3);
    }
    const { score } = evaluateEvidence(assessment);
    if (score === null) continue;
    assert.ok(score >= 0 && score <= 100);
    for (const { id } of criteria) {
      const improved = structuredClone(assessment);
      improved.criteria[id].level = 1;
      assert.ok(evaluateEvidence(improved).score >= score);
    }
  }
});
