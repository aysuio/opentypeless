"""Explicit, opt-in speech benchmark. Never reads app credential stores.

Manifest: [{"audio":"sample.wav","reference":"今天部署 Kubernetes", "terms":["Kubernetes"]}]
Run --self-test without a key/network. Real audio upload requires --send-to-groq.
"""
import argparse
import json
import os
from pathlib import Path
import re
import unicodedata
import urllib.request
import uuid
import time

ENDPOINT = "https://api.groq.com/openai/v1/audio/transcriptions"

def normalized(text):
    return "".join(c for c in unicodedata.normalize("NFKC", text).casefold()
                   if not c.isspace() and not unicodedata.category(c).startswith("P"))

def distance(reference, actual):
    previous = list(range(len(actual) + 1))
    for i, a in enumerate(reference, 1):
        row = [i]
        for j, b in enumerate(actual, 1):
            row.append(min(row[-1] + 1, previous[j] + 1, previous[j-1] + (a != b)))
        previous = row
    return previous[-1]

def score(reference, actual, terms):
    expected, result = normalized(reference), normalized(actual)
    edits = distance(expected, result)
    return {"character_edits": edits, "reference_characters": len(expected),
            "cer": edits / len(expected) if expected else (0 if not result else None),
            "terms": {term: normalized(term) in result for term in terms}}

def dictionary_prompt(words):
    result = ""
    for word in words:
        word = word.strip()
        if not word or any(unicodedata.category(c) == "Cc" for c in word):
            continue
        candidate = result + (", " if result else "") + word
        if len(candidate.encode("utf-8")) <= 200:
            result = candidate
    return result

class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None

def transcribe(audio, model, terms, key):
    boundary = "opentypeless-" + uuid.uuid4().hex
    content = bytearray()
    def field(name, value):
        content.extend((f'--{boundary}\r\nContent-Disposition: form-data; name="{name}"\r\n\r\n{value}\r\n').encode())
    field("model", model)
    field("temperature", "0")
    field("response_format", "json")
    prompt = dictionary_prompt(terms)
    if prompt:
        field("prompt", prompt)
    content.extend((f'--{boundary}\r\nContent-Disposition: form-data; name="file"; filename="audio.wav"\r\nContent-Type: audio/wav\r\n\r\n').encode())
    content.extend(audio)
    content.extend(f"\r\n--{boundary}--\r\n".encode())
    request = urllib.request.Request(ENDPOINT, data=bytes(content), headers={
        "Authorization": "Bearer " + key,
        "Content-Type": "multipart/form-data; boundary=" + boundary,
    })
    started = time.monotonic()
    with urllib.request.build_opener(NoRedirect).open(request, timeout=90) as response:
        body = response.read(1024 * 1024 + 1)
        if len(body) > 1024 * 1024:
            raise ValueError("Provider response exceeded size limit")
        text = json.loads(body)["text"]
    return text, round(time.monotonic() - started, 3)

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("manifest", nargs="?", type=Path)
    parser.add_argument("--send-to-groq", action="store_true", help="Explicitly authorize uploading every manifest WAV to Groq")
    parser.add_argument("--output", type=Path, default=Path(".private/evaluation.json"))
    parser.add_argument("--models", nargs="+", choices=["whisper-large-v3", "whisper-large-v3-turbo"], default=["whisper-large-v3", "whisper-large-v3-turbo"])
    parser.add_argument("--self-test", action="store_true")
    args = parser.parse_args()
    if args.self_test:
        assert distance("abc", "axc") == 1
        assert distance("", "abc") == 3
        assert score("部署 Kubernetes。", "部署 Kubernetes", ["Kubernetes"])["cer"] == 0
        assert score("不要删除", "要删除", ["不要"])["terms"]["不要"] is False
        assert dictionary_prompt(["普通话", "x" * 300, "Groq"]) == "普通话, Groq"
        print("5 offline evaluation checks passed; no network requests")
        return
    if not args.manifest or not args.send_to_groq:
        parser.error("A manifest and --send-to-groq are required. Nothing was uploaded.")
    key = os.environ.get("GROQ_API_KEY", "").strip()
    if not key:
        parser.error("Set GROQ_API_KEY locally; never put it in the manifest or command arguments.")
    samples = json.loads(args.manifest.read_text(encoding="utf-8"))
    prepared = []
    for item in samples:
        path = (args.manifest.parent / item["audio"]).resolve()
        if path.suffix.lower() != ".wav" or path.stat().st_size > 24 * 1024 * 1024:
            parser.error("Only WAV files up to 24 MiB are accepted")
        if not isinstance(item.get("reference"), str):
            parser.error("Every sample needs a human reference")
        audio = path.read_bytes()
        if audio[:4] != b"RIFF" or audio[8:12] != b"WAVE":
            parser.error("Invalid WAV header")
        prepared.append((item, audio))
    results = []
    for index, (item, audio) in enumerate(prepared, 1):
        for model in args.models:
            text, seconds = transcribe(audio, model, item.get("terms", []), key)
            results.append({"sample": index, "model": model, "reference": item["reference"],
                            "text": text, "request_seconds": seconds,
                            **score(item["reference"], text, item.get("terms", []))})
            print(f"Sample {index}: {model}, {seconds}s")
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(results, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"Saved private transcript results to {args.output}. Inspect numbers and meaning manually.")

if __name__ == "__main__":
    main()
