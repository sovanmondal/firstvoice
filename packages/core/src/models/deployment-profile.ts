/**
 * A pre-configured bundle for deploying FirstVoice to a specific
 * disaster response region. Contains language pins, map bounds,
 * team ID, and custom quick phrases.
 */
export interface DeploymentProfile {
  /** Unique profile identifier */
  id: string;
  /** Human-readable profile name */
  name: string;
  /** ISO 639-1 language codes for quick access, max 10 */
  languagePins: string[];
  /** Geographic bounding box for offline map tile pre-download */
  mapBoundingBox: {
    north: number;
    south: number;
    east: number;
    west: number;
  };
  /** Team identifier string */
  teamIdentifier: string;
  /** Custom quick phrases specific to this deployment */
  customQuickPhrases: Array<{
    sourceText: string;
    translations: Record<string, string>;
    category: 'medical' | 'safety' | 'logistics' | 'identification';
  }>;
}
