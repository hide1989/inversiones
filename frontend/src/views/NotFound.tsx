/**
 * Shown for any unmatched path. Deliberately not a redirect: a redirect
 * to a known route would hide a typo in the URL from the person who
 * typed it instead of telling them the route does not exist.
 */
export function NotFound() {
  return (
    <div className="flex flex-col gap-2">
      <h1 className="font-mono text-xl font-semibold text-fg">Página no encontrada</h1>
      <p className="font-sans text-sm text-fg-muted">La ruta solicitada no existe.</p>
    </div>
  )
}
