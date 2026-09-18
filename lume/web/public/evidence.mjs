// Product exploration only: this rubric has not been calibrated against real news.
export const criteria = Object.freeze([
  { id: 'support', label: 'A evidência sustenta a afirmação?', weight: 40 },
  { id: 'original', label: 'A fonte original é rastreável?', weight: 20 },
  { id: 'independent', label: 'Há confirmação independente?', weight: 20 },
  { id: 'context', label: 'Data e contexto estão claros?', weight: 20 },
]);

export function evaluateEvidence(assessment) {
  const unavailable = { score: null, tone: 'unknown', label: 'Sem base para avaliar' };
  if (!assessment || assessment.assessable !== true) return unavailable;
  if (typeof assessment.claim !== 'string' || !assessment.claim.trim()) return unavailable;
  if (!criteria.every(({ id }) => {
    const item = assessment.criteria?.[id];
    return item && [0, 0.5, 1].includes(item.level) && typeof item.reason === 'string' && item.reason.trim();
  })) return unavailable;
  // Missing information never becomes a numerical judgment of truth or falsity.
  if (!assessment.criteria.original.level || !assessment.criteria.support.level) return unavailable;
  const score = criteria.reduce((total, { id, weight }) => total + assessment.criteria[id].level * weight, 0);
  return {
    score,
    tone: score >= 80 ? 'strong' : score >= 50 ? 'partial' : 'limited',
    label: score >= 80 ? 'Evidências consistentes' : score >= 50 ? 'Há lacunas importantes' : 'Base de evidências limitada',
  };
}
