#!/usr/bin/env python3

import os
import subprocess
import sys
import tempfile
import wave

SUPPORTED_PCM_RATES = {8000, 12000, 16000, 24000, 32000, 44100, 48000}
TARGET_PCM_RATE = 24000
TRAILING_SILENCE_MS = 240


def convert_to_raw_pcm(input_path: str, sample_rate: int = TARGET_PCM_RATE) -> str:
    fd, pcm_path = tempfile.mkstemp(prefix="astrbot_tts_", suffix=".pcm")
    os.close(fd)
    command = [
        "ffmpeg",
        "-y",
        "-i",
        input_path,
        "-vn",
        "-f",
        "s16le",
        "-acodec",
        "pcm_s16le",
        "-ar",
        str(sample_rate),
        "-ac",
        "1",
        pcm_path,
    ]
    try:
        subprocess.run(command, check=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    except subprocess.CalledProcessError as error:
        stderr = error.stderr.decode("utf-8", errors="replace").strip() if error.stderr else ""
        stdout = error.stdout.decode("utf-8", errors="replace").strip() if error.stdout else ""
        message = stderr or stdout or f"ffmpeg exited with code {error.returncode}"
        raise RuntimeError(f"ffmpeg conversion failed: {message}") from error
    return pcm_path


def ensure_raw_pcm(input_path: str) -> tuple[str, int, bool]:
    if input_path.lower().endswith(".wav"):
        try:
            with wave.open(input_path, "rb") as wav_file:
                rate = wav_file.getframerate()
                channels = wav_file.getnchannels()
                sample_width = wav_file.getsampwidth()
                frames = wav_file.readframes(wav_file.getnframes())
            if rate in SUPPORTED_PCM_RATES and channels == 1 and sample_width == 2:
                fd, pcm_path = tempfile.mkstemp(prefix="astrbot_tts_", suffix=".pcm")
                os.close(fd)
                with open(pcm_path, "wb") as pcm_file:
                    pcm_file.write(frames)
                return pcm_path, rate, True
        except wave.Error:
            pass

    pcm_path = convert_to_raw_pcm(input_path, sample_rate=TARGET_PCM_RATE)
    return pcm_path, TARGET_PCM_RATE, True


def append_trailing_silence(pcm_path: str, sample_rate: int, duration_ms: int = TRAILING_SILENCE_MS) -> None:
    if duration_ms <= 0 or sample_rate <= 0:
        return
    silence_samples = max(1, int(sample_rate * duration_ms / 1000))
    silence_bytes = b"\x00\x00" * silence_samples
    with open(pcm_path, "ab") as pcm_file:
        pcm_file.write(silence_bytes)


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

    pcm_path, rate, generated = ensure_raw_pcm(input_path)
    append_trailing_silence(pcm_path, rate)
    duration = pilk.encode(pcm_path, output_path, pcm_rate=rate, tencent=True)
    if generated and os.path.exists(pcm_path):
        os.remove(pcm_path)
    print(duration)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
