const DOMAIN_PATTERN = /^(?=.{1,253}$)(?:[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\.)+[a-z]{2,63}$/i;

export function isValidDomain(value) {
  return typeof value === 'string' && DOMAIN_PATTERN.test(value);
}
