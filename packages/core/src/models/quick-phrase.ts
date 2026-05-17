/**
 * A pre-translated emergency phrase for instant communication
 * without requiring AI inference.
 */
export interface QuickPhrase {
  /** Unique phrase identifier */
  id: string;
  /** Category for grouping */
  category: 'medical' | 'safety' | 'logistics' | 'identification';
  /** English canonical text */
  sourceText: string;
  /** Translations keyed by ISO 639-1 language code */
  translations: Record<string, string>;
  /** Whether this phrase is marked as a favorite */
  isFavorite: boolean;
}

export type PhraseCategory = QuickPhrase['category'];
