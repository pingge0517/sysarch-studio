# P3 T4: the isolated solver service (spec P3 §3.4: a separate process, no network, read-only file system).
# The Helm chart runs it with readOnlyRootFilesystem, no egress (NetworkPolicy), not as root. Build context: solver/.
FROM python:3.13-slim
RUN pip install --no-cache-dir numpy==2.5.4 scipy==1.18.1
COPY python_solver.py http_server.py /app/
ENV SOLVER_HOST=0.0.0.0 SOLVER_PORT=8091 PYTHONDONTWRITEBYTECODE=1
USER 65534:65534
EXPOSE 8091
CMD ["python", "-I", "/app/http_server.py"]
