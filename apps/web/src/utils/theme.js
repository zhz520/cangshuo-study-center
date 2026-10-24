const THEME_KEY = 'theme';

export function getTheme() {
  return localStorage.getItem(THEME_KEY) === 'dark' ? 'dark' : 'light';
}

export function applyTheme(theme = getTheme()) {
  const value = theme === 'dark' ? 'dark' : 'light';
  document.documentElement.classList.toggle('dark', value === 'dark');
  document.documentElement.style.colorScheme = value;
  return value;
}

export function setTheme(theme) {
  const value = theme === 'dark' ? 'dark' : 'light';
  localStorage.setItem(THEME_KEY, value);
  applyTheme(value);
  window.dispatchEvent(new CustomEvent('study-theme-change', { detail: value }));
  return value;
}

export function toggleTheme() {
  return setTheme(getTheme() === 'dark' ? 'light' : 'dark');
}
