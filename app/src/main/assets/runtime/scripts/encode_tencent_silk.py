#!/usr/bin/env python3

import os
import subprocess
import sys
import tempfile
import wave

SUPPORTED_PCM_RATES = {8000, 12000, 16000, 24000, 32000, 44100, 48000}


def convert_to_pcm_wav(input_path: str) -> str:
    fd, wav_path = tempfile.mkstemp(prefix="astrbot_tts_", suffix=".wav")
    os.close(fd)
    command = [
        "ffmpeg",
        "-y",
        "-i",
        input_path,
        "-acodec",
        "pcm_s16le",
        "-ar",
        "24000",
        "-ac",
        "1",
        wav_path,
    ]
    try:
        subprocess.run(command, check=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    except subprocess.CalledProcessError as error:
        stderr = error.stderr.decode("utf-8", errors="replace").strip() if error.stderr else ""
        stdout = error.stdout.decode("utf-8", errors="replace").strip() if error.stdout else ""
        message = stderr or stdout or f"ffmpeg exited with code {error.returncode}"
        raise RuntimeError(f"ffmpeg conversion failed: {message}") from error
    return wav_path


def ensure_pcm_wav(input_path: str) -> tuple[str, bool]:
    if input_path.lower().endswith(".wav"):
        try:
            with wave.open(input_path, "rb") as wav_file:
                rate = wav_file.getframerate()
                channels = wav_file.getnchannels()
                sample_width = wav_file.getsampwidth()
            if rate in SUPPORTED_PCM_RATES and channels == 1 and sample_width == 2:
                return input_path, False
        except wave.Error:
            pass

    wav_path = convert_to_pcm_wav(input_path)
    return wav_path, True


def main() -> int:
    if len(sys.argv) != 3:
        print("usage: encode_tencent_silk.py <input-wav> <output-silk>", file=sys.stderr)
        return 2

    input_path = sys.argv[1]
    output_path = sys.argv[2]

    try:
        import pilk  # type: ignore
    except Exception as error:
        print(f"missing pilk: {error}", file=sys.stderr)
        return 3

    wav_path, generated = ensure_pcm_wav(input_path)
    with wave.open(wav_path, "rb") as wav_file:
        rate = wav_file.getframerate()

    duration = pilk.encode(wav_path, output_path, pcm_rate=rate, tencent=True)
    if generated and os.path.exists(wav_path):
        os.remove(wav_path)
    print(duration)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
