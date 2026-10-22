import { test } from 'node:test';
import assert from 'node:assert/strict';
import { JSDOM } from 'jsdom';
import katex from 'katex';

globalThis.window = new JSDOM('').window;
const { sanitizeHtml } = await import('../src/utils/sanitize-html.js');

test('untrusted rich text cannot execute handlers, scripts or javascript URLs', () => {
  const html = sanitizeHtml('<img src="x" onerror="stealToken()"><script>stealToken()</script><a href="javascript:stealToken()">open</a><iframe srcdoc="<script>stealToken()</script>"></iframe>');
  const doc = new JSDOM(html).window.document;
  assert.equal(doc.querySelector('script'), null);
  assert.equal(doc.querySelector('iframe'), null);
  assert.equal(doc.querySelector('img').hasAttribute('onerror'), false);
  assert.equal(doc.querySelector('a').hasAttribute('href'), false);
});

test('SVG and MathML input cannot smuggle script execution into the rendered content', () => {
  for (const input of ['<svg><g onload="stealToken()"></g><script>stealToken()</script></svg>', '<math><mi xlink:href="data:x,<script>stealToken()</script>">X</mi></math>']) {
    const doc = new JSDOM(sanitizeHtml(input)).window.document;
    assert.equal(doc.querySelector('script,[onload],[onerror]'), null);
    assert.ok(!doc.documentElement.innerHTML.includes('xlink:href="data:'));
  }
});

test('sanitizing final output preserves KaTeX, ordinary links and image content', () => {
  const equation = katex.renderToString('x^2 + \\frac{1}{2}', { throwOnError: false });
  const doc = new JSDOM(sanitizeHtml(equation + '<p><a href="https://example.com">资料</a><img src="/uploads/question.png"></p>')).window.document;
  assert.ok(doc.querySelector('.katex'));
  assert.ok(doc.querySelector('math'));
  assert.equal(doc.querySelector('a').getAttribute('href'), 'https://example.com');
  assert.equal(doc.querySelector('img').getAttribute('src'), '/uploads/question.png');
});
