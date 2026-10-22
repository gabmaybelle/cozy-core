"""Lays a recorded voiceover over a finished Short, line by line, with the game's sounds and a soft
music bed under it, and brings the mix to YouTube's loudness (about -14 LUFS).

python add_voice.py <video.mp4> <voice recording> <timeline.json> <music> <out.mp4> [music start s]

The video is assemble.py's output with the game's own sounds; timeline.json is the shot start times
it writes beside it. LINES says where each spoken line is in the recording (start and end, in
seconds) and which shot it plays over, starting how far into that shot. Measure the lines with
ffmpeg's silencedetect; re-check them for every new recording. The video stream is copied as it is.

Loudness is measured first and set with fixed gains, never with ffmpeg's loudnorm on the way: in a
single pass loudnorm eases in over its first seconds, which swallowed the first spoken line
(2026-10-21).
"""
import json, re, subprocess, sys

video, voice, timeline, music, out = sys.argv[1:6]
music_start = float(sys.argv[6]) if len(sys.argv) > 6 else 0.0

# "Home comes alive", Gabby's take of 2026-10-21 (22.9 s; lines found by silencedetect at -38 dB).
LINES = [
    (1.38, 4.67, 'v_home', 0.15),     # "I light a hearth, and my home starts looking after itself."
    (5.90, 8.88, 'v_candles', 1.75),  # "At sunset, the candles light up one by one."
    (9.90, 13.42, 'v_cat', 0.45),     # "At night, my cat comes home and curls up by the lantern."
    (14.53, 17.77, 'v_flower', 0.30), # "And every so often, a new flower turns up in the garden."
    (18.75, 22.87, 'v_morning', 0.20),  # "It's called Cozy Core. Follow for more cozy mods."
]
# The voice's own loudness, the game's sounds as a fixed cut, the music's loudness, the whole mix.
VOICE_LUFS, GAME_DB, MUSIC_LUFS, TARGET_LUFS = -16.0, -5.0, -31.0, -14.0


def run(args):
    return subprocess.run(['ffmpeg', '-nostdin', '-hide_banner'] + args, check=True, stdin=subprocess.DEVNULL,
                          stdout=subprocess.DEVNULL, stderr=subprocess.PIPE, text=True, errors='replace').stderr


def loudness(inputs, graph):
    """Integrated loudness (LUFS) of what `graph` produces as [m]."""
    log = run(inputs + ['-filter_complex', graph + ';[m]ebur128=framelog=quiet[o]', '-map', '[o]', '-f', 'null', '-'])
    return float(re.findall(r'I:\s+(-?[\d.]+) LUFS', log)[-1])


starts = json.load(open(timeline))
total = starts['total']
for i, (a, b, shot, offset) in enumerate(LINES):
    if starts[shot] + offset + (b - a) > total:
        sys.exit(f'line {i + 1} runs past the end of the video')

# Measured on the spoken lines only (not the pauses), so the gain is set by the voice itself.
n = len(LINES)
voice_graph = ';'.join([f'[0:a]asplit={n}' + ''.join(f'[s{i}]' for i in range(n))]
                       + [f'[s{i}]atrim=start={a}:end={b},asetpts=PTS-STARTPTS[c{i}]' for i, (a, b, _, _) in enumerate(LINES)]
                       + [''.join(f'[c{i}]' for i in range(n)) + f'concat=n={n}:v=0:a=1[m]'])
voice_gain = VOICE_LUFS - loudness(['-i', voice], voice_graph)
music_gain = MUSIC_LUFS - loudness(['-i', music], f'[0:a]atrim=start={music_start}:duration={total}[m]')

parts = [f'[1:a]asplit={n}' + ''.join(f'[r{i}]' for i in range(n))]
for i, (a, b, shot, offset) in enumerate(LINES):
    at = int((starts[shot] + offset) * 1000)
    length = b - a
    parts.append(f'[r{i}]atrim=start={a}:end={b},asetpts=PTS-STARTPTS,aformat=sample_rates=48000:channel_layouts=stereo,'
                 f'volume={voice_gain:.2f}dB,afade=t=in:d=0.04,afade=t=out:st={max(0.0, length - 0.08):.3f}:d=0.08,'
                 f'adelay={at}|{at}[v{i}]')
fade = 1.0
mix = ';'.join(parts + [
    ''.join(f'[v{i}]' for i in range(n)) + f'amix=inputs={n}:normalize=0,apad,atrim=0:{total:.3f}[voice]',
    f'[0:a]aformat=sample_rates=48000:channel_layouts=stereo,volume={GAME_DB}dB,apad,atrim=0:{total:.3f}[game]',
    f'[2:a]atrim=start={music_start}:duration={total},asetpts=PTS-STARTPTS,aformat=sample_rates=48000:channel_layouts=stereo,'
    f'volume={music_gain:.2f}dB,afade=t=in:d=0.5,afade=t=out:st={max(0.0, total - fade):.3f}:d={fade}[music]',
    '[voice][game][music]amix=inputs=3:normalize=0[m]',
])
inputs = ['-i', video, '-i', voice, '-i', music]
gain = TARGET_LUFS - loudness(inputs, mix)
final = mix.replace('amix=inputs=3:normalize=0[m]', f'amix=inputs=3:normalize=0,volume={gain:.2f}dB,alimiter=limit=0.84:level=false[a]')
run(inputs + ['-filter_complex', final, '-map', '0:v', '-map', '[a]', '-c:v', 'copy', '-c:a', 'aac', '-b:a', '192k',
              '-t', f'{total:.3f}', '-movflags', '+faststart', '-y', out])
print(f'{out} (voice {voice_gain:+.1f} dB, music {music_gain:+.1f} dB, mix {gain:+.1f} dB)')
