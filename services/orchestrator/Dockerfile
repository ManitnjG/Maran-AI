FROM python:3.12-slim-bookworm AS base
WORKDIR /app
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt \
    && useradd --create-home --uid 10001 maran \
    && mkdir /data && chown maran:maran /data
COPY --chown=maran:maran app ./app
ENV MARAN_DB_PATH=/data/maran.db
USER maran
EXPOSE 8000
CMD ["python","-m","uvicorn","app.main:app","--host","0.0.0.0","--port","8000","--workers","1"]
