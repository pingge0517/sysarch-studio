"""约束块求解：输入 JSON（stdin），输出 JSON（stdout）。只用 SciPy，不引入其它依赖。"""
import json, sys
import numpy as np
from scipy.optimize import least_squares

def main():
    req = json.load(sys.stdin)
    body = req["expression"]           # 例如 "P_out = P_in * eff"
    env = dict(req["inputs"])
    targets = req["targets"]

    def residual(x):
        local = dict(env)
        for i, t in enumerate(targets):
            local[t] = float(x[i])
        exec(body, {"__builtins__": {}}, local)   # 受限命名空间，禁止 import / open
        return np.array([float(local[t]) for t in targets])

    x0 = np.array([float(env.get(t, 1.0)) for t in targets])
    res = least_squares(residual, x0, xtol=req["options"]["tolerance"],
                        max_nfev=req["options"]["maxIterations"])
    print(json.dumps({
        "converged": bool(res.success),
        "outputs": {t: float(v) for t, v in zip(targets, res.x)},
        "residuals": {t: float(r) for t, r in zip(targets, res.fun)},
        "iterations": int(res.nfev),
        "solverLog": str(res.message)
    }))

if __name__ == "__main__":
    main()
