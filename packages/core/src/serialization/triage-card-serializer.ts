import type { TriageCard } from '../models/triage-card';
import type {
  UrgencyLevel,
  NeedsCategory,
  GPSCoordinate,
  PhotoAttachment,
  SourceDataRef,
  SyncStatus,
} from '../models/common';

const VALID_URGENCY_LEVELS: UrgencyLevel[] = ['CRITICAL', 'HIGH', 'MEDIUM', 'LOW'];
const VALID_NEEDS_CATEGORIES: NeedsCategory[] = [
  'Medical',
  'Extraction',
  'Shelter',
  'Water/Food',
  'Family Reunification',
];
const VALID_SOURCE_DATA_TYPES: SourceDataRef['type'][] = [
  'speech_turn',
  'vision_assessment',
  'quick_phrase',
  'note',
];

/**
 * Serialize a TriageCard to a JSON string.
 * Produces a compact JSON representation suitable for storage and sync.
 */
export function serializeTriageCard(card: TriageCard): string {
  return JSON.stringify(card);
}

/**
 * Pretty-print a TriageCard as formatted JSON with 2-space indentation.
 * Useful for debugging and manual inspection.
 */
export function prettyPrintTriageCard(card: TriageCard): string {
  return JSON.stringify(card, null, 2);
}

/**
 * Parse a JSON string into a validated TriageCard object.
 * Throws a descriptive error if the input is malformed or invalid.
 */
export function parseTriageCard(json: string): TriageCard {
  let raw: unknown;
  try {
    raw = JSON.parse(json);
  } catch (e) {
    throw new Error(
      `Failed to parse TriageCard JSON: invalid JSON syntax - ${(e as Error).message}`
    );
  }

  if (typeof raw !== 'object' || raw === null || Array.isArray(raw)) {
    throw new Error('Failed to parse TriageCard JSON: expected a JSON object at root');
  }

  const obj = raw as Record<string, unknown>;

  // Required string fields
  const requiredStrings = ['id', 'deviceId', 'sessionId', 'detectedLanguage', 'assessmentSummary'];
  for (const field of requiredStrings) {
    if (typeof obj[field] !== 'string' || (obj[field] as string).length === 0) {
      throw new Error(
        `Failed to parse TriageCard JSON: field "${field}" must be a non-empty string`
      );
    }
  }

  // Required number fields
  const requiredNumbers = ['timestamp', 'updatedAt'];
  for (const field of requiredNumbers) {
    if (typeof obj[field] !== 'number' || !Number.isFinite(obj[field] as number)) {
      throw new Error(
        `Failed to parse TriageCard JSON: field "${field}" must be a finite number`
      );
    }
  }

  // urgencyLevel
  if (!VALID_URGENCY_LEVELS.includes(obj['urgencyLevel'] as UrgencyLevel)) {
    throw new Error(
      `Failed to parse TriageCard JSON: field "urgencyLevel" must be one of ${VALID_URGENCY_LEVELS.join(', ')}`
    );
  }

  // needsCategories
  if (!Array.isArray(obj['needsCategories']) || obj['needsCategories'].length === 0) {
    throw new Error(
      'Failed to parse TriageCard JSON: field "needsCategories" must be a non-empty array'
    );
  }
  for (const cat of obj['needsCategories'] as unknown[]) {
    if (!VALID_NEEDS_CATEGORIES.includes(cat as NeedsCategory)) {
      throw new Error(
        `Failed to parse TriageCard JSON: invalid needs category "${cat}". Must be one of ${VALID_NEEDS_CATEGORIES.join(', ')}`
      );
    }
  }

  // peopleCount - nullable
  if (obj['peopleCount'] !== null && typeof obj['peopleCount'] !== 'number') {
    throw new Error(
      'Failed to parse TriageCard JSON: field "peopleCount" must be a number or null'
    );
  }

  // gpsCoordinates - nullable
  if (obj['gpsCoordinates'] !== null) {
    validateGPSCoordinate(obj['gpsCoordinates']);
  }

  // sourceDataRefs
  if (!Array.isArray(obj['sourceDataRefs'])) {
    throw new Error(
      'Failed to parse TriageCard JSON: field "sourceDataRefs" must be an array'
    );
  }
  for (const ref of obj['sourceDataRefs'] as unknown[]) {
    validateSourceDataRef(ref);
  }

  // photos
  if (!Array.isArray(obj['photos'])) {
    throw new Error('Failed to parse TriageCard JSON: field "photos" must be an array');
  }
  for (const photo of obj['photos'] as unknown[]) {
    validatePhotoAttachment(photo);
  }

  // syncStatus
  validateSyncStatus(obj['syncStatus']);

  return obj as unknown as TriageCard;
}

function validateGPSCoordinate(value: unknown): asserts value is GPSCoordinate {
  if (typeof value !== 'object' || value === null) {
    throw new Error(
      'Failed to parse TriageCard JSON: field "gpsCoordinates" must be an object or null'
    );
  }
  const gps = value as Record<string, unknown>;
  for (const field of ['latitude', 'longitude', 'accuracy', 'timestamp']) {
    if (typeof gps[field] !== 'number' || !Number.isFinite(gps[field] as number)) {
      throw new Error(
        `Failed to parse TriageCard JSON: gpsCoordinates.${field} must be a finite number`
      );
    }
  }
}

function validateSourceDataRef(value: unknown): asserts value is SourceDataRef {
  if (typeof value !== 'object' || value === null) {
    throw new Error(
      'Failed to parse TriageCard JSON: each sourceDataRef must be an object'
    );
  }
  const ref = value as Record<string, unknown>;
  if (!VALID_SOURCE_DATA_TYPES.includes(ref['type'] as SourceDataRef['type'])) {
    throw new Error(
      `Failed to parse TriageCard JSON: sourceDataRef.type must be one of ${VALID_SOURCE_DATA_TYPES.join(', ')}`
    );
  }
  if (typeof ref['refId'] !== 'string') {
    throw new Error(
      'Failed to parse TriageCard JSON: sourceDataRef.refId must be a string'
    );
  }
  if (typeof ref['timestamp'] !== 'number') {
    throw new Error(
      'Failed to parse TriageCard JSON: sourceDataRef.timestamp must be a number'
    );
  }
}

function validatePhotoAttachment(value: unknown): asserts value is PhotoAttachment {
  if (typeof value !== 'object' || value === null) {
    throw new Error(
      'Failed to parse TriageCard JSON: each photo must be an object'
    );
  }
  const photo = value as Record<string, unknown>;
  for (const field of ['id', 'filePath', 'thumbnailPath', 'assessmentText']) {
    if (typeof photo[field] !== 'string') {
      throw new Error(
        `Failed to parse TriageCard JSON: photo.${field} must be a string`
      );
    }
  }
  if (typeof photo['capturedAt'] !== 'number') {
    throw new Error(
      'Failed to parse TriageCard JSON: photo.capturedAt must be a number'
    );
  }
}

function validateSyncStatus(value: unknown): asserts value is SyncStatus {
  if (typeof value !== 'object' || value === null) {
    throw new Error(
      'Failed to parse TriageCard JSON: field "syncStatus" must be an object'
    );
  }
  const sync = value as Record<string, unknown>;
  if (typeof sync['meshSynced'] !== 'boolean') {
    throw new Error(
      'Failed to parse TriageCard JSON: syncStatus.meshSynced must be a boolean'
    );
  }
  if (typeof sync['cloudSynced'] !== 'boolean') {
    throw new Error(
      'Failed to parse TriageCard JSON: syncStatus.cloudSynced must be a boolean'
    );
  }
  // meshSyncedAt and cloudSyncedAt can be number or null
  if (sync['meshSyncedAt'] !== null && typeof sync['meshSyncedAt'] !== 'number') {
    throw new Error(
      'Failed to parse TriageCard JSON: syncStatus.meshSyncedAt must be a number or null'
    );
  }
  if (sync['cloudSyncedAt'] !== null && typeof sync['cloudSyncedAt'] !== 'number') {
    throw new Error(
      'Failed to parse TriageCard JSON: syncStatus.cloudSyncedAt must be a number or null'
    );
  }
}
