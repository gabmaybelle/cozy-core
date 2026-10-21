"""Copies the game sounds the Short uses out of Loom's asset cache (filled by any game-test run).

python get_sounds.py <out dir>
The sounds are Mojang's, so they're read from the cache each time rather than kept in the repo.
"""
import glob, json, os, shutil, sys

out = sys.argv[1]
os.makedirs(out, exist_ok=True)
assets = os.path.expanduser('~/.gradle/caches/fabric-loom/assets')
SOUNDS = {'fuse': 'random/fuse', 'boom1': 'random/explode1', 'boom2': 'random/explode2',
          'place1': 'block/lantern/place1', 'crackle1': 'block/campfire/crackle1', 'crackle2': 'block/campfire/crackle2',
          'thunder': 'ambient/weather/thunder1', 'glass': 'random/glass1', 'click': 'random/click'}
objects = {}
for index in sorted(glob.glob(os.path.join(assets, 'indexes', '*.json'))):
    objects.update(json.load(open(index))['objects'])
for name, file in SOUNDS.items():
    h = objects[f'minecraft/sounds/{file}.ogg']['hash']
    source = os.path.join(assets, 'objects', h[:2], h)
    if not os.path.exists(source):
        sys.exit(f'{file}.ogg is not in the asset cache; run a game test with resources.download.minecraft.net reachable')
    shutil.copy(source, os.path.join(out, name + '.ogg'))
    print(name)
