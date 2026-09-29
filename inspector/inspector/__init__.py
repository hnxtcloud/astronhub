"""Inspector worker for the software center.

Claims persisted static-inspection tasks from the restricted internal API,
streams the immutable package bytes, extracts version metadata with
fixed-argument subprocess tools (msiinfo, dpkg-deb, rpm) or stdlib binary
parsing, and reports the attempt result with its attempt token. The worker
holds no database or admin credentials — the deployment-scoped inspector
token is its only identity.
"""

__version__ = "0.1.0"
