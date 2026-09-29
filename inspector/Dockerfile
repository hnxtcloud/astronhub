FROM python:3.12-slim

# Static inspection toolchain: msitools provides msiinfo, rpm provides the
# RPM query tool, dpkg-deb ships with dpkg on Debian. No compilers, no
# package managers beyond install time.
RUN apt-get update \
    && apt-get install -y --no-install-recommends msitools rpm \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app
COPY inspector/ ./inspector/

# Non-root, writable only in /tmp; the worker holds no secrets beyond the
# inspector token supplied at runtime.
RUN useradd --system --no-create-home inspector \
    && mkdir -p /tmp/inspector && chown inspector /tmp/inspector
USER inspector

ENV INSPECTOR_WORK_DIR=/tmp/inspector \
    PYTHONUNBUFFERED=1

CMD ["python", "-m", "inspector.worker"]
