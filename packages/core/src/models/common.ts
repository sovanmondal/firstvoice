// Common types shared across all models

export type UrgencyLevel = 'CRITICAL' | 'HIGH' | 'MEDIUM' | 'LOW';

export type NeedsCategory =
  | 'Medical'
  | 'Extraction'
  | 'Shelter'
  | 'Water/Food'
  | 'Family Reunification';

export type ConfidenceLevel = 'HIGH' | 'MEDIUM' | 'LOW';

export type DamageSeverity = 'NONE' | 'MINOR' | 'MODERATE' | 'SEVERE' | 'CATASTROPHIC';

export type InjurySeverity = 'MINOR' | 'MODERATE' | 'SEVERE' | 'LIFE_THREATENING';

export type HazardSeverity = 'NONE' | 'LOW' | 'MODERATE' | 'HIGH' | 'EXTREME';

export type UniversalIcon = 'medical' | 'water' | 'shelter' | 'danger' | 'safe' | 'yes' | 'no';

export interface GPSCoordinate {
  latitude: number;
  longitude: number;
  accuracy: number; // meters
  timestamp: number; // Unix epoch ms
}

export interface PhotoAttachment {
  id: string;
  filePath: string;
  thumbnailPath: string;
  assessmentText: string;
  capturedAt: number; // Unix epoch ms
}

export interface SourceDataRef {
  type: 'speech_turn' | 'vision_assessment' | 'quick_phrase' | 'note';
  refId: string;
  timestamp: number; // Unix epoch ms
}

export interface SyncStatus {
  meshSynced: boolean;
  meshSyncedAt: number | null;
  cloudSynced: boolean;
  cloudSyncedAt: number | null;
}

export interface AudioClip {
  data: ArrayBuffer;
  durationSeconds: number;
  sampleRate: number; // 16000 for Gemma 4
}

export interface TranscriptionResult {
  text: string;
  detectedLanguage: string;
  confidence: ConfidenceLevel;
  segmentCount: number;
}

export interface TranslationResult {
  translatedText: string;
  sourceLang: string;
  targetLang: string;
}

export interface DamageAssessment {
  structuralDamage: { severity: DamageSeverity; description: string };
  hazards: Array<{ type: string; severity: HazardSeverity; description: string }>;
  extractedText: string | null;
  summary: string;
}

export interface InjuryAssessment {
  injuries: Array<{
    type: 'wound' | 'burn' | 'bleeding' | 'fracture_indication' | 'other';
    bodyRegion: string;
    severity: InjurySeverity;
    description: string;
  }>;
  disclaimer: string;
  summary: string;
}

export interface SceneAssessment {
  damage: DamageAssessment;
  injuries: InjuryAssessment;
}

/**
 * Generate a UUID v4 string.
 * Uses crypto.randomUUID when available, falls back to manual generation.
 */
export function generateId(): string {
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
    return crypto.randomUUID();
  }
  // Fallback for environments without crypto.randomUUID
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (c) => {
    const r = (Math.random() * 16) | 0;
    const v = c === 'x' ? r : (r & 0x3) | 0x8;
    return v.toString(16);
  });
}
