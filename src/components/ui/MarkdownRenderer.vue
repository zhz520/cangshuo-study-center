<script setup>
import { computed } from "vue";
import MarkdownIt from "markdown-it";
import taskLists from "markdown-it-task-lists";
import katex from "katex";
import "katex/dist/katex.min.css";

const props = defineProps({
  content: {
    type: String,
    default: "",
  },
});

const md = new MarkdownIt({
  html: true, // Allow HTML so KaTeX output passes through
  linkify: true,
  typographer: true,
  breaks: true,
});

// Fix: Prepend API Domain to /uploads/ images
const apiBaseUrl = import.meta.env.VITE_API_BASE_URL || "";
// If BASE_URL is "https://api.site.com/api", we want "https://api.site.com"
// If BASE_URL is "/api", we want "" (relative) -> Wait, no, for /api proxy we want it to stay relative to current domain
// Actually, if we are in dev (localhost:5173), /api proxies to localhost:3000.
// /uploads/ also proxies to localhost:3000 (configured in vite.config.js).
// So in dev, we don't need to change anything if it starts with /uploads/.
// BUT in PROD, VITE_API_BASE_URL is "https://studyapi.zhzgo.cn/api".
// And there is NO proxy for /uploads/ on the frontend server (study.zhzgo.cn).
// So we MUST prepend "https://studyapi.zhzgo.cn".

const getFixedSrc = (src) => {
  if (!src) return src;
  if (src.startsWith("/uploads/")) {
    // Check if we have an absolute API URL
    if (apiBaseUrl.startsWith("http")) {
      const origin = apiBaseUrl.replace(/\/api$/, "");
      return `${origin}${src}`;
    }
  }
  return src;
};

// Override image renderer
const defaultImageRender =
  md.renderer.rules.image ||
  function (tokens, idx, options, env, self) {
    return self.renderToken(tokens, idx, options);
  };

md.renderer.rules.image = function (tokens, idx, options, env, self) {
  const token = tokens[idx];
  const srcIndex = token.attrIndex("src");
  if (srcIndex >= 0) {
    const src = token.attrs[srcIndex][1];
    token.attrs[srcIndex][1] = getFixedSrc(src);
  }
  return defaultImageRender(tokens, idx, options, env, self);
};

// Task list checkboxes
md.use(taskLists, { enabled: true, label: true, labelAfter: true });

const renderedContent = computed(() => {
  let text = props.content || "";
  const placeholders = [];

  // Step 1: Extract $$...$$ (block math) and replace with placeholders
  text = text.replace(/\$\$([\s\S]+?)\$\$/g, (match, tex) => {
    const id = `%%MATH_BLOCK_${placeholders.length}%%`;
    try {
      placeholders.push(
        katex.renderToString(tex.trim(), {
          displayMode: true,
          throwOnError: false,
        }),
      );
    } catch (e) {
      placeholders.push(`<span style="color:red">${tex}</span>`);
    }
    return id;
  });

  // Step 2: Extract $...$ (inline math) and replace with placeholders
  text = text.replace(/\$([^\$\n]+?)\$/g, (match, tex) => {
    const id = `%%MATH_INLINE_${placeholders.length}%%`;
    try {
      placeholders.push(
        katex.renderToString(tex.trim(), {
          displayMode: false,
          throwOnError: false,
        }),
      );
    } catch (e) {
      placeholders.push(`<span style="color:red">${tex}</span>`);
    }
    return id;
  });

  // Step 3: Render markdown
  let html = md.render(text);

  // Step 4: Replace placeholders with rendered KaTeX HTML
  html = html.replace(/%%MATH_(?:BLOCK|INLINE)_(\d+)%%/g, (match, idx) => {
    return placeholders[parseInt(idx)] || match;
  });

  return html;
});
</script>

<template>
  <div
    class="markdown-body bg-transparent dark:text-slate-200"
    v-html="renderedContent"
  ></div>
</template>

<style>
.markdown-body {
  font-family: inherit;
  font-size: inherit;
  line-height: inherit;
  color: inherit;
  background-color: transparent !important;
}

.markdown-body p {
  margin-bottom: 0.5em;
}

.markdown-body img {
  max-width: 60%;
  max-height: 200px;
  object-fit: contain;
  border-radius: 0.5rem;
  margin: 0.5rem 0;
  display: block;
}

/* Task list */
.markdown-body .task-list-item {
  list-style: none;
  margin-left: -1.5em;
}

.markdown-body .task-list-item input[type="checkbox"] {
  margin-right: 0.5em;
  accent-color: #6366f1;
  width: 1em;
  height: 1em;
}

/* KaTeX */
.markdown-body .katex-display {
  margin: 0.5em 0;
  overflow-x: auto;
  overflow-y: hidden;
}

.markdown-body .katex {
  font-size: 1.1em;
}

/* Dark mode */
.dark .markdown-body {
  color: #e2e8f0;
}

.dark .markdown-body code {
  background-color: #1e293b;
  color: #e2e8f0;
}

.dark .markdown-body pre {
  background-color: #1e293b;
}

.dark .markdown-body blockquote {
  color: #94a3b8;
  border-left-color: #475569;
}

.dark .markdown-body .katex {
  color: #e2e8f0;
}
</style>
