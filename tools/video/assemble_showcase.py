"""Builds the showcase video, "a day at the cottage", from VideoTour's wide (d_) frames.

python assemble_showcase.py <video_tour dir> <sounds dir> <out dir> [showcase | trailer]
(sounds from get_sounds.py; set CAPTION_FONT to a heavy font file if none of the usual ones is found)

Makes, at 1920x1080: cozy-core-showcase.mp4 (the game's own sounds), cozy-core-showcase-silent.mp4,
cozy-core-showcase-thumbnail.png (1280x720), one GIF per scene (each under 5 MB) and chapters.txt
(the YouTube chapter times). It opens on a quiet sunrise at the cottage with the title over it and
ends on the next sunrise with the end card.

No captions explain the features (Gabby, 2026-10-20): the pictures and the game's sounds show what
happens. The only words on screen are the title, the end card and the mod's own "Home" text.
"""
import csv, json, os, random, shutil, subprocess, sys
from concurrent.futures import ThreadPoolExecutor
from PIL import Image, ImageDraw, ImageEnhance, ImageFont

# Absolute: ffmpeg's concat list resolves relative paths against its own folder.
frames_dir, sounds_dir, out = (os.path.abspath(p) for p in sys.argv[1:4])
# 'showcase' (the d_ scenes) or 'trailer' (the tr_ scenes): an optional fourth argument.
CUT = sys.argv[4] if len(sys.argv) > 4 else 'showcase'
FPS = 20
W, H = 1920, 1080
ORANGE = (255, 170, 70)  # the Hearth's flame
NAME = 'cozy-core-' + CUT
repo = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', '..')

# Running order: scene, chapter it opens (or None), first frame kept and last (None = to the end).
# Chapter names are YouTube's (chapters.txt); nothing names them on screen.
SCENES = [
    ('d_dawn', 'Morning', 0, None),
    ('d_home', None, 0, None),
    ('d_cut', None, 0, None),
    ('d_dye', None, 0, None),
    ('d_roof', None, 0, None),
    ('d_trader', None, 0, None),
    ('d_anvil', 'Evening', 0, None),
    ('d_name', None, 0, None),
    ('d_styles', None, 0, None),
    ('d_night', 'Night', 0, None),
    ('d_storm', None, 0, None),
    ('d_porch', None, 0, None),
    ('d_morning', None, 0, None),
]
# The trailer "When the sun goes down" (0.8.0; plan: mods-media cozy-core/TRAILER-PLAN.md): one evening,
# in motion from the first frame, no title card and no chapters; her voice is laid over it afterwards
# with add_voice.py, and the shots are held to her lines. The end card closes it.
# Frame ranges from take 02 (2026-10-22), cut to her recorded lines (TRAILER-PLAN.md, "Cut to her lines").
TRAILER = [
    ('tr_walk', None, 0, None),
    ('tr_candles', None, 40, None),
    ('tr_cat', None, 0, None),
    ('tr_visitor', None, 0, None),
    ('tr_creeper', None, 0, 139),
    ('tr_tame', None, 0, None),
    ('tr_flower', None, 0, None),
    ('tr_end', None, 120, None),
]
# Scenes played slower than filmed: the stray settles in under two seconds, too short for its line.
SPEED = {}
if CUT == 'trailer':
    SCENES = TRAILER
    SPEED = {'tr_visitor': 0.55}
# The night shot from high above is dark enough under the shader that the monsters are hard to see.
BRIGHTEN = {'d_night': 1.5, 'tr_cat': 1.45, 'tr_creeper': 1.35}
# Scenes under steady rain: a bed of the game's rain sounds under them.
RAIN = {'d_storm', 'd_porch'}
TITLE_FROM = 30  # frames into the opening before the title fades in
NAME_LINE, PROMISE = '*Cozy Core*', 'Light a hearth: your home stays safe, cozy and easy to build.'
END = ['*Cozy Core*', 'Fabric and NeoForge', 'Minecraft 26.3 and 1.21.1', 'plus a data pack of the recipes for plain vanilla']
if CUT == 'trailer':
    END = ['*Cozy Core*', 'Fabric and NeoForge, Minecraft 26.3 and 1.21.1', 'Search "Cozy Core" on CurseForge']
FADE = 6  # frames: captions fade in; a chapter fades in from black and the video fades out at the end

FONTS = [os.environ.get('CAPTION_FONT', ''), 'C:/Windows/Fonts/ariblk.ttf', '/usr/share/fonts/truetype/msttcorefonts/Arial_Black.ttf',
         '/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf']
TEXT_FONTS = [os.environ.get('BADGE_FONT', ''), 'C:/Windows/Fonts/segoeuib.ttf', '/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf']


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


def words(layer, lines, top, face, stroke, lead, centre=W / 2):
    """Draws caption lines, centred on `centre`, each "*word*" in orange."""
    d = ImageDraw.Draw(layer)
    y = top
    for line in lines:
        parts = [(p, ORANGE if i % 2 else (255, 255, 255)) for i, p in enumerate(line.split('*')) if p]
        x = centre - sum(d.textlength(p, font=face) for p, _ in parts) / 2
        for text, colour in parts:
            d.text((x, y), text, font=face, fill=colour, stroke_width=stroke, stroke_fill=(0, 0, 0))
            x += d.textlength(text, font=face)
        y += lead
    return layer


def title():
    """The name large, the promise under it, over the opening sunrise."""
    layer = Image.new('RGBA', (W, H), (0, 0, 0, 0))
    words(layer, [NAME_LINE], H // 2 - 120, font(FONTS, 120), 10, 150)
    return words(layer, [PROMISE], H // 2 + 64, font(TEXT_FONTS, 52), 5, 66)


def end_card():
    """Darkened footage with the icon, the name, where it runs and the credit."""
    layer = Image.new('RGBA', (W, H), (0, 0, 0, 120))
    icon = Image.open(os.path.join(repo, 'art/icon.png')).convert('RGBA').resize((200, 200), Image.NEAREST)
    layer.alpha_composite(icon, ((W - 200) // 2, 170))
    words(layer, END[:1], 400, font(FONTS, 96), 8, 120)
    words(layer, END[1:], 540, font(TEXT_FONTS, 50), 4, 66)
    # The credit line lives in the description, not on the card (Gabby, 2026-10-20).
    return layer


def faded(layer, a):
    if a >= 1:
        return layer
    out = layer.copy()
    out.putalpha(out.getchannel('A').point(lambda v: int(v * a)))
    return out


# A Minecraft-ish pixel arrow (1 = white, 2 = black outline), drawn at 3x: the cursor on screens.
ARROW = ["2", "22", "212", "2112", "21112", "211112", "2111112", "21111112", "211111112", "2111112222",
         "2112112", "212 2112", "22  2112", "2    2112", "     2112", "      22"]
cursor = Image.new('RGBA', (30, 48), (0, 0, 0, 0))
d = ImageDraw.Draw(cursor)
for y, row in enumerate(ARROW):
    for x, c in enumerate(row):
        if c in '12':
            d.rectangle([x * 3, y * 3, x * 3 + 2, y * 3 + 2], fill=(255, 255, 255, 255) if c == '1' else (0, 0, 0, 255))

with open(os.path.join(frames_dir, 'frames.tsv')) as f:
    cursor_at = {(r[0], int(r[1])): (r[2], r[3]) for r in csv.reader(f, delimiter='\t') if len(r) > 3}
with open(os.path.join(frames_dir, 'actions.tsv')) as f:
    actions = [(r[0], int(r[1]), r[2], r[3]) for r in csv.reader(f, delimiter='\t')]

work = os.path.join(out, 'work-' + CUT)
shutil.rmtree(work, ignore_errors=True)
os.makedirs(work)
TITLE_LAYER, END_LAYER = title(), end_card()

kept = {}  # scene -> (first, end)
jobs = []  # one per frame to draw
for index, (scene, chap, first, last) in enumerate(SCENES):
    names = sorted(n for n in os.listdir(frames_dir) if n.startswith(scene + '_') and n.endswith('.png') and n[len(scene) + 1:-4].isdigit())
    if not names:
        print('no frames for', scene, '(skipped)')
        continue
    if isinstance(first, str):
        # "kind-N": start N frames before the scene's first logged `kind` (a long wait trimmed).
        kind, before = first.rsplit('-', 1)
        marks = [f for s, f, k, _ in actions if s == scene and k == kind]
        first = max(0, marks[0] - int(before)) if marks else 0
    end = len(names) if last is None else min(last + 1, len(names))
    kept[scene] = (first, end)
    dest = os.path.join(work, scene)
    os.makedirs(dest)
    # The title sits over the opening and fades out with its last frames; the end card over the close.
    title_span = (TITLE_FROM, end - first) if scene == 'd_dawn' else None
    end_from = max(0, (end - first) - 90) if scene == SCENES[-1][0] else None
    for i, n in enumerate(range(first, end)):
        jobs.append((scene, i, n, names[n], dest, title_span, end_from))

# Drawing and saving each frame is the slow part, so it runs on every core. Pillow lets go of
# Python's lock while it decodes, resizes and encodes, so threads are enough. The frames are only
# read once by ffmpeg, so they're saved with light compression.
def draw(job):
    scene, i, n, name, dest, title_span, end_from = job
    if True:
        img = Image.open(os.path.join(frames_dir, name)).convert('RGBA')
        sx, sy = W / img.width, H / img.height
        if img.size != (W, H):
            img = img.resize((W, H), Image.LANCZOS)
        if scene in BRIGHTEN:
            img = ImageEnhance.Brightness(img).enhance(BRIGHTEN[scene])
        x, y = cursor_at.get((scene, n), ('-', '-'))
        if x != '-':
            img.alpha_composite(cursor, (int(int(x) * sx), int(int(y) * sy)))
        if title_span is not None and title_span[0] <= i < title_span[1]:
            a = min(1.0, (i - title_span[0] + 1) / (FADE * 2), (title_span[1] - i) / (FADE * 2))
            img.alpha_composite(faded(TITLE_LAYER, a))
        if end_from is not None and i >= end_from:
            img.alpha_composite(faded(END_LAYER, min(1.0, (i - end_from + 1) / (FADE * 2))))
        img.convert('RGB').save(os.path.join(dest, '%04d.png' % i), compress_level=1)


with ThreadPoolExecutor(os.cpu_count()) as pool:
    list(pool.map(draw, jobs))

# One clip per scene, cut hard inside a chapter; a chapter fades in from black, the video fades out.
order = [s for s, *_ in SCENES if s in kept]
chapter_starts = {s for s, c, *_ in SCENES if c}
clips, starts, t, encodes = [], {}, 0.0, []
for scene in order:
    first, end = kept[scene]
    rate = FPS * SPEED.get(scene, 1.0)
    secs = (end - first) / rate
    fades = []
    if scene in chapter_starts and scene != order[0]:
        fades.append('fade=t=in:st=0:d=0.3')
    if scene == order[-1]:
        fades.append(f'fade=t=out:st={max(0.0, secs - 1.0):.3f}:d={min(1.0, secs):.3f}')
    vf = ','.join(fades + ['format=yuv420p'])
    clip = os.path.join(work, scene + '.mp4')
    encodes.append(('ffmpeg', '-y', '-framerate', str(rate), '-i', os.path.join(work, scene, '%04d.png'), '-vf', vf,
                    '-r', '30', '-c:v', 'libx264', '-preset', 'slow', '-crf', '17', clip))
    starts[scene] = t
    t += secs
    clips.append(clip)
total = t
# A few clips at a time: each ffmpeg uses several cores itself.
with ThreadPoolExecutor(4) as pool:
    list(pool.map(lambda args: run(*args), encodes))
with open(os.path.join(work, 'list.txt'), 'w') as f:
    f.writelines(f"file '{c.replace(os.sep, '/')}'\n" for c in clips)
silent = os.path.join(out, NAME + '-silent.mp4')
run('ffmpeg', '-y', '-f', 'concat', '-safe', '0', '-i', os.path.join(work, 'list.txt'), '-c', 'copy', '-movflags', '+faststart', silent)

# The game's own sounds at the logged frames. Typing makes no sound in the game, so none here.
random.seed(7)
SOUND = {'fuse': (['fuse'], 0.8), 'boom': (['boom1', 'boom2'], 0.8), 'place': (['place1'], 0.8), 'thunder': (['thunder'], 0.8),
         'break': (['glass'], 0.8), 'click': (['click'], 0.5), 'cat': (['meow'], 0.4)}
cues = []
for scene, frame, kind, what in actions:
    if scene not in kept or kind not in SOUND:
        continue
    first, end = kept[scene]
    if not first <= frame < end:
        continue
    at = starts[scene] + (frame - first) / (FPS * SPEED.get(scene, 1.0))
    names, volume = SOUND[kind]
    cues.append((at, random.choice(names), 0.9 + random.random() * 0.2, volume))
    if kind == 'place':
        # The "Home" sign's crackle comes a moment after the lantern is down.
        cues.append((at + 0.3, random.choice(['crackle1', 'crackle2']), 1.0, 0.6))
    if what == 'anvil':
        cues.append((at, 'anvil', 1.0, 0.4))
    if what.endswith('_buy'):
        cues.append((at + 0.1, 'trader_yes' if what.startswith('trader') else 'villager_yes', 1.0, 0.6))
# Arriving home: the crackle as "Gabby's Cottage" shows on the walk in.
for walk in ('d_name', 'tr_walk'):
    if walk in kept:
        cues.append((starts[walk] + 0.4, 'crackle1', 1.0, 0.6))
# Steady rain under the night's wet scenes: overlapping rain sounds about every 0.8 s.
for scene in RAIN:
    if scene in kept:
        first, end = kept[scene]
        at = starts[scene]
        while at < starts[scene] + (end - first) / FPS - 0.5:
            cues.append((at, random.choice(['rain1', 'rain2', 'rain3', 'rain4']), 0.95 + random.random() * 0.1, 0.3))
            at += 0.8
inputs, filters = [], []
for i, (at, sound, pitch, volume) in enumerate(cues):
    inputs += ['-i', os.path.join(sounds_dir, sound + '.ogg')]
    ms = int(at * 1000)
    filters.append(f'[{i}:a]aformat=sample_rates=44100:channel_layouts=stereo,asetrate={44100 * pitch:.0f},aresample=44100,'
                   f'volume={volume},adelay={ms}|{ms}[s{i}]')
mix = ''.join(f'[s{i}]' for i in range(len(cues)))
filters.append(f'{mix}amix=inputs={len(cues)}:normalize=0,apad,atrim=0:{total:.3f}[a]')
audio = os.path.join(work, 'sound.wav')
run('ffmpeg', '-y', *inputs, '-filter_complex', ';'.join(filters), '-map', '[a]', '-t', f'{total:.3f}', audio)
run('ffmpeg', '-y', '-i', silent, '-i', audio, '-map', '0:v', '-map', '1:a', '-c:v', 'copy', '-c:a', 'aac', '-b:a', '192k',
    '-shortest', '-movflags', '+faststart', os.path.join(out, NAME + '.mp4'))

# Each scene's start, for add_voice.py (the same timeline.json the Shorts' assemble.py writes).
with open(os.path.join(out, NAME + '-timeline.json'), 'w') as f:
    json.dump(dict(starts, total=total), f, indent=1)
if CUT == 'trailer':
    raw = sorted(n for n in os.listdir(frames_dir) if n.startswith('tr_end_') and n[7:-4].isdigit())
    Image.open(os.path.join(frames_dir, raw[len(raw) // 4])).convert('RGB').resize((1280, 720), Image.LANCZOS).save(
        os.path.join(out, NAME + '-thumbnail-frame.png'))
    print('total seconds', round(total, 2))
    for scene in order:
        print(f'  {scene}: starts {starts[scene]:.2f} s, {(kept[scene][1] - kept[scene][0]) / (FPS * SPEED.get(scene, 1.0)):.2f} s')
    sys.exit(0)

# YouTube's chapters: the first must start at 0:00 and each must last at least 10 s, so the opening
# belongs to the first chapter and the closing shot to the last.
with open(os.path.join(out, 'chapters.txt'), 'w') as f:
    marks = [(0.0 if scene == order[0] else starts[scene], chap) for scene, chap, *_ in SCENES if chap and scene in kept]
    marks[0] = (0.0, marks[0][1])
    for i, (at, chap) in enumerate(marks):
        until = marks[i + 1][0] if i + 1 < len(marks) else total
        if until - at < 10:
            print(f'warning: chapter "{chap}" is only {until - at:.1f} s; YouTube needs 10')
        s = int(at)
        f.write(f'{s // 60}:{s % 60:02d} {chap}\n')

# One GIF per scene: the scene with its caption, 640 wide, smaller until it is under 5 MB.
def make_gif(scene):
    gif = os.path.join(out, f'cozy-core-{scene[2:]}.gif')
    for width, fps in ((640, 15), (560, 12), (480, 10), (400, 10)):
        chain = f'fps={fps},scale={width}:-1:flags=lanczos'
        run('ffmpeg', '-y', '-i', os.path.join(work, scene + '.mp4'), '-filter_complex',
            f'[0:v]{chain},split[a][b];[a]palettegen=max_colors=192:stats_mode=diff[p];[b][p]paletteuse=dither=bayer:bayer_scale=4:diff_mode=rectangle',
            gif)
        if os.path.getsize(gif) < 5 * 1024 * 1024:
            break
    return f'  {scene}: {(kept[scene][1] - kept[scene][0]) / FPS:.2f} s, GIF {os.path.getsize(gif) // 1024} KB at {width}'


with ThreadPoolExecutor(6) as pool:
    for line in pool.map(make_gif, order):
        print(line)

# The thumbnail matches the opening, as YouTube advises: the cottage at sunrise, here with the
# Hearth Lantern glowing on its post (the closing shot, before the end card), and the name large.
raw = sorted(n for n in os.listdir(frames_dir) if n.startswith('d_morning_') and n[10:-4].isdigit())
pick = len(raw) // 4
thumb = Image.open(os.path.join(frames_dir, raw[pick])).convert('RGBA').resize((1280, 720), Image.LANCZOS)
d = ImageDraw.Draw(thumb)
# One line in the sky above the roof, so the cottage itself shows (Gabby, 2026-10-20).
big = font(FONTS, 118)
parts = [('COZY ', (255, 255, 255)), ('CORE', ORANGE)]
x = (1280 - sum(d.textlength(t, font=big) for t, _ in parts)) / 2
for text, colour in parts:
    d.text((x, 14), text, font=big, fill=colour, stroke_width=10, stroke_fill=(0, 0, 0))
    x += d.textlength(text, font=big)
thumb.convert('RGB').save(os.path.join(out, NAME + '-thumbnail.png'))
print('total seconds', round(total, 2))
print(open(os.path.join(out, 'chapters.txt')).read())
