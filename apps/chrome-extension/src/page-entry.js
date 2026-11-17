// Mark scripted pages before CSS paints so entry motion never flashes content.
document.documentElement.classList.add('js');
try {
  if (sessionStorage.getItem('dimky-tab-pill-only')) {
    document.documentElement.classList.add('tab-pill-only');
    sessionStorage.removeItem('dimky-tab-pill-only');
  }
  if (sessionStorage.getItem('dimky-nav-fallback')) {
    document.documentElement.classList.add('page-fallback-enter');
    document.documentElement.dataset.nav = sessionStorage.getItem('dimky-nav-direction') ?? 'forward';
    sessionStorage.removeItem('dimky-nav-fallback');
  }
} catch { /* Navigation remains available without session storage. */ }

let entered = false;
const enterCurrentPage = () => {
  if (entered) return;
  entered = true;
  if (document.documentElement.classList.contains('tab-pill-only')) {
    for (const element of document.querySelectorAll('[data-enter]')) element.classList.add('is-entered');
    document.querySelector('[data-page-heading], #blocked-heading')?.focus();
    return;
  }
  for (const element of document.querySelectorAll('[data-enter]')) {
    element.classList.add('is-entering');
    const onEnterEnd = event => {
      if (event.target !== element) return;
      element.removeEventListener('animationend', onEnterEnd);
      element.classList.remove('is-entering');
      element.classList.add('is-entered');
    };
    element.addEventListener('animationend', onEnterEnd);
  }
  document.querySelector('[data-page-heading], #blocked-heading')?.focus();
};

// Native MPA transitions finish before the page cascade and focus movement.
addEventListener('pageswap', event => {
  if (document.documentElement.classList.contains('tab-pill-only')) event.viewTransition?.skipTransition();
});
addEventListener('pagereveal', event => {
  if (document.documentElement.classList.contains('tab-pill-only')) event.viewTransition?.skipTransition();
  let direction = null;
  try {
    direction = sessionStorage.getItem('dimky-nav-direction');
    if (direction) sessionStorage.removeItem('dimky-nav-direction');
  } catch { /* Traversal metadata can still supply the direction. */ }
  if (direction) document.documentElement.dataset.nav = direction;
  else {
    const nav = globalThis.navigation;
    const activation = nav?.activation;
    if (activation?.navigationType === 'traverse' && activation.from && nav.currentEntry) {
      document.documentElement.dataset.nav = nav.currentEntry.index < activation.from.index ? 'back' : 'forward';
    }
  }
  const enter = () => {
    document.documentElement.classList.remove('page-transitioning');
    enterCurrentPage();
  };
  if (!event.viewTransition) {
    const panel = document.querySelector('.view-panel:not([hidden])');
    if (!document.documentElement.classList.contains('page-fallback-enter') || !panel ||
        getComputedStyle(panel).animationName === 'none') return enter();
    // The heading cascade starts after the document has finished sliding in.
    const onPanelEnd = animationEvent => {
      if (animationEvent.target !== panel) return;
      panel.removeEventListener('animationend', onPanelEnd);
      document.documentElement.classList.remove('page-fallback-enter');
      enter();
    };
    panel.addEventListener('animationend', onPanelEnd);
    return;
  }
  document.documentElement.classList.add('page-transitioning');
  event.viewTransition.finished.then(enter, enter);
}, { once: true });

document.addEventListener('DOMContentLoaded', () => {
  if (!('onpagereveal' in window)) enterCurrentPage();
}, { once: true });
