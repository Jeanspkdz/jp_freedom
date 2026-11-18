import { createServer } from 'node:http';
import { pathToFileURL } from 'node:url';
import handler from './index.js';

const server = createServer(async (incoming, outgoing) => {
  try {
    const headers = new Headers();
    for (const [name, value] of Object.entries(incoming.headers)) {
      if (Array.isArray(value)) value.forEach(item => headers.append(name, item));
      else if (value !== undefined) headers.set(name, value);
    }
    const chunks = [];
    for await (const chunk of incoming) chunks.push(chunk);
    const body = Buffer.concat(chunks);
    const request = new Request(`http://${headers.get('host') ?? 'localhost'}${incoming.url}`, {
      method: incoming.method,
      headers,
      ...(incoming.method === 'GET' || incoming.method === 'HEAD' ? {} : { body }),
    });
    const response = await handler(request);
    outgoing.writeHead(response.status, Object.fromEntries(response.headers.entries()));
    outgoing.end(Buffer.from(await response.arrayBuffer()));
  } catch (error) {
    console.error('Request failed', error);
    outgoing.writeHead(500, { 'content-type': 'application/json' });
    outgoing.end('{"error":"internal_error"}');
  }
});

if (import.meta.url === pathToFileURL(process.argv[1] ?? '').href) {
  const port = Number(process.env.PORT ?? 8787);
  server.listen(port, '0.0.0.0', () => console.log(`Tally sync API listening on ${port}`));
}
