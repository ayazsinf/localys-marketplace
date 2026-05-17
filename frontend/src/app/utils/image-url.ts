export function resolveImageUrl(url?: string): string {
  if (!url) {
    return '';
  }

  if (url.startsWith('http://') || url.startsWith('https://')) {
    return url;
  }

  if (url.startsWith('/api/')) {
    return url;
  }

  if (url.startsWith('/uploads/')) {
    return `/api${url}`;
  }

  const normalized = url.startsWith('/') ? url : `/${url}`;
  return `/api${normalized}`;
}