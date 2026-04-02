import { AnsiUp } from 'ansi_up';

const ansiUp = new AnsiUp();
ansiUp.escape_html = true;

/**
 * Converts terminal output (with ANSI color codes) to HTML for use with [innerHTML].
 */
export function ansiStringToHtml(text: string): string {
  return ansiUp.ansi_to_html(text);
}
