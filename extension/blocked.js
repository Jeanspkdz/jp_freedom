document.querySelector('#return-button').addEventListener('click', () => history.back());

void chrome.runtime.sendMessage({ type: 'get-status' }).then(status => {
  if (status?.current?.name) {
    document.querySelector('#blocked-message').textContent = `This site is paused during “${status.current.name}”. It will be available when the Planned block ends.`;
  }
}).catch(() => {});
