import type {
  UrgencyLevel,
  NeedsCategory,
  GPSCoordinate,
  PhotoAttachment,
  SourceDataRef,
  SyncStatus,
} from './common';

/**
 * TriageCard is the central data record produced by each encounter.
 * It captures location, affected population, urgency, needs, and
 * AI-generated assessments from speech and vision analysis.
 */
export interface TriageCard {
  /** UUID v4 unique identifier */
  id: string;
  /** Device that created this card */
  deviceId: string;
  /** Parent conversation session */
  sessionId: string;
  /** GPS location, null if unknown */
  gpsCoordinates: GPSCoordinate | null;
  /** Creation timestamp (Unix epoch ms) */
  timestamp: number;
  /** Last modification timestamp (Unix epoch ms) */
  updatedAt: number;
  /** Number of affected people, null if unknown */
  peopleCount: number | null;
  /** Urgency classification */
  urgencyLevel: UrgencyLevel;
  /** One or more needs categories */
  needsCategories: NeedsCategory[];
  /** Detected language of the survivor */
  detectedLanguage: string;
  /** AI-generated summary of the situation */
  assessmentSummary: string;
  /** References to source interactions */
  sourceDataRefs: SourceDataRef[];
  /** Attached photos with AI assessments */
  photos: PhotoAttachment[];
  /** Synchronization status */
  syncStatus: SyncStatus;
}

/**
 * Filter criteria for querying triage cards.
 */
export interface TriageCardFilter {
  urgencyLevels?: UrgencyLevel[];
  needsCategories?: NeedsCategory[];
  languages?: string[];
  dateRange?: { start: number; end: number };
}
