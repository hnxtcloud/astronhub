#!/usr/bin/env python3
"""Exercise deployed session/CSRF, resumable material and real private Skill submission.

Set PUBLICATION_SMOKE_URL, PUBLICATION_SMOKE_USERNAME and PUBLICATION_SMOKE_PASSWORD.
The account must be able to publish private Skills in PUBLICATION_SMOKE_NAMESPACE
(default global). Creates uniquely named acceptance content; never resets shared data.
"""
import http.cookiejar
import io
import json
import os
import time
import urllib.parse
import urllib.request
import zipfile


def main():
    base = os.environ.get('PUBLICATION_SMOKE_URL', 'http://localhost:8080').rstrip('/')
    username = os.environ['PUBLICATION_SMOKE_USERNAME']
    password = os.environ['PUBLICATION_SMOKE_PASSWORD']
    namespace = os.environ.get('PUBLICATION_SMOKE_NAMESPACE', 'global')
    cookies = http.cookiejar.CookieJar()
    client = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(cookies))

    def request(path, method='GET', data=None, binary=False):
        token = next((urllib.parse.unquote(cookie.value) for cookie in cookies if cookie.name == 'XSRF-TOKEN'), '')
        headers = {'Content-Type': 'application/octet-stream' if binary else 'application/json'}
        if token:
            headers['X-XSRF-TOKEN'] = token
        body = data if binary else None if data is None else json.dumps(data).encode()
        with client.open(urllib.request.Request(base + path, data=body, headers=headers, method=method), timeout=180) as response:
            payload = json.load(response)
            assert payload.get('code') == 0, payload.get('msg')
            return payload.get('data')

    request('/api/v1/auth/providers')
    request('/api/v1/auth/local/login', 'POST', {'username': username, 'password': password})
    name = f'publication-smoke-{time.time_ns()}'
    archive = io.BytesIO()
    with zipfile.ZipFile(archive, 'w', zipfile.ZIP_DEFLATED) as package:
        package.writestr('SKILL.md', f'---\nname: {name}\ndescription: Publication operator smoke\nversion: 1.0.0\n---\nLocal acceptance instructions.\n')
    material = archive.getvalue()
    task = request('/api/web/publications', 'POST')
    path = '/api/web/publications/' + task['id']
    task = request(path + '?revision=0')
    task = request(path + '/material-upload?revision=' + str(task['revision']), 'POST', {'fileName': 'smoke.zip', 'byteSize': len(material), 'replace': False})
    revision = task['revision']
    task = request(path + '/material-upload/1?revision=' + str(revision), 'PUT', material, True)
    replay = request(path + '/material-upload/1?revision=' + str(revision), 'PUT', material, True)
    assert replay['revision'] == task['revision'], 'identical replay must not duplicate progress'
    task = request(path + '/material-upload/complete?revision=' + str(task['revision']), 'POST')
    assert task['draft']['type'] == 'SKILL' and task['materialReady']
    draft = task['draft'] | {'namespace': namespace, 'visibility': 'PRIVATE', 'confirmWarnings': True}
    task = request(path, 'PUT', {'revision': task['revision'], 'draft': draft})
    task = request(path)  # Reopen the persisted checkpoint before execution.
    task = request(path + '/execute?revision=' + str(task['revision']), 'POST')
    assert task['status'] == 'COMPLETE' and len(task['progress']['results']) == 1
    task = request(path + '/cancel?revision=' + str(task['revision']), 'POST')
    assert task['status'] == 'CANCELLED' and task['progress']['results'][0]['resourceId']
    assert not task['materialReady'], 'cancel must dispose staging without deleting content'
    print('Publication smoke passed:', task['id'])


if __name__ == '__main__':
    main()
