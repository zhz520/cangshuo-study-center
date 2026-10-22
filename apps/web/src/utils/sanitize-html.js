import DOMPurify from 'dompurify';

// Sanitize the final rendered HTML, including Markdown, KaTeX and rich-text previews.
export function sanitizeHtml(value) {
  return DOMPurify.sanitize(String(value ?? ''));
}
