#!/usr/bin/env python3
"""
Estrae lo stream URL di un video YouTube e lo salva in Supabase (current_track).
Uso: python push_track.py <youtube_video_id>
Richiede: yt-dlp  (winget install yt-dlp)

Idempotente: eseguire più volte con lo stesso video_id produce sempre
lo stesso stato finale su Supabase (upsert su user_id).
"""

import sys
import json
import os
import re
import subprocess
import urllib.request
import urllib.error
from datetime import datetime, timezone, timedelta


def load_env(path):
    env = {}
    with open(path) as f:
        for line in f:
            line = line.strip()
            if line and not line.startswith('#') and '=' in line:
                key, _, val = line.partition('=')
                env[key.strip()] = val.strip()
    return env


def fetch_track_info(video_id):
    """Singola chiamata yt-dlp: restituisce metadati + URL di streaming."""
    yt_url = f"https://www.youtube.com/watch?v={video_id}"
    raw = subprocess.check_output(
        [
            'yt-dlp',
            '-j',
            '-f', 'bestaudio[ext=m4a]/bestaudio/best',
            '--no-playlist',
            yt_url,
        ],
        text=True,
        stderr=subprocess.DEVNULL,
    )
    return json.loads(raw)


def parse_expiry(stream_url):
    m = re.search(r'expire=(\d+)', stream_url)
    if m:
        return datetime.fromtimestamp(int(m.group(1)), tz=timezone.utc).isoformat()
    return (datetime.now(tz=timezone.utc) + timedelta(hours=6)).isoformat()


def upsert_supabase(uri, service_key, payload):
    data = json.dumps(payload).encode('utf-8')
    req = urllib.request.Request(
        uri,
        data=data,
        headers={
            'apikey':        service_key,
            'Authorization': f'Bearer {service_key}',
            'Content-Type':  'application/json',
            'Prefer':        'resolution=merge-duplicates',
        },
        method='POST',
    )
    with urllib.request.urlopen(req) as resp:
        return resp.status, resp.reason


def main():
    if len(sys.argv) < 2:
        print("Uso: python push_track.py <youtube_video_id>")
        print("Es.: python push_track.py dQw4w9WgXcQ")
        sys.exit(1)

    video_id = sys.argv[1].strip()

    script_dir = os.path.dirname(os.path.abspath(__file__))
    env = load_env(os.path.join(script_dir, '..', '.env'))

    supabase_uri = env['SUPABASE_DB_TRACK_URI']
    service_key  = env['SUPABASE_SERVICE_KEY']
    user_email   = env['USER_EMAIL']

    # Unica chiamata a yt-dlp — metadati + URL in un colpo solo
    print(f"Recupero traccia {video_id}...")
    try:
        info = fetch_track_info(video_id)
    except FileNotFoundError:
        print("ERRORE: yt-dlp non trovato. Installalo con: winget install yt-dlp")
        sys.exit(1)

    stream_url = info.get('url') or info.get('webpage_url')
    title      = info.get('title', 'Sconosciuto')
    artist     = info.get('uploader') or info.get('artist') or 'Sconosciuto'
    expire_iso = parse_expiry(stream_url)
    now_iso    = datetime.now(tz=timezone.utc).isoformat()

    print(f"Traccia : {title}")
    print(f"Artista : {artist}")
    print(f"Scade   : {expire_iso}")
    print(f"User    : {user_email}")

    payload = {
        "user_id":        user_email,
        "url":            stream_url,
        "url_expires_at": expire_iso,
        "offset":         0,
        "track_title":    title,
        "track_artist":   artist,
        "updated_at":     now_iso,
    }

    print("\nAggiornamento Supabase...")
    try:
        status, reason = upsert_supabase(supabase_uri + '?on_conflict=user_id', service_key, payload)
        print(f"Supabase: {status} {reason}")
        print("current_track aggiornato con successo.")
    except urllib.error.HTTPError as e:
        body = e.read().decode('utf-8', errors='replace')
        print(f"Errore Supabase {e.code}: {body}")
        sys.exit(1)


if __name__ == '__main__':
    main()
