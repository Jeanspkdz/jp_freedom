// Keep each half independent so only a changed card performs the 3D turn.
export function setFlipValue(card, value, reducedMotion = false) {
  if (!card || typeof card.querySelector !== 'function') return;
  const next = String(value).padStart(2, '0');
  const previous = card.dataset.value;
  if (previous === next) return;
  const parts = selector => card.querySelector(selector);
  const top = parts('.flip-static-top .flip-face');
  const bottom = parts('.flip-static-bottom .flip-face');
  const flapTop = parts('.flip-flap-top .flip-face');
  const flapBottom = parts('.flip-flap-bottom .flip-face');
  const finish = () => {
    card.classList.remove('is-flipping');
    for (const face of [top, bottom, flapTop, flapBottom]) face.textContent = next;
    card.dataset.value = next;
  };
  card._finishFlip?.();
  if (!previous || reducedMotion) { finish(); card._finishFlip = null; return; }
  top.textContent = next;
  bottom.textContent = previous;
  flapTop.textContent = previous;
  flapBottom.textContent = next;
  card.dataset.value = next;
  card.classList.remove('is-flipping');
  // Restart the CSS animation even when the next tick arrives immediately.
  void card.offsetWidth;
  card.classList.add('is-flipping');
  const onEnd = event => {
    if (event.target !== parts('.flip-flap-bottom')) return;
    card.removeEventListener('animationend', onEnd);
    card._finishFlip = null;
    finish();
  };
  card._finishFlip = () => { card.removeEventListener('animationend', onEnd); finish(); };
  card.addEventListener('animationend', onEnd);
}

export function setClockTime(documentRef, totalSeconds, reducedMotion = false) {
  const minutes = Math.floor(totalSeconds / 60);
  const seconds = totalSeconds % 60;
  setFlipValue(documentRef.getElementById('flip-minutes'), minutes, reducedMotion);
  setFlipValue(documentRef.getElementById('flip-seconds'), seconds, reducedMotion);
  documentRef.querySelector?.('.clock-display')?.setAttribute('aria-label', `${minutes} minutes, ${seconds} seconds`);
}
