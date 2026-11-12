import { Router, Request, Response } from 'express';
import { getEtas, Operator } from '../etaService';

export const etaRouter = Router();

const ALL: Operator[] = ['KMB', 'CTB', 'NLB'];

etaRouter.get('/eta/:stopId', async (req: Request, res: Response) => {
  const { stopId } = req.params;
  const opsParam = req.query.operators as string | undefined;
  const operators = opsParam
    ? (opsParam.split(',').filter((v): v is Operator =>
        ['KMB', 'CTB', 'NLB'].includes(v),
      ) as Operator[])
    : ALL;

  try {
    const result = await getEtas(stopId, operators);
    res.json(result);
  } catch (err) {
    const message = err instanceof Error ? err.message : 'unknown error';
    res.status(502).json({ error: 'upstream_failed', message });
  }
});
