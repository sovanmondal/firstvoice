import type {
  GPSCoordinate,
  ConfidenceLevel,
  DamageAssessment,
  InjuryAssessment,
} from './common';

/**
 * A time-bounded interaction between a responder and one or more survivors.
 * Contains an ordered sequence of speech turns, translations, vision assessments,
 * quick phrases, and notes. Produces a TriageCard when closed.
 */
export interface ConversationSession {
  /** UUID v4 unique identifier */
  id: string;
  /** Device that created this session */
  deviceId: string;
  /** Session start time (Unix epoch ms) */
  startedAt: number;
  /** Session end time, null if still active */
  endedAt: number | null;
  /** GPS location at session start */
  gpsCoordinates: GPSCoordinate | null;
  /** Responder's configured language */
  responderLanguage: string;
  /** Survivor's detected language, null until detected */
  survivorLanguage: string | null;
  /** Ordered log of all interactions */
  interactions: Interaction[];
  /** Linked triage card ID, set when session closes */
  triageCardId: string | null;
  /** Session lifecycle status */
  status: 'active' | 'closed' | 'timed_out';
}

/**
 * Union type for all interaction types within a session.
 */
export type Interaction =
  | SpeechTurnInteraction
  | VisionAssessmentInteraction
  | QuickPhraseInteraction
  | NoteInteraction;

export interface SpeechTurnInteraction {
  type: 'speech_turn';
  id: string;
  timestamp: number;
  speaker: 'responder' | 'survivor';
  originalText: string;
  originalLanguage: string;
  translatedText: string;
  translatedLanguage: string;
  confidence: ConfidenceLevel;
}

export interface VisionAssessmentInteraction {
  type: 'vision_assessment';
  id: string;
  timestamp: number;
  photoId: string;
  assessment: DamageAssessment | InjuryAssessment;
}

export interface QuickPhraseInteraction {
  type: 'quick_phrase';
  id: string;
  timestamp: number;
  phraseId: string;
  sourceText: string;
  translatedText: string;
  targetLanguage: string;
}

export interface NoteInteraction {
  type: 'note';
  id: string;
  timestamp: number;
  text: string;
}

export interface SessionFilter {
  status?: ('active' | 'closed' | 'timed_out')[];
  dateRange?: { start: number; end: number };
}
