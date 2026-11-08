let sequence = 0;

export function notify(message, type = 'info', duration = 3000) {
  if (typeof window === 'undefined') return;
  window.dispatchEvent(new CustomEvent('study-ui-toast', { detail: { message, type, duration } }));
}

export function confirmAction(options = {}) {
  if (typeof window === 'undefined') return Promise.resolve(false);
  return new Promise((resolve) => {
    const id = ++sequence;
    const onResult = (event) => {
      if (event.detail?.id !== id) return;
      window.removeEventListener('study-ui-confirm-result', onResult);
      resolve(Boolean(event.detail.value));
    };
    window.addEventListener('study-ui-confirm-result', onResult);
    window.dispatchEvent(new CustomEvent('study-ui-confirm', { detail: { id, options } }));
  });
}
