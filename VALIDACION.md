# TracePutt — estado de validez de métricas

| Métrica | Algoritmo heredado | Publicable para fitting? | Validación pendiente |
|---|---|---|---|
| Impacto acústico | AudioRecord + RMS adaptativo | Solo evento candidato | Comparar onda/audio/vídeo; ruido; falsos impactos |
| Trayectoria del putter | Centroides + Kalman + OLS | No (ángulo absoluto) | Perspectiva, línea objetivo, escala física |
| Cara en impacto | Dos marcadores HSV | No | Homografía + frames sincronizados, tolerancia angular |
| Tempo | Postura→impacto→temporizador | No | Detectar transición backswing/downswing |
| Rodadura/dirección bola | ROI + LK óptico | No | FPS real, pelota real, desplazamiento en plano calibrado |
| Estabilidad cabeza | Pose ML Kit en píxeles | Solo exploratorio | Normalización métrica, tolerancia a oclusiones |
| Fitting de putter | No disponible | No | Ensayo cruzado por modelo, repeticiones, incertidumbre |

Se preserva el código de v9 para evolucionarlo, pero las salidas no validadas se marcan como ausentes.
