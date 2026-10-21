"""Builds the YouTube Short from VideoTour's frames.

python assemble.py <video_tour dir> <sounds dir> <out dir>
(sounds from get_sounds.py; set CAPTION_FONT to a heavy font file if none of the usual ones is found)

Makes cozy-core-short.mp4 (the game's own sounds), cozy-core-short-silent.mp4 and
cozy-core-short-thumbnail.png, 1080x1920. It opens on a creeper going off at the edge of a Hearth
Lantern's reach, and its last shot leads back into the first, so a replay loops; no title or end card.

Captions are bold white capitals with a black outline straight on the footage, one word in the
Hearth's warm orange. A small Cozy Core badge stays in one place all the way through. If a
scene changes in VideoTour, change its caption here too.
"""
import csv, os, random, shutil, subprocess, sys
from PIL import Image, ImageDraw, ImageFont

frames_dir, sounds_dir, out = sys.argv[1:4]
FPS = 20
BLUE = (255, 170, 70)  # the Hearth's flame orange
repo = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', '..')

W, H, SIZE, STROKE, LEAD = 1080, 1920, 84, 7, 104
NAME = 'cozy-core-short'
# "Coming home at dusk": scene, the frames kept (first, last; None = to the end), caption lines
# ("*word*" in orange) and where the caption's top sits. A scene can appear twice with different
# frames, to change its caption partway (the roof: the glass, then the fall).
SCENES = [
    # The opening is the payoff and the refuge in one picture (Gabby, 2026-10-20): a blast on the
    # edge of home tears up the wild half of the lawn and stops at a line, with the lantern behind.
    ('s_edge', 0, None, ['HOME IS *SAFE*'], 390),
    # Why it's safe (Gabby, 2026-10-20: before, nothing in the video showed the Hearth Lantern was
    # the cause): the lantern hung on the post, "Home" above the hotbar.
    ('s_home', 0, None, ['LIGHT A *HEARTH*'], 390),
    ('s_name', 0, None, ['WELCOME *HOME*'], 390),
    # The porch blast and the roof (glass, no fall damage) wait for the longer video: two perks in
    # three seconds read as a feature list, not as a refuge.
    # Below the stonecutter screen, still above the bottom fifth YouTube covers.
    ('s_cut', 0, None, ['ONE LOG,', 'FOUR *STAIRS*'], 1360),
    ('s_wide', 0, None, None, 0),
]
BADGE_BOX = (40, 250, 540, 350)
# The call to follow (Gabby, 2026-10-20: viewers on phones rarely open the description). It sits on
# the closing wide shot (long enough to read), low in the safe zone,
# so the Short still loops straight into its first frame with no end card.
CTA = ['FOLLOW FOR MORE', '*COZY* MODS']
CTA_TOP = 1250

FONTS = [os.environ.get('CAPTION_FONT', ''), 'C:/Windows/Fonts/ariblk.ttf', '/usr/share/fonts/truetype/msttcorefonts/Arial_Black.ttf',
         '/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf']
BADGE_FONTS = [os.environ.get('BADGE_FONT', ''), 'C:/Windows/Fonts/segoeuib.ttf', '/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf']


def font(paths, size):
    for p in paths:
        if p and os.path.exists(p):
            return ImageFont.truetype(p, size)
    sys.exit('no caption font found; set CAPTION_FONT')


def run(*args):
    # -nostdin: run in the background, ffmpeg otherwise sits waiting for keyboard input.
    if args[0] == 'ffmpeg':
        args = ('ffmpeg', '-nostdin') + args[1:]
    subprocess.run(args, check=True, stdin=subprocess.DEVNULL, stdout=subprocess.DEVNULL, stderr=subprocess.PIPE)


CAPTION = font(FONTS, SIZE)


def caption(lines, top, face=None, width=W, lead=None):
    """A transparent frame-sized layer with the caption lines, centred across the first `width` pixels."""
    face = face or CAPTION
    lead = lead or LEAD
    layer = Image.new('RGBA', (W, H), (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)
    y = top
    for line in lines:
        parts = [(p, BLUE if i % 2 else (255, 255, 255)) for i, p in enumerate(line.split('*')) if p]
        x = (width - sum(d.textlength(p, font=face) for p, _ in parts)) / 2
        for text, colour in parts:
            d.text((x, y), text, font=face, fill=colour, stroke_width=STROKE, stroke_fill=(0, 0, 0))
            x += d.textlength(text, font=face)
        y += lead
    return layer


def badge():
    """A small mod-list style badge: the icon, the name, the loaders."""
    layer = Image.new('RGBA', (W, H), (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)
    box = BADGE_BOX
    d.rounded_rectangle(box, 18, fill=(20, 20, 24, 225))
    side = box[3] - box[1] - 28
    icon = Image.open(os.path.join(repo, 'art/icon.png')).convert('RGBA')
    icon = icon.resize((side, side), Image.NEAREST)
    layer.alpha_composite(icon, (box[0] + 14, box[1] + 14))
    x = box[0] + side + 32
    d.text((x, box[1] + 12), 'Cozy Core', font=font(BADGE_FONTS, 40), fill=(255, 255, 255))
    d.text((x, box[1] + 60), 'Fabric · NeoForge', font=font(BADGE_FONTS, 24), fill=(170, 170, 180))
    return layer


BADGE = badge()

with open(os.path.join(frames_dir, 'actions.tsv')) as f:
    actions = [(r[0], int(r[1]), r[2], r[3]) for r in csv.reader(f, delimiter='\t')]

work = os.path.join(out, 'work-short')
shutil.rmtree(work, ignore_errors=True)
os.makedirs(work)

FADE_IN = 4  # frames for a caption to fade in; scenes cut hard
kept = {}  # segment key -> (scene, first frame, end frame)
for index, (scene, first, last, lines, top) in enumerate(SCENES):
    names = sorted(n for n in os.listdir(frames_dir) if n.startswith(scene + '_') and n.endswith('.png') and n[len(scene) + 1:-4].isdigit())
    if not names:
        print('no frames for', scene, '(skipped)')
        continue
    end = len(names) if last is None else min(last + 1, len(names))
    key = f'{index:02d}-{scene}'
    kept[key] = (scene, first, end)
    dest = os.path.join(work, key)
    os.makedirs(dest)
    cap = caption(lines, top) if lines else None
    # Smaller, and centred left of the right ~19% where YouTube's buttons sit.
    cta = caption(CTA, CTA_TOP, font(FONTS, 64), int(W * 0.81), 80)
    cta_from = 0 if index == len(SCENES) - 1 else None
    for i, n in enumerate(range(first, end)):
        img = Image.open(os.path.join(frames_dir, names[n])).convert('RGBA')
        if img.size != (W, H):
            img = img.resize((W, H), Image.LANCZOS)
        img.alpha_composite(BADGE)
        if cap is not None and not (cta_from is not None and i >= cta_from):
            a = min(1.0, (i + 1) / FADE_IN)
            layer = cap.copy()
            layer.putalpha(layer.getchannel('A').point(lambda v: int(v * a)))
            img.alpha_composite(layer)
        if cta_from is not None and i >= cta_from:
            a = min(1.0, (i + 1) / FADE_IN)
            layer = cta.copy()
            layer.putalpha(layer.getchannel('A').point(lambda v: int(v * a)))
            img.alpha_composite(layer)
        img.convert('RGB').save(os.path.join(dest, '%04d.png' % i))

clips, starts, t = [], {}, 0.0
for key, (scene, first, end) in kept.items():
    clip = os.path.join(work, key + '.mp4')
    run('ffmpeg', '-y', '-framerate', str(FPS), '-i', os.path.join(work, key, '%04d.png'),
        '-vf', 'format=yuv420p', '-r', '30', '-c:v', 'libx264', '-preset', 'slow', '-crf', '18', clip)
    starts[key] = t
    t += (end - first) / FPS
    clips.append(clip)
total = t
with open(os.path.join(work, 'list.txt'), 'w') as f:
    f.writelines(f"file '{c.replace(os.sep, '/')}'\n" for c in clips)
silent = os.path.join(out, NAME + '-silent.mp4')
run('ffmpeg', '-y', '-f', 'concat', '-safe', '0', '-i', os.path.join(work, 'list.txt'), '-c', 'copy', '-movflags', '+faststart', silent)

# The game's own sounds at the logged frames: the fuse and the blast, the fireplace crackle that
# comes with "Home" (the walk opens just as it plays), the window breaking and the stonecutter click.
random.seed(7)
SOUND = {'fuse': ['fuse'], 'boom': ['boom1', 'boom2'], 'place': ['place1'], 'crackle': ['crackle1', 'crackle2'],
         'thunder': ['thunder'], 'break': ['glass'], 'click': ['click']}
cues = []
for key, (scene, first, end) in kept.items():
    if scene == 's_name' and first == 0:
        cues.append((starts[key] + 0.1, random.choice(SOUND['crackle']), 1.0, 0.7))
for scene, frame, kind, what in actions:
    if kind not in SOUND:
        continue
    for key, (s2, first, end) in kept.items():
        if s2 != scene or not first <= frame < end:
            continue
        at = starts[key] + (frame - first) / FPS
        cues.append((at, random.choice(SOUND[kind]), 0.9 + random.random() * 0.2, 0.8))
        if kind == 'place':
            # The "Home" sign's crackle comes a moment after the lantern is down.
            cues.append((at + 0.3, random.choice(SOUND['crackle']), 1.0, 0.6))
inputs, filters = [], []
for i, (at, sound, pitch, volume) in enumerate(cues):
    inputs += ['-i', os.path.join(sounds_dir, sound + '.ogg')]
    ms = int(at * 1000)
    filters.append(f'[{i}:a]aformat=sample_rates=44100:channel_layouts=stereo,asetrate={44100 * pitch:.0f},aresample=44100,'
                   f'volume={volume},adelay={ms}|{ms}[s{i}]')
mix = ''.join(f'[s{i}]' for i in range(len(cues)))
filters.append(f'{mix}amix=inputs={len(cues)}:normalize=0,apad,atrim=0:{total:.3f}[a]')
audio = os.path.join(work, 'sound.wav')
# A hard stop at the video's length: with a long sound (fire) in the mix, apad could keep ffmpeg waiting.
run('ffmpeg', '-y', *inputs, '-filter_complex', ';'.join(filters), '-map', '[a]', '-t', f'{total:.3f}', audio)
run('ffmpeg', '-y', '-i', silent, '-i', audio, '-map', '0:v', '-map', '1:a', '-c:v', 'copy', '-c:a', 'aac', '-b:a', '160k',
    '-shortest', '-movflags', '+faststart', os.path.join(out, NAME + '.mp4'))

# The thumbnail: a clean frame from the opening scene, just after the blast, with the mod's name large.
raw = sorted(n for n in os.listdir(frames_dir) if n.startswith(SCENES[0][0] + '_'))
stripped = [f for s, f, k, _ in actions if s == SCENES[0][0] and k == 'boom']
pick = min(len(raw) - 1, (stripped[0] + 20) if stripped else len(raw) * 2 // 3)
thumb = Image.open(os.path.join(frames_dir, raw[pick])).convert('RGBA').resize((W, H), Image.LANCZOS)
d = ImageDraw.Draw(thumb)
size, top = 150, 300
big = font(FONTS, size)
for i, parts in enumerate([[('COZY', (255, 255, 255))], [('CORE', BLUE)]]):
    x = (W - sum(d.textlength(text, font=big) for text, _ in parts)) / 2
    for text, colour in parts:
        d.text((x, top + i * size * 1.1), text, font=big, fill=colour, stroke_width=size // 12, stroke_fill=(0, 0, 0))
        x += d.textlength(text, font=big)
thumb.convert('RGB').save(os.path.join(out, NAME + '-thumbnail.png'))
print('total seconds', round(total, 2))
for key, (scene, first, end) in kept.items():
    print(f'  {key}: {(end - first) / FPS:.2f} s')
