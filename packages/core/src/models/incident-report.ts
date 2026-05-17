import type { UrgencyLevel, NeedsCategory } from './common';

/**
 * A standardized incident report aggregating one or more triage cards
 * for sharing with coordination centers.
 */
export interface IncidentReport {
  /** UUID v4 unique identifier */
  id: string;
  /** Generation timestamp (Unix epoch ms) */
  generatedAt: number;
  /** IDs of triage cards included in this report */
  triageCardIds: string[];
  /** AI-generated incident summary */
  summary: string;
  /** Location details description */
  locationDetails: string;
  /** Total affected population count */
  affectedPopulation: number;
  /** Count of cards per urgency level */
  urgencyBreakdown: Record<UrgencyLevel, number>;
  /** Count of cards per needs category */
  needsBreakdown: Record<NeedsCategory, number>;
  /** Chronological timeline of events */
  timeline: Array<{ timestamp: number; description: string }>;
  /** Detailed assessment text */
  assessmentDetails: string;
  /** Output format */
  format: 'text' | 'pdf';
}
