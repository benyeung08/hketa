import express from 'express';
import cors from 'cors';
import { getEtas } from './etaService';
import { bootRouteService } from './routeService';
import { etaRouter } from './routes/eta';
import { routeRouter } from './routes/routes';

const app = express();
app.use(cors());
app.use(express.json());

bootRouteService();

app.get('/health', (_req, res) => {
  res.json({ status: 'ok', service: 'hketa-api' });
});

app.use('/api', etaRouter);
app.use('/api', routeRouter);

const port = Number(process.env.PORT) || 3000;
if (require.main === module) {
  app.listen(port, () => {
    console.log(`[hketa-api] listening on http://localhost:${port}`);
  });
}

export { app, getEtas };
