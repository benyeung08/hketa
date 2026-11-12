import { Router, Request, Response } from 'express';
import {
  bootRouteService,
  getRoutesByMode,
  getRoute,
  searchRoutes,
  getUpdateManifest,
  updateMode,
} from '../routeService';
import { TransitMode } from '../types/eta';

bootRouteService();

export const routeRouter = Router();

const VALID_MODES: TransitMode[] = ['BUS', 'MINIBUS', 'MTR', 'LRT'];

routeRouter.get('/routes', async (_req: Request, res: Response) => {
  const all = (
    await Promise.all(VALID_MODES.map((m) => getRoutesByMode(m)))
  ).flat();
  res.json({ generatedAt: new Date().toISOString(), routes: all });
});

routeRouter.get('/routes/:mode', async (req: Request, res: Response) => {
  const mode = req.params.mode.toUpperCase() as TransitMode;
  if (!VALID_MODES.includes(mode)) {
    res.status(400).json({ error: 'invalid_mode', valid: VALID_MODES });
    return;
  }
  res.json({ mode, routes: await getRoutesByMode(mode) });
});

routeRouter.get('/route/:routeId', async (req: Request, res: Response) => {
  const route = await getRoute(req.params.routeId);
  if (!route) {
    res.status(404).json({ error: 'route_not_found' });
    return;
  }
  res.json(route);
});

routeRouter.get('/routes/search/:query', async (req: Request, res: Response) => {
  res.json({ query: req.params.query, routes: await searchRoutes(req.params.query) });
});

/* ----------------------------- updates ----------------------------- */

routeRouter.get('/updates/manifest', async (_req: Request, res: Response) => {
  res.json(await getUpdateManifest());
});

routeRouter.post('/updates/:mode', async (req: Request, res: Response) => {
  const mode = req.params.mode.toUpperCase() as TransitMode;
  if (!VALID_MODES.includes(mode)) {
    res.status(400).json({ error: 'invalid_mode', valid: VALID_MODES });
    return;
  }
  res.json(await updateMode(mode));
});

routeRouter.post('/updates', async (_req: Request, res: Response) => {
  const results = await Promise.all(VALID_MODES.map((m) => updateMode(m)));
  res.json({ generatedAt: new Date().toISOString(), results });
});
